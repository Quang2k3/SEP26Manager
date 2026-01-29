package org.example.sep26management.application.mapper;

import org.example.sep26management.application.dto.response.UserProfileResponse;
import org.example.sep26management.domain.entity.User;
import org.example.sep26management.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Component;

/**
 * Unified Mapper for User-related conversions
 */
@Component
public class UserMapper {

    /**
     * Map UserEntity (Persistence) to UserProfileResponse (DTO)
     * Used in: ProfileService, UserManagementService
     */
    public UserProfileResponse toProfileResponse(UserEntity entity) {
        if (entity == null) {
            return null;
        }

        return UserProfileResponse.builder()
                .userId(entity.getUserId())
                .email(entity.getEmail())
                .fullName(entity.getFullName())
                .phone(entity.getPhone())
                .gender(entity.getGender())
                .dateOfBirth(entity.getDateOfBirth())
                .address(entity.getAddress())
                .avatarUrl(entity.getAvatarUrl())
                .role(entity.getRole())
                .status(entity.getStatus())
                .isPermanent(entity.getIsPermanent())
                .expireDate(entity.getExpireDate())
                .lastLoginAt(entity.getLastLoginAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    /**
     * Map UserEntity (Persistence) to User (Domain)
     * Used in: AuthService (if needed)
     */
    public User toDomain(UserEntity entity) {
        if (entity == null) {
            return null;
        }

        return User.builder()
                .userId(entity.getUserId())
                .email(entity.getEmail())
                .passwordHash(entity.getPasswordHash())
                .fullName(entity.getFullName())
                .phone(entity.getPhone())
                .gender(entity.getGender())
                .dateOfBirth(entity.getDateOfBirth())
                .address(entity.getAddress())
                .avatarUrl(entity.getAvatarUrl())
                .role(entity.getRole())
                .status(entity.getStatus())
                .isPermanent(entity.getIsPermanent())
                .expireDate(entity.getExpireDate())
                .isFirstLogin(entity.getIsFirstLogin())
                .lastLoginAt(entity.getLastLoginAt())
                .failedLoginAttempts(entity.getFailedLoginAttempts())
                .lockedUntil(entity.getLockedUntil())
                .passwordChangedAt(entity.getPasswordChangedAt())
                .createdAt(entity.getCreatedAt())
                .createdBy(entity.getCreatedBy())
                .updatedAt(entity.getUpdatedAt())
                .updatedBy(entity.getUpdatedBy())
                .build();
    }

    /**
     * Map User (Domain) to UserEntity (Persistence)
     * Used in: Domain-driven services (if needed)
     */
    public UserEntity toEntity(User domain) {
        if (domain == null) {
            return null;
        }

        return UserEntity.builder()
                .userId(domain.getUserId())
                .email(domain.getEmail())
                .passwordHash(domain.getPasswordHash())
                .fullName(domain.getFullName())
                .phone(domain.getPhone())
                .gender(domain.getGender())
                .dateOfBirth(domain.getDateOfBirth())
                .address(domain.getAddress())
                .avatarUrl(domain.getAvatarUrl())
                .role(domain.getRole())
                .status(domain.getStatus())
                .isPermanent(domain.getIsPermanent())
                .expireDate(domain.getExpireDate())
                .isFirstLogin(domain.getIsFirstLogin())
                .lastLoginAt(domain.getLastLoginAt())
                .failedLoginAttempts(domain.getFailedLoginAttempts())
                .lockedUntil(domain.getLockedUntil())
                .passwordChangedAt(domain.getPasswordChangedAt())
                .createdAt(domain.getCreatedAt())
                .createdBy(domain.getCreatedBy())
                .updatedAt(domain.getUpdatedAt())
                .updatedBy(domain.getUpdatedBy())
                .build();
    }
}