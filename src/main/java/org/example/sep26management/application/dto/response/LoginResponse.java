package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponse {
    @Schema(description = "Chuỗi Token đăng nhập (JWT)", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String token;

    @Schema(description = "Loại Token", example = "Bearer")
    private String tokenType;

    @Schema(description = "Thời gian hết hạn của token", example = "3600")
    private Long expiresIn;

    @Schema(description = "Cờ đánh dấu tài khoản đang bị khoá cần OTP (Trường hợp Login ở thiết bị mới)", example = "false")
    private Boolean requiresVerification;

    @Schema(description = "Token tạm để gửi lại OTP nếu requiresVerification = true", example = "temp_token_abc")
    private String pendingToken;

    @Schema(description = "Thông tin cơ bản của người dùng")
    private UserInfoDTO user;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserInfoDTO {
        @Schema(description = "ID Người dùng", example = "1")
        private Long userId;

        @Schema(description = "Email người dùng", example = "admin@example.com")
        private String email;

        @Schema(description = "Họ và tên", example = "Super Admin")
        private String fullName;

        @Schema(description = "Danh sách mã quyền (Roles)", example = "[\"ADMIN\", \"MANAGER\"]")
        private Set<String> roleCodes;

        @Schema(description = "Đường dẫn ảnh đại diện", example = "/uploads/avatar.png")
        private String avatarUrl;

        @Schema(description = "Danh sách ID các kho được phân công quản lý", example = "[1, 2]")
        private List<Long> warehouseIds; // Danh sách kho được phân công
    }
}