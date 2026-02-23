package org.example.sep26management.infrastructure.mapper;

import org.example.sep26management.application.dto.response.UserListResponse;
import org.example.sep26management.application.dto.response.UserProfileResponse;
import org.example.sep26management.application.dto.response.UserResponse;
import org.example.sep26management.infrastructure.persistence.entity.RoleEntity;
import org.example.sep26management.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * UserMapper — maps UserEntity to response DTOs.
 * <p>
 * Replaces inline builder blocks scattered across:
 * - UserManagementService (toResponse, toListItem)
 * - ProfileService (toProfileResponse)
 * - AuthService (UserResponse in getCurrentUser)
 */
@Component
public class UserMapper {

    /**
     * Map UserEntity → UserResponse
     * (used in createUser, assignRole, changeStatus, getCurrentUser)
     */
    public UserResponse toResponse(UserEntity entity) {
        if (entity == null)
            return null;

        return UserResponse.builder()
                .userId(entity.getUserId())
                .email(entity.getEmail())
                .fullName(entity.getFullName())
                .phone(entity.getPhone())
                .gender(entity.getGender())
                .dateOfBirth(entity.getDateOfBirth())
                .address(entity.getAddress())
                .avatarUrl(entity.getAvatarUrl())
                .roleCodes(extractRoleCodes(entity))
                .status(entity.getStatus())
                .isPermanent(entity.getIsPermanent())
                .expireDate(entity.getExpireDate())
                .lastLoginAt(entity.getLastLoginAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    /**
     * Map UserEntity → UserProfileResponse
     * (used in getProfile, updateProfile in ProfileService)
     */
    public UserProfileResponse toProfileResponse(UserEntity entity) {
        if (entity == null)
            return null;

        return UserProfileResponse.builder()
                .userId(entity.getUserId())
                .email(entity.getEmail())
                .fullName(entity.getFullName())
                .phone(entity.getPhone())
                .gender(entity.getGender())
                .dateOfBirth(entity.getDateOfBirth())
                .address(entity.getAddress())
                .avatarUrl(entity.getAvatarUrl())
                .roleCodes(extractRoleCodes(entity))
                .status(entity.getStatus())
                .isPermanent(entity.getIsPermanent())
                .expireDate(entity.getExpireDate())
                .lastLoginAt(entity.getLastLoginAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    /**
     * Map UserEntity → UserListResponse.UserItemDTO
     * (used in getUserList pagination in UserManagementService)
     */
    public UserListResponse.UserItemDTO toListItem(UserEntity entity) {
        if (entity == null)
            return null;

        return UserListResponse.UserItemDTO.builder()
                .userId(entity.getUserId())
                .email(entity.getEmail())
                .fullName(entity.getFullName())
                .phone(entity.getPhone())
                .roleCodes(extractRoleCodes(entity))
                .status(entity.getStatus())
                .isPermanent(entity.getIsPermanent())
                .expireDate(entity.getExpireDate())
                .createdAt(entity.getCreatedAt())
                .lastLoginAt(entity.getLastLoginAt())
                .build();
    }

    // --------------------------------------------------------
    // Private helpers
    // --------------------------------------------------------

    private Set<String> extractRoleCodes(UserEntity entity) {
        if (entity.getRoles() == null)
            return Set.of();
        return entity.getRoles().stream()
                .map(RoleEntity::getRoleCode)
                .collect(Collectors.toSet());
    }
}
