package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateUserRequest {

    @Schema(description = "Email của người dùng", example = "nhanvien01@gmail.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email address")
    private String email;

    @Schema(description = "Danh sách Role của người dùng", example = "[\"KEEPER\"]")
    @NotEmpty(message = "At least one role is required")
    private Set<String> roleCodes;

    @Schema(description = "Đánh dấu tài khoản vĩnh viễn", example = "true")
    @NotNull(message = "Please specify account type")
    private Boolean isPermanent;

    @Schema(description = "Ngày hết hạn (nếu không vĩnh viễn)", example = "2026-12-31")
    private LocalDate expireDate;
}