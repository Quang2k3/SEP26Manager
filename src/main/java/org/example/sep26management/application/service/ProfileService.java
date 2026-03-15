package org.example.sep26management.application.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
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
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProfileService {

    private final UserJpaRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final UserMapper userMapper;
    private final Cloudinary cloudinary;

    @Value("${file.upload.max-size}")
    private long maxFileSize;

    private static final List<String> ALLOWED_IMAGE_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "webp");
    private static final String CLOUDINARY_FOLDER = "sep26wms/avatars";

    // ─── Change Password ──────────────────────────────────────────────────────

    public ApiResponse<Void> changePassword(
            Long userId,
            ChangePasswordRequest request,
            String ipAddress,
            String userAgent) {

        log.info(LogMessages.PROFILE_CHANGING_PASSWORD, userId);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(MessageConstants.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessException(MessageConstants.PASSWORD_INCORRECT);
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(MessageConstants.PASSWORD_MISMATCH);
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new BusinessException(MessageConstants.PASSWORD_SAME);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);

        auditLogService.logAction(userId, "PASSWORD_CHANGE", "USER", userId,
                "Password changed successfully", ipAddress, userAgent);

        return ApiResponse.success(MessageConstants.PASSWORD_CHANGED);
    }

    // ─── Get Profile ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<UserProfileResponse> getProfile(Long userId) {
        log.info(LogMessages.PROFILE_FETCHING, userId);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(MessageConstants.USER_NOT_FOUND));

        return ApiResponse.success(MessageConstants.PROFILE_SUCCESS, userMapper.toProfileResponse(user));
    }

    // ─── Update Profile ───────────────────────────────────────────────────────

    public ApiResponse<UserProfileResponse> updateProfile(
            Long userId,
            UpdateProfileRequest request,
            String ipAddress,
            String userAgent) {

        log.info(LogMessages.PROFILE_UPDATING, userId);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(MessageConstants.PROFILE_LOAD_FAILED));

        String oldFullName  = user.getFullName();
        String oldPhone     = user.getPhone();
        String oldGender    = user.getGender();
        String oldAddress   = user.getAddress();
        String oldAvatarUrl = user.getAvatarUrl();

        if (request.getFullName() != null && !request.getFullName().trim().isEmpty()) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getPhone() != null && !request.getPhone().trim().isEmpty()) {
            user.setPhone(request.getPhone().trim());
        }
        if (request.getGender() != null && !request.getGender().trim().isEmpty()) {
            user.setGender(request.getGender());
        }
        if (request.getDateOfBirth() != null) {
            user.setDateOfBirth(request.getDateOfBirth());
        }
        if (request.getAddress() != null && !request.getAddress().trim().isEmpty()) {
            user.setAddress(request.getAddress().trim());
        }

        user.setUpdatedBy(userId);

        // Upload avatar lên Cloudinary nếu có
        if (request.getAvatar() != null && !request.getAvatar().isEmpty()) {
            String avatarUrl = uploadAvatarToCloudinary(request.getAvatar(), userId);
            user.setAvatarUrl(avatarUrl);
        }

        userRepository.save(user);

        auditLogService.logAction(userId, "UPDATE_PROFILE", "USER", userId, "Profile updated",
                ipAddress, userAgent,
                buildValue(oldFullName, oldPhone, oldGender, oldAddress, oldAvatarUrl),
                buildValue(user.getFullName(), user.getPhone(), user.getGender(),
                        user.getAddress(), user.getAvatarUrl()));

        return ApiResponse.success(MessageConstants.PROFILE_UPDATED, userMapper.toProfileResponse(user));
    }

    // ─── Cloudinary Upload ────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String uploadAvatarToCloudinary(MultipartFile file, Long userId) {
        // Validate
        if (file.isEmpty()) {
            throw new BusinessException(MessageConstants.FILE_EMPTY);
        }
        if (file.getSize() > maxFileSize) {
            throw new BusinessException(MessageConstants.FILE_TOO_LARGE);
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new BusinessException(MessageConstants.FILE_INVALID_NAME);
        }

        String extension = getFileExtension(originalFilename).toLowerCase();
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            throw new BusinessException(MessageConstants.FILE_NOT_IMAGE);
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException(MessageConstants.FILE_NOT_IMAGE);
        }

        try {
            // Public ID cố định theo userId → tự ghi đè ảnh cũ, không sinh file rác
            String publicId = CLOUDINARY_FOLDER + "/avatar_" + userId;

            // transformation phải là List<Map>, không phải String
            List<Map<String, Object>> transformation = new java.util.ArrayList<>();
            transformation.add(ObjectUtils.asMap(
                    "width", 400, "height", 400,
                    "crop", "fill", "gravity", "face",
                    "quality", "auto", "fetch_format", "auto"
            ));

            Map uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "public_id",      publicId,
                            "overwrite",      true,
                            "resource_type",  "image",
                            "transformation", transformation,
                            "invalidate",     true   // xóa CDN cache khi overwrite
                    )
            );

            String secureUrl = (String) uploadResult.get("secure_url");
            log.info("Avatar uploaded to Cloudinary for userId={}: {}", userId, secureUrl);
            return secureUrl;

        } catch (IOException e) {
            log.error("Failed to upload avatar to Cloudinary for userId={}: {}", userId, e.getMessage(), e);
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

    private String buildValue(String fullName, String phone, String gender,
                              String address, String avatarUrl) {
        return String.format(
                "{\"fullName\":\"%s\",\"phone\":\"%s\",\"gender\":\"%s\",\"address\":\"%s\",\"avatarUrl\":\"%s\"}",
                fullName, phone, gender, address, avatarUrl);
    }
}