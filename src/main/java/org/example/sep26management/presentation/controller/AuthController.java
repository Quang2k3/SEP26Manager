package org.example.sep26management.presentation.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.request.*;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.LoginResponse;
import org.example.sep26management.application.service.AuthService;
import org.example.sep26management.domain.enums.OtpType;
import org.example.sep26management.infrastructure.security.JwtTokenProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;


    /**
     * UC-AUTH-01: Login
     * POST /api/v1/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        log.info("Login request from IP: {} for email: {}", ipAddress, request.getEmail());

        LoginResponse response = authService.login(request, ipAddress, userAgent);

        if (Boolean.TRUE.equals(response.getRequiresVerification())) {
            return ResponseEntity.ok(ApiResponse.success(
                    "Verification required. OTP has been sent to your email.",
                    response
            ));
        }

        return ResponseEntity.ok(ApiResponse.success(
                "Login successful",
                response
        ));
    }

    /**
     * UC-AUTH-03: Verify First Login OTP
     * POST /api/v1/auth/verify-otp
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<LoginResponse>> verifyFirstLoginOtp(
            @RequestParam String email,
            @Valid @RequestBody VerifyOtpRequest request,
            HttpServletRequest httpRequest
    ) {
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        log.info("First login OTP verification for email: {}", email);

        LoginResponse response = authService.verifyFirstLoginOtp(
                email,
                request,
                ipAddress,
                userAgent
        );

        return ResponseEntity.ok(ApiResponse.success(
                "Your OTP verification successful.",
                response
        ));
    }

    /**
     * UC-PERS-01: Forgot Password - Send OTP
     * POST /api/v1/auth/forgot-password
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        log.info("Password reset OTP requested for email: {}", request.getEmail());

        ApiResponse<Void> response = authService.sendResetPasswordOtp(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Verify Reset Password OTP
     * POST /api/v1/auth/verify-reset-otp
     */
    @PostMapping("/verify-reset-otp")
    public ResponseEntity<ApiResponse<Void>> verifyResetPasswordOtp(
            @RequestParam String email,
            @Valid @RequestBody VerifyOtpRequest request
    ) {
        log.info("Reset password OTP verification for email: {}", email);

        ApiResponse<Void> response = authService.verifyResetPasswordOtp(email, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Reset Password
     * POST /api/v1/auth/reset-password
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @RequestParam String email,
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        log.info("Resetting password for email: {}", email);

        ApiResponse<Void> response = authService.resetPassword(email, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Resend OTP
     * POST /api/v1/auth/resend-otp
     */
    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<Void>> resendOtp(
            @RequestParam String email,
            @RequestParam OtpType type
    ) {
        log.info("Resending OTP for email: {} type: {}", email, type);

        ApiResponse<Void> response = authService.resendOtp(email, type);
        return ResponseEntity.ok(response);
    }

    /**
     * UC-AUTH-02: Logout
     * POST /api/v1/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest httpRequest
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getDetails() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> details = (Map<String, Object>) authentication.getDetails();
            Long userId = (Long) details.get("userId");

            String ipAddress = getClientIpAddress(httpRequest);
            String userAgent = httpRequest.getHeader("User-Agent");

            log.info("Logout request from user ID: {}", userId);

            ApiResponse<Void> response = authService.logout(userId, ipAddress, userAgent);

            // Clear security context
            SecurityContextHolder.clearContext();

            return ResponseEntity.ok(response);
        }

        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }

    /**
     * Get current user info from token
     * GET /api/v1/auth/me
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getDetails() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> details = (Map<String, Object>) authentication.getDetails();

            return ResponseEntity.ok(ApiResponse.success(
                    "Current user retrieved",
                    details
            ));
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Not authenticated"));
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}