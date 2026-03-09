package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResetPasswordRequest {

    @Schema(description = "Email của tài khoản cần tạo lại mật khẩu", example = "user@example.com")
    @NotBlank(message = "Email is required.")
    @Email(message = "Invalid email format.")
    private String email;

    @Schema(description = "Mã OTP hợp lệ nhận từ email", example = "123456")
    @NotBlank(message = "OTP code is required.")
    private String otp;

    @Schema(description = "Mật khẩu mới (8-48 ký tự, gồm ít nhất 1 Hoa, 1 Thường, 1 Số, 1 Ký tự đặc biệt)", example = "NewPassword123!")
    @NotBlank(message = "Please enter the new password and confirm password.")
    @Size(min = 8, max = 48, message = "The password must be at least 8 characters long.")
    @Pattern(regexp = ".*[A-Z].*", message = "The password must contain at least one uppercase letter.")
    @Pattern(regexp = ".*[a-z].*", message = "The password must contain at least one lowercase letter.")
    @Pattern(regexp = ".*[0-9].*", message = "The password must contain at least one number.")
    @Pattern(regexp = ".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*", message = "The password must contain at least one special character.")
    private String newPassword;

    @Schema(description = "Xác nhận lại mật khẩu mới", example = "NewPassword123!")
    @NotBlank(message = "Please enter the new password and confirm password.")
    private String confirmPassword;
}