package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.example.sep26management.domain.enums.UserRole;
import org.example.sep26management.domain.enums.UserStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserListResponse {
    private List<UserItemDTO> users;
    private Long totalElements;
    private Integer totalPages;
    private Integer currentPage;
    private Integer pageSize;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserItemDTO {
        private Long userId;
        private String email;
        private String fullName;
        private String phone;
        private UserRole role;
        private UserStatus status;
        private Boolean isPermanent;
        private LocalDate expireDate;
        private LocalDateTime createdAt;
        private LocalDateTime lastLoginAt;
    }
}