package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.request.ChangePasswordRequest;
import org.example.sep26management.application.dto.request.UpdateProfileRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.UserProfileResponse;
import org.example.sep26management.application.mapper.UserMapper;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.example.sep26management.infrastructure.exception.ResourceNotFoundException;
import org.example.sep26management.infrastructure.persistence.entity.UserEntity;
import org.example.sep26management.infrastructure.persistence.repository.UserJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProfileService {

    private final UserJpaRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final UserMapper userMapper;

    @Value("${file.upload.dir}")
    private String uploadDir;

    @Value("${file.upload.max-size}")
    private long maxFileSize;

    private static final List<String> ALLOWED_IMAGE_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png");

    public ApiResponse<Void> changePassword(
            Long userId,
            ChangePasswordRequest request,
            String ipAddress,
            String userAgent
    ) {
        log.info("Changing password for user ID: {}", userId);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessException("Current password is incorrect.");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("New password and confirmation do not match.");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new BusinessException("New password must be different from current password.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setUpdatedBy(userId);
        userRepository.save(user);

        auditLogService.logAction(
                userId,
                "PASSWORD_CHANGE",
                "USER",
                userId,
                "Password changed successfully",
                ipAddress,
                userAgent,
                null,
                "Password updated"
        );

        log.info("Password changed successfully for user ID: {}", userId);

        return ApiResponse.success("Password changed successfully");
    }

    @Transactional(readOnly = true)
    public ApiResponse<UserProfileResponse> getProfile(Long userId) {
        log.info("Fetching profile for user ID: {}", userId);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserProfileResponse response = userMapper.toProfileResponse(user);

        log.info("Profile retrieved successfully for user: {}", user.getEmail());

        return ApiResponse.success("Profile retrieved successfully", response);
    }

    public ApiResponse<UserProfileResponse> updateProfile(
            Long userId,
            UpdateProfileRequest request,
            String ipAddress,
            String userAgent
    ) {
        log.info("Updating profile for user ID: {}", userId);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Unable to load profile information."));

        String oldValues = buildAuditValue(user);

        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setGender(request.getGender());
        user.setDateOfBirth(request.getDateOfBirth());
        user.setAddress(request.getAddress());
        user.setUpdatedAt(LocalDateTime.now());
        user.setUpdatedBy(userId);

        if (request.getAvatar() != null && !request.getAvatar().isEmpty()) {
            String avatarUrl = saveAvatar(request.getAvatar(), userId);
            user.setAvatarUrl(avatarUrl);
        }

        user = userRepository.save(user);

        String newValues = buildAuditValue(user);

        auditLogService.logAction(
                userId,
                "UPDATE_PROFILE",
                "USER",
                userId,
                "Profile updated",
                ipAddress,
                userAgent,
                oldValues,
                newValues
        );

        log.info("Profile updated successfully for user: {}", user.getEmail());

        UserProfileResponse response = userMapper.toProfileResponse(user);

        return ApiResponse.success("Profile updated successfully.", response);
    }

    private String saveAvatar(MultipartFile file, Long userId) {
        try {
            if (file.isEmpty()) {
                throw new BusinessException("The uploaded file is empty.");
            }

            if (file.getSize() > maxFileSize) {
                throw new BusinessException("File size must be less than 5MB");
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) {
                throw new BusinessException("Invalid file name");
            }

            String extension = getFileExtension(originalFilename).toLowerCase();
            if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
                throw new BusinessException("Please select an image file (jpg, jpeg, png)");
            }

            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new BusinessException("Please select an image file");
            }

            Path uploadPath = Paths.get(uploadDir, "avatars");
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String newFilename = String.format("avatar_%d_%s.%s",
                    userId,
                    UUID.randomUUID().toString(),
                    extension);

            Path filePath = uploadPath.resolve(newFilename);

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            log.info("Avatar saved successfully: {}", newFilename);

            return "/uploads/avatars/" + newFilename;

        } catch (IOException e) {
            log.error("Error saving avatar file", e);
            throw new BusinessException("Failed to save avatar image");
        }
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
            return filename.substring(lastDotIndex + 1);
        }
        return "";
    }

    private String buildAuditValue(UserEntity user) {
        return String.format(
                "{\"fullName\":\"%s\",\"phone\":\"%s\",\"gender\":\"%s\",\"dateOfBirth\":\"%s\",\"address\":\"%s\",\"avatarUrl\":\"%s\"}",
                nvl(user.getFullName()),
                nvl(user.getPhone()),
                nvl(user.getGender()),
                user.getDateOfBirth() != null ? user.getDateOfBirth().toString() : "",
                nvl(user.getAddress()),
                nvl(user.getAvatarUrl())
        );
    }

    private String nvl(String value) {
        return value != null ? value : "";
    }
}