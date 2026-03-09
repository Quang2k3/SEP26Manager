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
public class LoginRequest {

    @Schema(description = "Email đăng nhập", example = "test@gmail.com")
    @NotBlank(message = "Please enter a valid username.")
    @Email(message = "Invalid email.")
    private String email;

    @Schema(description = "Mật khẩu", example = "123")
    @NotBlank(message = "Password is required.")
    private String password;

    @Schema(description = "Lưu nhớ đăng nhập (Sẽ kéo dài phiên đăng nhập nếu được hỗ trợ)", example = "true")
    private Boolean rememberMe;
}