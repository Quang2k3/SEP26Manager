package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequest {

    @NotBlank(message = "Please enter a valid username.")
    @Email(message = "Invalid email.")
    private String email;

    @NotBlank(message = "Password is required.")
    private String password;

    private Boolean rememberMe;
}