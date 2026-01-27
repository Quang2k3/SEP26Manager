package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.request.AssignRoleRequest;
import org.example.sep26management.application.dto.request.CreateUserRequest;
import org.example.sep26management.application.dto.request.UpdateUserStatusRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.UserListResponse;
import org.example.sep26management.application.dto.response.UserProfileResponse;
import org.example.sep26management.domain.enums.OtpType;
import org.example.sep26management.domain.enums.UserRole;
import org.example.sep26management.domain.enums.UserStatus;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.example.sep26management.infrastructure.exception.ResourceNotFoundException;
import org.example.sep26management.infrastructure.exception.UnauthorizedException;
import org.example.sep26management.infrastructure.persistence.entity.UserEntity;
import org.example.sep26management.infrastructure.persistence.repository.UserJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserManagementService {

    private final UserJpaRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditLogService auditLogService;

    @Value("${app.default-password}")
    private String defaultPassword;

    /**
     * UC-MUA-01: Create Account User
     */
    public ApiResponse<UserProfileResponse> createUser(
            CreateUserRequest request,
            Long currentUserId,
            String ipAddress,
            String userAgent
    ) {
        log.info("Creating new user with email: {}", request.getEmail());

        // Step 5: Validate email uniqueness (BR-USER-07)
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already exists");
        }

        // Validate expire date if not permanent
        if (Boolean.FALSE.equals(request.getIsPermanent()) && request.getExpireDate() == null) {
            throw new BusinessException("Expire date is required for non-permanent account");
        }

        if (request.getExpireDate() != null && request.getExpireDate().isBefore(LocalDate.now())) {
            throw new BusinessException("Expire date must be in the future");
        }

        // Step 6: Generate defaults (BR-USER-08, BR-USER-09)
        UserEntity user = UserEntity.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode("Warehouse@123")) // BR-USER-08: Default password
                .role(request.getRole())
                .status(UserStatus.INACTIVE) // BR-USER-09: Initial state
                .isPermanent(request.getIsPermanent())
                .expireDate(request.getExpireDate())
                .isFirstLogin(true)
                .failedLoginAttempts(0)
                .createdBy(currentUserId)
                .updatedBy(currentUserId)
                .build();

        // Step 7: Create account and default profile (BR-USER-12)
        userRepository.save(user);

        // Send welcome email with credentials
        emailService.sendWelcomeEmail(
                user.getEmail(),
                "Warehouse@123",
                user.getRole().getDisplayName()
        );

        // Log audit
        auditLogService.logAction(
                currentUserId,
                "CREATE_USER",
                "USER",
                user.getUserId(),
                "Created new user: " + user.getEmail(),
                ipAddress,
                userAgent
        );

        // Build response
        UserProfileResponse response = UserProfileResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .role(user.getRole())
                .status(user.getStatus())
                .isPermanent(user.getIsPermanent())
                .expireDate(user.getExpireDate())
                .createdAt(user.getCreatedAt())
                .build();

        return ApiResponse.success("Create Account Success", response);
    }

    /**
     * UC-MUA-02: View List User
     */
    @Transactional(readOnly = true)
    public ApiResponse<UserListResponse> getUserList(
            String keyword,
            UserRole role,
            UserStatus status,
            int page,
            int size
    ) {
        log.info("Fetching user list - keyword: {}, role: {}, status: {}, page: {}, size: {}",
                keyword, role, status, page, size);

        // Step 3: Load accounts with pagination (BR-USER-13)
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<UserEntity> userPage = userRepository.searchUsers(keyword, role, status, pageable);

        // Step 4: Map to DTOs
        List<UserListResponse.UserItemDTO> users = userPage.getContent().stream()
                .map(user -> UserListResponse.UserItemDTO.builder()
                        .userId(user.getUserId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .phone(user.getPhone())
                        .role(user.getRole())
                        .status(user.getStatus())
                        .isPermanent(user.getIsPermanent())
                        .expireDate(user.getExpireDate())
                        .createdAt(user.getCreatedAt())
                        .lastLoginAt(user.getLastLoginAt())
                        .build())
                .collect(Collectors.toList());

        UserListResponse response = UserListResponse.builder()
                .users(users)
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .currentPage(page)
                .pageSize(size)
                .build();

        return ApiResponse.success("Users retrieved successfully", response);
    }

    /**
     * UC-MUA-03: Assign Role
     */
    public ApiResponse<UserProfileResponse> assignRole(
            Long userId,
            AssignRoleRequest request,
            Long currentUserId,
            String ipAddress,
            String userAgent
    ) {
        log.info("Assigning role {} to user ID: {}", request.getRole(), userId);

        // Find user
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Store old role for audit
        UserRole oldRole = user.getRole();

        // Validate: Cannot change own role
        if (userId.equals(currentUserId)) {
            throw new BusinessException("Cannot change your own role");
        }

        // Step 4: Update role (BR-USER-15)
        user.setRole(request.getRole());
        user.setUpdatedBy(currentUserId);
        userRepository.save(user);

        // Log audit
        auditLogService.logAction(
                currentUserId,
                "UPDATE_USER_ROLE",
                "USER",
                userId,
                String.format("Changed role from %s to %s", oldRole, request.getRole()),
                ipAddress,
                userAgent
        );

        // Build response
        UserProfileResponse response = UserProfileResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .status(user.getStatus())
                .build();

        return ApiResponse.success("User role has been updated successfully.", response);
    }

    /**
     * UC-MUA-04: Active / Inactive User
     */
    public ApiResponse<UserProfileResponse> updateUserStatus(
            Long userId,
            UpdateUserStatusRequest request,
            Long currentUserId,
            String ipAddress,
            String userAgent
    ) {
        log.info("Updating status for user ID: {} to {}", userId, request.getStatus());

        // Find user
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Validate: Cannot change own status
        if (userId.equals(currentUserId)) {
            throw new BusinessException("Cannot change your own account status");
        }

        // Store old status for audit
        UserStatus oldStatus = user.getStatus();

        // Update status
        user.setStatus(request.getStatus());
        user.setUpdatedBy(currentUserId);

        // If activating, reset failed login attempts
        if (request.getStatus() == UserStatus.ACTIVE) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }

        userRepository.save(user);

        // Send notification email
        String statusText = request.getStatus() == UserStatus.ACTIVE ? "activated" : "deactivated";
        emailService.sendStatusChangeEmail(user.getEmail(), statusText);

        // Log audit
        auditLogService.logAction(
                currentUserId,
                "UPDATE_USER_STATUS",
                "USER",
                userId,
                String.format("Changed status from %s to %s", oldStatus, request.getStatus()),
                ipAddress,
                userAgent
        );

        // Build response
        UserProfileResponse response = UserProfileResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .status(user.getStatus())
                .build();

        String message = request.getStatus() == UserStatus.ACTIVE
                ? "User account has been activated successfully."
                : "User account has been deactivated successfully.";

        return ApiResponse.success(message, response);
    }

    /**
     * Get user by ID
     */
    @Transactional(readOnly = true)
    public ApiResponse<UserProfileResponse> getUserById(Long userId) {
        log.info("Fetching user by ID: {}", userId);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserProfileResponse response = UserProfileResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .gender(user.getGender())
                .dateOfBirth(user.getDateOfBirth())
                .address(user.getAddress())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .status(user.getStatus())
                .isPermanent(user.getIsPermanent())
                .expireDate(user.getExpireDate())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();

        return ApiResponse.success("User retrieved successfully", response);
    }
}