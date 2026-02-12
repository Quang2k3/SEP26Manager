package org.example.sep26management.infrastructure.mapper;

import org.example.sep26management.domain.entity.User;
import org.example.sep26management.infrastructure.persistence.entity.RoleEntity;
import org.example.sep26management.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mapper between UserEntity (JPA persistence) and User (domain model)
 */
@Component
public class UserEntityMapper {

    /**
     * Convert JPA UserEntity to domain User
     */
    public User toDomain(UserEntity entity) {
        if (entity == null) {
            return null;
        }

        // Extract role codes from RoleEntity set
        Set<String> roleCodes = entity.getRoles() != null
                ? entity.getRoles().stream()
                        .map(RoleEntity::getRoleCode)
                        .collect(Collectors.toSet())
                : null;

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
                .roleCodes(roleCodes)
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

    public void updateEntity(UserEntity entity, User domain) {
        if (entity == null || domain == null) {
            return;
        }

        entity.setEmail(domain.getEmail());
        entity.setPasswordHash(domain.getPasswordHash());
        entity.setFullName(domain.getFullName());
        entity.setPhone(domain.getPhone());
        entity.setGender(domain.getGender());
        entity.setDateOfBirth(domain.getDateOfBirth());
        entity.setAddress(domain.getAddress());
        entity.setAvatarUrl(domain.getAvatarUrl());
        entity.setStatus(domain.getStatus());
        entity.setIsPermanent(domain.getIsPermanent());
        entity.setExpireDate(domain.getExpireDate());
        entity.setIsFirstLogin(domain.getIsFirstLogin());
        entity.setLastLoginAt(domain.getLastLoginAt());
        entity.setFailedLoginAttempts(domain.getFailedLoginAttempts());
        entity.setLockedUntil(domain.getLockedUntil());
        entity.setPasswordChangedAt(domain.getPasswordChangedAt());
    }
}