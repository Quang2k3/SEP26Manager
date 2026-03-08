package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.example.sep26management.domain.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserListResponse {
    @Schema(description = "Danh sách người dùng hiện tại trong trang")
    private List<UserItemDTO> users;

    @Schema(description = "Tổng số người dùng", example = "100")
    private Long totalElements;

    @Schema(description = "Tổng số trang", example = "10")
    private Integer totalPages;

    @Schema(description = "Trang hiện tại (0-indexed)", example = "0")
    private Integer currentPage;

    @Schema(description = "Số lượng trong 1 trang", example = "10")
    private Integer pageSize;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserItemDTO {
        @Schema(description = "ID Người dùng", example = "10")
        private Long userId;

        @Schema(description = "Email", example = "user@example.com")
        private String email;

        @Schema(description = "Họ và tên", example = "Trần Thị B")
        private String fullName;

        @Schema(description = "Số điện thoại", example = "0901234567")
        private String phone;

        @Schema(description = "Danh sách Role", example = "[\"KEEPER\"]")
        private Set<String> roleCodes; // Changed from UserRole to Set<String>

        @Schema(description = "Trạng thái tài khoản", example = "ACTIVE")
        private UserStatus status;

        @Schema(description = "Đánh dấu tài khoản vĩnh viễn", example = "true")
        private Boolean isPermanent;

        @Schema(description = "Ngày hết hạn", example = "2026-12-31")
        private LocalDate expireDate;

        @Schema(description = "Ngày tạo", example = "2024-01-01T08:00:00")
        private LocalDateTime createdAt;

        @Schema(description = "Đăng nhập lần cuối", example = "2024-03-08T10:00:00")
        private LocalDateTime lastLoginAt;
    }
}