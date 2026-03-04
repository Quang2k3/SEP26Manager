package org.example.sep26management.presentation.controller;

import io.jsonwebtoken.JwtException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.LogMessages;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.application.dto.request.ForgotPasswordRequest;
import org.example.sep26management.application.dto.request.ResendOtpRequest;
import org.example.sep26management.application.dto.request.ResetPasswordRequest;
import org.example.sep26management.application.dto.request.VerifyOtpRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.LoginResponse;
import org.example.sep26management.application.service.AuthService;
import org.example.sep26management.application.service.OtpService;
import org.example.sep26management.infrastructure.security.JwtTokenProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for OTP (One-Time Password) operations.
 * Uses pendingToken from login response instead of requiring email input.
 */
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication - OTP", description = "Email verification with OTP, forgot password & reset password")
public class OtpController {

        private final OtpService otpService;
        private final AuthService authService;
        private final JwtTokenProvider jwtTokenProvider;

        /**
         * Verify OTP and complete email verification.
         * Email is extracted from the pendingToken returned during login.
         *
         * @param request     VerifyOtpRequest containing pendingToken and OTP code
         * @param httpRequest HTTP servlet request for IP address extraction
         * @return LoginResponse with JWT token if OTP is valid
         */
        @PostMapping("/verify-otp")
        @Operation(summary = "Verify OTP and complete login", description = "Verifies the OTP code using the pendingToken from login response. "
                        + "If valid, marks email as verified and returns JWT token for authentication.")
        public ResponseEntity<ApiResponse<LoginResponse>> verifyOtp(
                        @Valid @RequestBody VerifyOtpRequest request,
                        HttpServletRequest httpRequest) {

                // Extract email from pending token
                String email;
                try {
                        email = jwtTokenProvider.getEmailFromPendingToken(request.getPendingToken());
                } catch (JwtException e) {
                        log.warn(LogMessages.OTP_CONTROLLER_INVALID_PENDING_TOKEN, e.getMessage());
                        return ResponseEntity.badRequest().body(
                                        ApiResponse.error(MessageConstants.INVALID_PENDING_TOKEN));
                }

                log.info(LogMessages.OTP_CONTROLLER_VERIFICATION_REQUEST, email);

                // Verify OTP
                boolean isValid = otpService.verifyOtp(email, request.getOtp());

                if (!isValid) {
                        log.warn(LogMessages.OTP_CONTROLLER_INVALID_OTP, email);
                        return ResponseEntity.badRequest().body(
                                        ApiResponse.error(MessageConstants.OTP_INVALID));
                }

                // Get client information
                String ipAddress = getClientIpAddress(httpRequest);
                String userAgent = httpRequest.getHeader("User-Agent");

                // Complete email verification and generate JWT token
                LoginResponse response = authService.completeEmailVerification(
                                email,
                                ipAddress,
                                userAgent);

                log.info(LogMessages.OTP_CONTROLLER_VERIFICATION_SUCCESS, email);

                return ResponseEntity.ok(ApiResponse.success(
                                MessageConstants.OTP_VERIFIED,
                                response));
        }

        /**
         * Resend OTP using the pendingToken from login response.
         * Email is extracted from the pendingToken.
         *
         * @param request ResendOtpRequest containing pendingToken
         * @return Success response
         */
        @PostMapping("/resend-otp")
        @Operation(summary = "Resend OTP", description = "Resends a new OTP using the pendingToken from login response. "
                        +
                        "Can only be requested after 2 minutes cooldown period.")
        public ResponseEntity<ApiResponse<Void>> resendOtp(
                        @Valid @RequestBody ResendOtpRequest request) {

                // Extract email from pending token
                String email;
                try {
                        email = jwtTokenProvider.getEmailFromPendingToken(request.getPendingToken());
                } catch (JwtException e) {
                        log.warn(LogMessages.OTP_CONTROLLER_INVALID_PENDING_TOKEN_RESEND, e.getMessage());
                        return ResponseEntity.badRequest().body(
                                        ApiResponse.error(MessageConstants.INVALID_PENDING_TOKEN));
                }

                log.info(LogMessages.OTP_CONTROLLER_RESEND_REQUEST, email);

                otpService.generateAndSendOtp(email);

                log.info(LogMessages.OTP_CONTROLLER_RESEND_SUCCESS, email);

                return ResponseEntity.ok(ApiResponse.success(
                                MessageConstants.OTP_RESENT));
        }

        /**
         * UC-AUTH-04: Forgot Password — gửi OTP qua email
         * POST /api/v1/auth/forgot-password
         */
        @PostMapping("/forgot-password")
        @Operation(summary = "Quên mật khẩu — gửi OTP", description = "Nhập email để nhận mã OTP qua email. Mã OTP có hiệu lực trong vài phút. "
                        + "Nếu email không tồn tại hoặc tài khoản bị vô hiệu hóa → trả lỗi.")
        public ResponseEntity<ApiResponse<Void>> forgotPassword(
                        @Valid @RequestBody ForgotPasswordRequest request) {
                log.info("POST /v1/auth/forgot-password for email: {}", request.getEmail());
                authService.forgotPassword(request.getEmail());
                return ResponseEntity.ok(ApiResponse.success(
                                "Mã OTP đã được gửi đến email của bạn."));
        }

        /**
         * UC-AUTH-05: Reset Password — verify OTP + đặt mật khẩu mới
         * POST /api/v1/auth/reset-password
         */
        @PostMapping("/reset-password")
        @Operation(summary = "Đặt lại mật khẩu bằng OTP", description = "Nhập email + OTP + mật khẩu mới để đặt lại mật khẩu. "
                        + "OTP phải đúng và còn hiệu lực. Mật khẩu mới phải có ít nhất 8 ký tự, "
                        + "chứa chữ hoa, chữ thường, số và ký tự đặc biệt. "
                        + "Nếu tài khoản đang bị khóa (LOCKED) → tự động mở khóa sau khi reset.")
        public ResponseEntity<ApiResponse<Void>> resetPassword(
                        @Valid @RequestBody ResetPasswordRequest request,
                        HttpServletRequest httpRequest) {
                String ipAddress = getClientIpAddress(httpRequest);
                String userAgent = httpRequest.getHeader("User-Agent");

                log.info("POST /v1/auth/reset-password for email: {}", request.getEmail());
                authService.resetPassword(
                                request.getEmail(),
                                request.getOtp(),
                                request.getNewPassword(),
                                request.getConfirmPassword(),
                                ipAddress,
                                userAgent);
                return ResponseEntity.ok(ApiResponse.success(
                                "Mật khẩu đã được đặt lại thành công. Vui lòng đăng nhập."));
        }

        // ==================== Helper Methods ====================

        /**
         * Extract client IP address from HTTP request.
         * Handles X-Forwarded-For header for proxy/load balancer scenarios.
         */
        private String getClientIpAddress(HttpServletRequest request) {
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                        return xForwardedFor.split(",")[0].trim();
                }
                return request.getRemoteAddr();
        }
}
