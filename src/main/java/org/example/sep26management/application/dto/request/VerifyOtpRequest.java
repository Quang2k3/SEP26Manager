package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerifyOtpRequest {

    @Schema(description = "Token tạm thời dùng để xác nhận phiên tạo OTP", example = "token_abc123")
    @NotBlank(message = "Pending token is required")
    private String pendingToken;

    @Schema(description = "Mã OTP 6 số nhận từ Email", example = "123456")
    @NotBlank(message = "OTP is required")
    @Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
    private String otp;
}