package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerifyOtpRequest {

    @NotBlank(message = "Please enter the OTP.")
    @Pattern(regexp = "^[0-9]{6}$", message = "The OTP code is incorrect.")
    private String otpCode;
}