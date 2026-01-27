package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.example.sep26management.domain.enums.UserRole;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateUserRequest {

    @NotBlank(message = "Email and Role is required")
    @Email(message = "Invalid email address")
    private String email;

    @NotNull(message = "Email and Role is required")
    private UserRole role;

    @NotNull(message = "Please specify account type")
    private Boolean isPermanent;

    private LocalDate expireDate;
}