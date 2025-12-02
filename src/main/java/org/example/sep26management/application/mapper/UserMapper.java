package org.example.sep26management.application.mapper;

import org.example.sep26management.application.dto.UserDto;
import org.example.sep26management.domain.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    
    public UserDto toDto(User user) {
        if (user == null) {
            return null;
        }
        
        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .enabled(user.getEnabled())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
    
    public User toEntity(UserDto dto) {
        if (dto == null) {
            return null;
        }
        
        User user = User.builder()
                .username(dto.getUsername())
                .email(dto.getEmail())
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .enabled(dto.getEnabled())
                .role(dto.getRole())
                .build();

        user.setId(dto.getId());
        user.setCreatedAt(dto.getCreatedAt());
        user.setUpdatedAt(dto.getUpdatedAt());

        return user;
    }
}

