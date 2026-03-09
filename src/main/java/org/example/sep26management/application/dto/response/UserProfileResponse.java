package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.example.sep26management.domain.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileResponse {
    @Schema(description = "ID Người dùng", example = "1")
    private Long userId;

    @Schema(description = "Email", example = "admin@example.com")
    private String email;

    @Schema(description = "Họ và tên", example = "Super Admin")
    private String fullName;

    @Schema(description = "Số điện thoại", example = "0987654321")
    private String phone;

    @Schema(description = "Giới tính", example = "MALE")
    private String gender;

    @Schema(description = "Ngày sinh", example = "2000-01-01")
    private LocalDate dateOfBirth;

    @Schema(description = "Địa chỉ liên lạc", example = "Hà Nội, Việt Nam")
    private String address;

    @Schema(description = "Đường dẫn File ảnh đại diện", example = "/uploads/avatar.png")
    private String avatarUrl;

    @Schema(description = "Danh sách mã Roles", example = "[\"ADMIN\"]")
    private Set<String> roleCodes; // Changed from UserRole to Set<String>

    @Schema(description = "Trạng thái", example = "ACTIVE")
    private UserStatus status;

    @Schema(description = "Vai trò vĩnh viễn (Không có ngày hết hạn)", example = "true")
    private Boolean isPermanent;

    @Schema(description = "Ngày hết hạn hợp đồng", example = "2026-12-31")
    private LocalDate expireDate;

    @Schema(description = "Lần đăng nhập cuối", example = "2026-03-08T10:00:00")
    private LocalDateTime lastLoginAt;

    @Schema(description = "Ngày tạo tài khoản", example = "2026-01-01T10:00:00")
    private LocalDateTime createdAt;
}