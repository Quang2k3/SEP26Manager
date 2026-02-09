package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.example.sep26management.domain.enums.UserStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * User response DTO for /me endpoint and user profile
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {
    private Long userId;
    private String email;
    private String fullName;
    private String phone;
    private String gender;
    private LocalDate dateOfBirth;
    private String address;
    private String avatarUrl;
    private Set<String> roleCodes;
    private UserStatus status;
    private Boolean isPermanent;
    private LocalDate expireDate;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
