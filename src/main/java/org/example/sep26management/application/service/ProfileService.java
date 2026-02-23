package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.LogMessages;
import org.example.sep26management.application.dto.request.ChangePasswordRequest;
import org.example.sep26management.application.dto.request.UpdateProfileRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.UserProfileResponse;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.example.sep26management.infrastructure.exception.ResourceNotFoundException;
import org.example.sep26management.infrastructure.mapper.UserMapper;
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

    /**
     * UC-PERS-02: Change Password
     */
    public ApiResponse<Void> changePassword(
            Long userId,
            ChangePasswordRequest request,
            String ipAddress,
            String userAgent) {
        log.info(LogMessages.PROFILE_CHANGING_PASSWORD, userId);

        // Find user
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(MessageConstants.USER_NOT_FOUND));

        // Step 4: Validate current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessException(MessageConstants.PASSWORD_INCORRECT);
        }

        // Step 5: Validate new password
        // 5a: Check if passwords match
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(MessageConstants.PASSWORD_MISMATCH);
        }

        // 5c: Check if new password is same as current (BR-PERS-02)
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new BusinessException(MessageConstants.PASSWORD_SAME);
        }

        // Step 6: Update password
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);

        // Step 7: Log audit
        auditLogService.logAction(
                userId,
                "PASSWORD_CHANGE",
                "USER",
                userId,
                "Password changed successfully",
                ipAddress,
                userAgent);

        // Step 8: Return success (BR-PERS-04: maintain current session)
        return ApiResponse.success(MessageConstants.PASSWORD_CHANGED);
    }

    /**
     * UC-PERS-03: View Personal Profile
     */
    @Transactional(readOnly = true)
    public ApiResponse<UserProfileResponse> getProfile(Long userId) {
        log.info(LogMessages.PROFILE_FETCHING, userId);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(MessageConstants.USER_NOT_FOUND));

        return ApiResponse.success(MessageConstants.PROFILE_SUCCESS, userMapper.toProfileResponse(user));
    }

    /**
     * UC-PERS-04: Update Personal Profile
     */
    public ApiResponse<UserProfileResponse> updateProfile(
            Long userId,
            UpdateProfileRequest request,
            String ipAddress,
            String userAgent) {
        log.info(LogMessages.PROFILE_UPDATING, userId);

        // Step 2: Retrieve current profile
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(MessageConstants.PROFILE_LOAD_FAILED));

        // Store old values for audit
        String oldFullName = user.getFullName();
        String oldPhone = user.getPhone();
        String oldGender = user.getGender();
        String oldAddress = user.getAddress();
        String oldAvatarUrl = user.getAvatarUrl();

        // Step 4: User modifies data
        // Step 6: Validate (already done by @Valid annotation in controller)

        // Update basic info
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setGender(request.getGender());
        user.setDateOfBirth(request.getDateOfBirth());
        user.setAddress(request.getAddress());
        user.setUpdatedBy(userId);

        // Handle avatar upload if provided
        if (request.getAvatar() != null && !request.getAvatar().isEmpty()) {
            String avatarUrl = saveAvatar(request.getAvatar(), userId);
            user.setAvatarUrl(avatarUrl);
        }

        // Step 7: Persist changes
        userRepository.save(user);

        // Step 8: Log audit
        auditLogService.logAction(
                userId,
                "UPDATE_PROFILE",
                "USER",
                userId,
                "Profile updated",
                ipAddress,
                userAgent,
                buildOldValue(oldFullName, oldPhone, oldGender, oldAddress, oldAvatarUrl),
                buildNewValue(user.getFullName(), user.getPhone(), user.getGender(),
                        user.getAddress(), user.getAvatarUrl()));

        return ApiResponse.success(MessageConstants.PROFILE_UPDATED, userMapper.toProfileResponse(user));
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    private String saveAvatar(MultipartFile file, Long userId) {
        try {
            // Step 6b: Avatar Validation

            // Check if file is empty
            if (file.isEmpty()) {
                throw new BusinessException(MessageConstants.FILE_EMPTY);
            }

            // Check file size (BR-PERS-13)
            if (file.getSize() > maxFileSize) {
                throw new BusinessException(MessageConstants.FILE_TOO_LARGE);
            }

            // Check file extension
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) {
                throw new BusinessException(MessageConstants.FILE_INVALID_NAME);
            }

            String extension = getFileExtension(originalFilename).toLowerCase();
            if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
                throw new BusinessException(MessageConstants.FILE_NOT_IMAGE);
            }

            // Check content type
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new BusinessException(MessageConstants.FILE_NOT_IMAGE);
            }

            // Create upload directory if not exists
            Path uploadPath = Paths.get(uploadDir, "avatars");
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Generate unique filename
            String newFilename = String.format("avatar_%d_%s.%s",
                    userId,
                    UUID.randomUUID().toString(),
                    extension);

            Path filePath = uploadPath.resolve(newFilename);

            // Save file
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // Return relative URL
            return "/uploads/avatars/" + newFilename;

        } catch (IOException e) {
            log.error(LogMessages.PROFILE_ERROR_SAVING_AVATAR, e);
            throw new BusinessException(MessageConstants.AVATAR_SAVE_FAILED);
        }
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
            return filename.substring(lastDotIndex + 1);
        }
        return "";
    }

    private String buildOldValue(String fullName, String phone, String gender,
            String address, String avatarUrl) {
        return String.format(
                "{\"fullName\":\"%s\",\"phone\":\"%s\",\"gender\":\"%s\",\"address\":\"%s\",\"avatarUrl\":\"%s\"}",
                fullName, phone, gender, address, avatarUrl);
    }

    private String buildNewValue(String fullName, String phone, String gender,
            String address, String avatarUrl) {
        return buildOldValue(fullName, phone, gender, address, avatarUrl);
    }
}