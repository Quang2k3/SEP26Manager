package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponse {
    private String token;
    private String tokenType;
    private Long expiresIn;
    private Boolean requiresVerification;
    private String pendingToken;
    private UserInfoDTO user;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserInfoDTO {
        private Long userId;
        private String email;
        private String fullName;
        private Set<String> roleCodes; // Changed from UserRole to Set<String>
        private String avatarUrl;
    }
}