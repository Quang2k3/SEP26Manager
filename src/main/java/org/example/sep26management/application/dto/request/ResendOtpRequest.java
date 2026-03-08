package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request DTO for resending OTP using the pending token from login
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResendOtpRequest {

    @Schema(description = "Token tạm thời dùng để xác nhận phiên tạo OTP", example = "token_abc123")
    @NotBlank(message = "Pending token is required")
    private String pendingToken;
}
