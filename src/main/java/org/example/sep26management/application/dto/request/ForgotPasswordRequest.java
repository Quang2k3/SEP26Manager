package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForgotPasswordRequest {

    @Schema(description = "Email của tài khoản cần khôi phục mật khẩu", example = "user@example.com")
    @NotBlank(message = "Please enter your email.")
    @Email(message = "Invalid email.")
    private String email;
}