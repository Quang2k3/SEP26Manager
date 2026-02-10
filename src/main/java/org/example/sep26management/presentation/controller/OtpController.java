package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.request.SendOtpRequest;
import org.example.sep26management.application.dto.request.VerifyOtpRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.LoginResponse;
import org.example.sep26management.application.service.AuthService;
import org.example.sep26management.application.service.OtpService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for OTP (One-Time Password) operations
 * Handles email verification workflow
 */
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication - OTP", description = "Email verification with OTP")
public class OtpController {

    private final OtpService otpService;
    private final AuthService authService;

    /**
     * Send OTP to email
     * 
     * @param request SendOtpRequest containing email
     * @return Success response
     */
    @PostMapping("/send-otp")
    @Operation(summary = "Send OTP for email verification", description = "Generates a 6-digit OTP and sends it to the user's email. "
            +
            "OTP expires in 5 minutes. User must wait 2 minutes before requesting a new OTP.")
    public ResponseEntity<ApiResponse<Void>> sendOtp(
            @Valid @RequestBody SendOtpRequest request) {

        log.info("OTP send request for email: {}", request.getEmail());

        otpService.generateAndSendOtp(request.getEmail());

        return ResponseEntity.ok(ApiResponse.success(
                "OTP has been sent to your email. Please check your inbox."));
    }

    /**
     * Verify OTP and complete email verification
     * 
     * @param request     VerifyOtpRequest containing email and OTP code
     * @param httpRequest HTTP servlet request for IP address extraction
     * @return LoginResponse with JWT token if OTP is valid
     */
    @PostMapping("/verify-otp")
    @Operation(summary = "Verify OTP and complete login", description = "Verifies the OTP code sent to the user's email. "
            +
            "If valid, marks email as verified and returns JWT token for authentication.")
    public ResponseEntity<ApiResponse<LoginResponse>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request,
            HttpServletRequest httpRequest) {

        log.info("OTP verification request for email: {}", request.getEmail());

        // Verify OTP
        boolean isValid = otpService.verifyOtp(request.getEmail(), request.getOtp());

        if (!isValid) {
            log.warn("Invalid OTP provided for email: {}", request.getEmail());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Invalid or expired OTP code. Please try again."));
        }

        // Get client information
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        // Complete email verification and generate JWT token
        LoginResponse response = authService.completeEmailVerification(
                request.getEmail(),
                ipAddress,
                userAgent);

        log.info("Email verification successful for: {}", request.getEmail());

        return ResponseEntity.ok(ApiResponse.success(
                "Email verified successfully. You are now logged in.",
                response));
    }

    /**
     * Resend OTP to email
     * 
     * @param request SendOtpRequest containing email
     * @return Success response
     */
    @PostMapping("/resend-otp")
    @Operation(summary = "Resend OTP", description = "Resends a new OTP to the user's email. " +
            "Can only be requested after 2 minutes cooldown period.")
    public ResponseEntity<ApiResponse<Void>> resendOtp(
            @Valid @RequestBody SendOtpRequest request) {

        log.info("OTP resend request for email: {}", request.getEmail());

        otpService.generateAndSendOtp(request.getEmail());

        log.info("OTP resent successfully to: {}", request.getEmail());

        return ResponseEntity.ok(ApiResponse.success(
                "A new OTP has been sent to your email. Please check your inbox."));
    }

    // ==================== Helper Methods ====================

    /**
     * Extract client IP address from HTTP request
     * Handles X-Forwarded-For header for proxy/load balancer scenarios
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
