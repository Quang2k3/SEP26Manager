package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.example.sep26management.domain.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

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
    @Schema(description = "ID Người dùng", example = "10")
    private Long userId;

    @Schema(description = "Email (dùng để đăng nhập)", example = "admin@example.com")
    private String email;

    @Schema(description = "Họ và tên", example = "Nguyễn Văn A")
    private String fullName;

    @Schema(description = "Số điện thoại", example = "0901234567")
    private String phone;

    @Schema(description = "Giới tính", example = "MALE")
    private String gender;

    @Schema(description = "Ngày sinh", example = "1990-01-01")
    private LocalDate dateOfBirth;

    @Schema(description = "Địa chỉ", example = "123 Đường ABC, Q1, TP.HCM")
    private String address;

    @Schema(description = "URL ảnh đại diện", example = "https://example.com/avatar.jpg")
    private String avatarUrl;

    @Schema(description = "Danh sách role của user", example = "[\"ADMIN\", \"MANAGER\"]")
    private Set<String> roleCodes;

    @Schema(description = "Trạng thái tài khoản", example = "ACTIVE")
    private UserStatus status;

    @Schema(description = "Đánh dấu tài khoản vĩnh viễn", example = "true")
    private Boolean isPermanent;

    @Schema(description = "Ngày hết hạn (nếu không vĩnh viễn)", example = "2026-12-31")
    private LocalDate expireDate;

    @Schema(description = "Thời gian đăng nhập lần cuối", example = "2024-03-08T10:00:00")
    private LocalDateTime lastLoginAt;

    @Schema(description = "Thời gian tạo tài khoản", example = "2024-01-01T08:00:00")
    private LocalDateTime createdAt;
}
