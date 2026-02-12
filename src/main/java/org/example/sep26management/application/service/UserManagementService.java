package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.LogMessages;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.application.dto.request.CreateUserRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.UserListResponse;
import org.example.sep26management.application.dto.response.UserResponse;
import org.example.sep26management.domain.enums.UserStatus;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.example.sep26management.infrastructure.persistence.entity.RoleEntity;
import org.example.sep26management.infrastructure.persistence.entity.UserEntity;
import org.example.sep26management.infrastructure.persistence.repository.RoleJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.UserJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserManagementService {

        private final UserJpaRepository userRepository;
        private final RoleJpaRepository roleRepository;
        private final PasswordEncoder passwordEncoder;
        private final EmailService emailService;
        private final AuditLogService auditLogService;

        private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
        private static final int TEMP_PASSWORD_LENGTH = 12;
        private static final SecureRandom RANDOM = new SecureRandom();

        /**
         * Create a new user with specified roles
         * Only ADMIN or MANAGER can create users
         * 
         * @param request   CreateUserRequest containing user details
         * @param createdBy User ID of the creator (admin/manager)
         * @param ipAddress IP address of the request
         * @param userAgent User agent of the request
         * @return ApiResponse containing created user details
         */
        public ApiResponse<UserResponse> createUser(CreateUserRequest request, Long createdBy, String ipAddress,
                        String userAgent) {
                log.info(LogMessages.USER_CREATING, request.getEmail());

                // Validate email uniqueness
                if (userRepository.existsByEmail(request.getEmail())) {
                        log.warn(LogMessages.USER_EMAIL_DUPLICATE, request.getEmail());
                        throw new BusinessException(MessageConstants.USER_EMAIL_EXISTS);
                }

                // Validate expire date for temporary accounts
                if (Boolean.FALSE.equals(request.getIsPermanent())) {
                        if (request.getExpireDate() == null) {
                                throw new BusinessException(MessageConstants.EXPIRE_DATE_REQUIRED);
                        }
                        if (request.getExpireDate().isBefore(LocalDate.now())) {
                                throw new BusinessException(MessageConstants.INVALID_EXPIRE_DATE);
                        }
                }

                // Fetch role entities from role codes
                Set<RoleEntity> roles = new HashSet<>();
                for (String roleCode : request.getRoleCodes()) {
                        RoleEntity role = roleRepository.findByRoleCode(roleCode)
                                        .orElseThrow(() -> new BusinessException(
                                                        String.format(MessageConstants.ROLE_NOT_FOUND, roleCode)));
                        roles.add(role);
                }

                // Generate temporary password
                String tempPassword = generateTempPassword();
                String hashedPassword = passwordEncoder.encode(tempPassword);

                // Create user entity
                UserEntity userEntity = UserEntity.builder()
                                .email(request.getEmail())
                                .passwordHash(hashedPassword)
                                .roles(roles)
                                .status(UserStatus.PENDING_VERIFY)
                                .isPermanent(request.getIsPermanent())
                                .expireDate(request.getExpireDate())
                                .isFirstLogin(true)
                                .failedLoginAttempts(0)
                                .passwordChangedAt(LocalDateTime.now())
                                .createdBy(createdBy)
                                .updatedBy(createdBy)
                                .build();

                // Save user to database
                UserEntity savedUser = userRepository.save(userEntity);

                log.info(LogMessages.USER_CREATED, savedUser.getUserId());

                // Send welcome email with temporary password
                String roleNames = roles.stream()
                                .map(RoleEntity::getRoleName)
                                .collect(Collectors.joining(", "));
                emailService.sendWelcomeEmail(savedUser.getEmail(), tempPassword, roleNames);

                // Log audit event
                auditLogService.logAction(
                                createdBy,
                                "USER_CREATED",
                                "USER",
                                savedUser.getUserId(),
                                "New user created: " + savedUser.getEmail(),
                                ipAddress,
                                userAgent);

                // Build response
                UserResponse response = UserResponse.builder()
                                .userId(savedUser.getUserId())
                                .email(savedUser.getEmail())
                                .fullName(savedUser.getFullName())
                                .roleCodes(roles.stream()
                                                .map(RoleEntity::getRoleCode)
                                                .collect(Collectors.toSet()))
                                .status(savedUser.getStatus())
                                .isPermanent(savedUser.getIsPermanent())
                                .expireDate(savedUser.getExpireDate())
                                .createdAt(savedUser.getCreatedAt())
                                .build();

                return ApiResponse.success(MessageConstants.USER_CREATED_SUCCESS, response);
        }

        /**
         * Get list of users with pagination and filtering
         * Supports filtering by status and searching by keyword (email or name)
         * 
         * @param keyword Optional keyword to search by email or name
         * @param status  Optional status filter
         * @param page    Page number (0-indexed)
         * @param size    Page size
         * @return ApiResponse containing paginated user list
         */
        @Transactional(readOnly = true)
        public ApiResponse<UserListResponse> getUserList(String keyword, UserStatus status, int page, int size) {
                log.info(LogMessages.USER_LIST_FETCHING, keyword, status, page, size);

                // Create pageable with sorting by createdAt DESC
                Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

                // Search users with filters
                Page<UserEntity> userPage = userRepository.searchUsers(keyword, status, pageable);

                // Convert to DTOs
                List<UserListResponse.UserItemDTO> userItems = userPage.getContent().stream()
                                .map(user -> {
                                        Set<String> roleCodes = user.getRoles() != null
                                                        ? user.getRoles().stream()
                                                                        .map(RoleEntity::getRoleCode)
                                                                        .collect(Collectors.toSet())
                                                        : Set.of();

                                        return UserListResponse.UserItemDTO.builder()
                                                        .userId(user.getUserId())
                                                        .email(user.getEmail())
                                                        .fullName(user.getFullName())
                                                        .phone(user.getPhone())
                                                        .roleCodes(roleCodes)
                                                        .status(user.getStatus())
                                                        .isPermanent(user.getIsPermanent())
                                                        .expireDate(user.getExpireDate())
                                                        .createdAt(user.getCreatedAt())
                                                        .lastLoginAt(user.getLastLoginAt())
                                                        .build();
                                })
                                .collect(Collectors.toList());

                // Build response
                UserListResponse listResponse = UserListResponse.builder()
                                .users(userItems)
                                .totalElements(userPage.getTotalElements())
                                .totalPages(userPage.getTotalPages())
                                .currentPage(userPage.getNumber())
                                .pageSize(userPage.getSize())
                                .build();

                return ApiResponse.success("Users retrieved successfully", listResponse);
        }

        /**
         * Assign a new role to a user
         * Only MANAGER can perform this action
         * 
         * @param userId       Target user ID whose role will be changed
         * @param roleCode     New role code to assign
         * @param managerId    Manager ID performing the action
         * @param managerEmail Manager email for audit trail
         * @param ipAddress    IP address of the request
         * @param userAgent    User agent of the request
         * @return ApiResponse containing updated user details
         */
        public ApiResponse<UserResponse> assignRole(Long userId, String roleCode, Long managerId,
                        String managerEmail, String ipAddress, String userAgent) {
                log.info(LogMessages.USER_ROLE_ASSIGNING, roleCode, userId, managerId);

                try {
                        // Fetch target user
                        UserEntity user = userRepository.findById(userId)
                                        .orElseThrow(() -> new BusinessException(
                                                        MessageConstants.USER_NOT_FOUND_FOR_ROLE_ASSIGNMENT));

                        // Fetch new role
                        RoleEntity newRole = roleRepository.findByRoleCode(roleCode)
                                        .orElseThrow(() -> new BusinessException(
                                                        String.format(MessageConstants.ROLE_NOT_FOUND, roleCode)));

                        // Get current roles for comparison
                        Set<RoleEntity> currentRoles = user.getRoles();
                        String oldRoles = currentRoles.stream()
                                        .map(RoleEntity::getRoleName)
                                        .collect(Collectors.joining(", "));

                        // Check if user already has this role
                        boolean alreadyHasRole = currentRoles.stream()
                                        .anyMatch(r -> r.getRoleCode().equals(roleCode));

                        if (alreadyHasRole) {
                                log.warn(LogMessages.USER_ROLE_ALREADY_ASSIGNED, userId, roleCode);
                                throw new BusinessException(MessageConstants.SAME_ROLE_ASSIGNMENT);
                        }

                        // Replace user's roles with the new role (one role per user)
                        Set<RoleEntity> newRoles = new HashSet<>();
                        newRoles.add(newRole);
                        user.setRoles(newRoles);
                        user.setUpdatedBy(managerId);

                        // Save updated user
                        UserEntity updatedUser = userRepository.save(user);

                        log.info(LogMessages.USER_ROLE_ASSIGNED, userId, oldRoles, newRole.getRoleName());

                        // Send role change email notification
                        emailService.sendRoleChangeEmail(
                                        updatedUser.getEmail(),
                                        oldRoles,
                                        newRole.getRoleName(),
                                        managerEmail);

                        // Log audit event
                        auditLogService.logAction(
                                        managerId,
                                        "ROLE_ASSIGNED",
                                        "USER",
                                        userId,
                                        String.format("Role changed from '%s' to '%s'", oldRoles,
                                                        newRole.getRoleName()),
                                        ipAddress,
                                        userAgent);

                        // Build response
                        UserResponse response = UserResponse.builder()
                                        .userId(updatedUser.getUserId())
                                        .email(updatedUser.getEmail())
                                        .fullName(updatedUser.getFullName())
                                        .roleCodes(updatedUser.getRoles().stream()
                                                        .map(RoleEntity::getRoleCode)
                                                        .collect(Collectors.toSet()))
                                        .status(updatedUser.getStatus())
                                        .isPermanent(updatedUser.getIsPermanent())
                                        .expireDate(updatedUser.getExpireDate())
                                        .createdAt(updatedUser.getCreatedAt())
                                        .build();

                        return ApiResponse.success(MessageConstants.ROLE_ASSIGNED_SUCCESS, response);

                } catch (BusinessException e) {
                        log.error(LogMessages.USER_ROLE_ASSIGNMENT_FAILED, userId, e.getMessage());
                        throw e;
                } catch (Exception e) {
                        log.error(LogMessages.USER_ROLE_ASSIGNMENT_FAILED, userId, e.getMessage(), e);
                        throw new BusinessException(MessageConstants.ROLE_ASSIGNMENT_FAILED);
                }
        }

        /**
         * Generate a random temporary password
         * Password includes uppercase, lowercase, digits, and special characters
         * 
         * @return Generated temporary password
         */
        private String generateTempPassword() {
                StringBuilder password = new StringBuilder(TEMP_PASSWORD_LENGTH);

                // Ensure at least one of each character type
                password.append(getRandomChar("ABCDEFGHIJKLMNOPQRSTUVWXYZ")); // Uppercase
                password.append(getRandomChar("abcdefghijklmnopqrstuvwxyz")); // Lowercase
                password.append(getRandomChar("0123456789")); // Digit
                password.append(getRandomChar("!@#$%^&*")); // Special char

                // Fill the rest randomly
                for (int i = 4; i < TEMP_PASSWORD_LENGTH; i++) {
                        password.append(getRandomChar(TEMP_PASSWORD_CHARS));
                }

                // Shuffle the password to avoid predictable patterns
                return shuffleString(password.toString());
        }

        /**
         * Get a random character from the given character set
         */
        private char getRandomChar(String chars) {
                return chars.charAt(RANDOM.nextInt(chars.length()));
        }

        /**
         * Shuffle a string to randomize character positions
         */
        private String shuffleString(String input) {
                char[] characters = input.toCharArray();
                for (int i = characters.length - 1; i > 0; i--) {
                        int j = RANDOM.nextInt(i + 1);
                        char temp = characters[i];
                        characters[i] = characters[j];
                        characters[j] = temp;
                }
                return new String(characters);
        }
}