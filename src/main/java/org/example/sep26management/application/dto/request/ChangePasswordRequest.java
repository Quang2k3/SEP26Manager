package org.example.sep26management.application.dto.request;

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
public class ChangePasswordRequest {

    @Schema(description = "Mật khẩu hiện tại", example = "OldPassword123!")
    @NotBlank(message = "Please fill in all fields.")
    private String currentPassword;

    @Schema(description = "Mật khẩu mới (8-48 ký tự, gồm ít nhất 1 Hoa, 1 Thường, 1 Số, 1 Ký tự đặc biệt)", example = "NewPassword123!")
    @NotBlank(message = "Please fill in all fields.")
    @Size(min = 8, max = 48, message = "New Password must be between 8 and 48 characters long.")
    @Pattern(regexp = ".*[A-Z].*", message = "Password must contain at least one uppercase letter.")
    @Pattern(regexp = ".*[a-z].*", message = "Password must contain at least one lowercase letter.")
    @Pattern(regexp = ".*[0-9].*", message = "Password must contain at least one number.")
    @Pattern(regexp = ".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*", message = "Password must contain at least one special character.")
    private String newPassword;

    @Schema(description = "Xác nhận lại mật khẩu mới", example = "NewPassword123!")
    @NotBlank(message = "Please fill in all fields.")
    private String confirmPassword;
}