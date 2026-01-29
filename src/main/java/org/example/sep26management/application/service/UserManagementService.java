package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.request.AssignRoleRequest;
import org.example.sep26management.application.dto.request.CreateUserRequest;
import org.example.sep26management.application.dto.request.UpdateUserStatusRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.UserListResponse;
import org.example.sep26management.application.dto.response.UserProfileResponse;
import org.example.sep26management.application.mapper.UserMapper;
import org.example.sep26management.domain.enums.UserRole;
import org.example.sep26management.domain.enums.UserStatus;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.example.sep26management.infrastructure.exception.ResourceNotFoundException;
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
    private final UserMapper userMapper;

    @Value("${app.default-password:Warehouse@123}")
    private String defaultPassword;

    public ApiResponse<UserProfileResponse> createUser(
            CreateUserRequest request,
            Long currentUserId,
            String ipAddress,
            String userAgent
    ) {
        log.info("Creating new user with email: {}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already exists");
        }

        if (Boolean.FALSE.equals(request.getIsPermanent()) && request.getExpireDate() == null) {
            throw new BusinessException("Expire date is required for non-permanent account");
        }

        if (request.getExpireDate() != null && request.getExpireDate().isBefore(LocalDate.now())) {
            throw new BusinessException("Expire date must be in the future");
        }

        UserEntity user = UserEntity.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(defaultPassword))
                .role(request.getRole())
                .status(UserStatus.INACTIVE)
                .isPermanent(request.getIsPermanent())
                .expireDate(request.getExpireDate())
                .isFirstLogin(true)
                .failedLoginAttempts(0)
                .createdBy(currentUserId)
                .updatedBy(currentUserId)
                .build();

        user = userRepository.save(user);

        emailService.sendWelcomeEmail(
                user.getEmail(),
                defaultPassword,
                user.getRole().getDisplayName()
        );

        auditLogService.logAction(
                currentUserId,
                "CREATE_USER",
                "USER",
                user.getUserId(),
                "Created new user: " + user.getEmail(),
                ipAddress,
                userAgent,
                null,
                String.format("Email: %s, Role: %s", user.getEmail(), user.getRole())
        );

        log.info("User created successfully: {}", user.getEmail());

        UserProfileResponse response = userMapper.toProfileResponse(user);

        return ApiResponse.success("Create Account Success", response);
    }

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

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<UserEntity> userPage = userRepository.searchUsers(keyword, role, status, pageable);

        List<UserListResponse.UserItemDTO> users = userPage.getContent().stream()
                .map(user -> {
                    UserProfileResponse profile = userMapper.toProfileResponse(user);

                    return UserListResponse.UserItemDTO.builder()
                            .userId(profile.getUserId())
                            .email(profile.getEmail())
                            .fullName(profile.getFullName())
                            .phone(profile.getPhone())
                            .role(profile.getRole())
                            .status(profile.getStatus())
                            .isPermanent(profile.getIsPermanent())
                            .expireDate(profile.getExpireDate())
                            .createdAt(profile.getCreatedAt())
                            .lastLoginAt(profile.getLastLoginAt())
                            .build();
                })
                .collect(Collectors.toList());

        UserListResponse response = UserListResponse.builder()
                .users(users)
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .currentPage(page)
                .pageSize(size)
                .build();

        log.info("Retrieved {} users (page {}/{})", users.size(), page + 1, userPage.getTotalPages());

        return ApiResponse.success("Users retrieved successfully", response);
    }

    public ApiResponse<UserProfileResponse> assignRole(
            Long userId,
            AssignRoleRequest request,
            Long currentUserId,
            String ipAddress,
            String userAgent
    ) {
        log.info("Assigning role {} to user ID: {}", request.getRole(), userId);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserRole oldRole = user.getRole();

        if (userId.equals(currentUserId)) {
            throw new BusinessException("Cannot change your own role");
        }

        user.setRole(request.getRole());
        user.setUpdatedBy(currentUserId);
        user = userRepository.save(user);

        auditLogService.logAction(
                currentUserId,
                "UPDATE_USER_ROLE",
                "USER",
                userId,
                String.format("Changed role from %s to %s", oldRole, request.getRole()),
                ipAddress,
                userAgent,
                "Role: " + oldRole,
                "Role: " + request.getRole()
        );

        log.info("Role assigned successfully for user: {}", user.getEmail());

        UserProfileResponse response = userMapper.toProfileResponse(user);

        return ApiResponse.success("User role has been updated successfully.", response);
    }

    public ApiResponse<UserProfileResponse> updateUserStatus(
            Long userId,
            UpdateUserStatusRequest request,
            Long currentUserId,
            String ipAddress,
            String userAgent
    ) {
        log.info("Updating status for user ID: {} to {}", userId, request.getStatus());

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (userId.equals(currentUserId)) {
            throw new BusinessException("Cannot change your own account status");
        }

        UserStatus oldStatus = user.getStatus();

        user.setStatus(request.getStatus());
        user.setUpdatedBy(currentUserId);

        if (request.getStatus() == UserStatus.ACTIVE) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }

        user = userRepository.save(user);

        String statusText = request.getStatus() == UserStatus.ACTIVE ? "activated" : "deactivated";
        emailService.sendStatusChangeEmail(user.getEmail(), statusText);

        auditLogService.logAction(
                currentUserId,
                "UPDATE_USER_STATUS",
                "USER",
                userId,
                String.format("Changed status from %s to %s", oldStatus, request.getStatus()),
                ipAddress,
                userAgent,
                "Status: " + oldStatus,
                "Status: " + request.getStatus()
        );

        log.info("Status updated successfully for user: {}", user.getEmail());

        UserProfileResponse response = userMapper.toProfileResponse(user);

        String message = request.getStatus() == UserStatus.ACTIVE
                ? "User account has been activated successfully."
                : "User account has been deactivated successfully.";

        return ApiResponse.success(message, response);
    }

    @Transactional(readOnly = true)
    public ApiResponse<UserProfileResponse> getUserById(Long userId) {
        log.info("Fetching user by ID: {}", userId);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserProfileResponse response = userMapper.toProfileResponse(user);

        log.info("User retrieved: {}", user.getEmail());

        return ApiResponse.success("User retrieved successfully", response);
    }
}