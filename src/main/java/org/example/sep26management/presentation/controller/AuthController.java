package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.LogMessages;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.application.dto.request.*;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.LoginResponse;
import org.example.sep26management.application.dto.response.UserResponse;
import org.example.sep26management.application.service.AuthService;
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
@Tag(name = "Authentication", description = "Đăng nhập, đăng xuất và lấy thông tin người dùng hiện tại. "
                + "Login trả về JWT token hoặc yêu cầu OTP verification nếu email chưa được xác thực.")
public class AuthController {

        private final AuthService authService;

        /**
         * UC-AUTH-01: Login
         * POST /v1/auth/login
         */
        @PostMapping("/login")
        @Operation(summary = "Đăng nhập hệ thống", description = "Xác thực bằng email + password. Nếu email chưa verified → trả về pendingToken + requiresVerification=true "
                        + "(cần gọi /verify-otp). Nếu đã verified → trả về JWT token để sử dụng cho các API khác.")
        public ResponseEntity<ApiResponse<LoginResponse>> login(
                        @Valid @RequestBody LoginRequest request,
                        HttpServletRequest httpRequest) {
                String ipAddress = getClientIpAddress(httpRequest);
                String userAgent = httpRequest.getHeader("User-Agent");

                log.info(LogMessages.AUTH_LOGIN_REQUEST_FROM_IP, ipAddress, request.getEmail());

                LoginResponse response = authService.login(request, ipAddress, userAgent);

                if (Boolean.TRUE.equals(response.getRequiresVerification())) {
                        return ResponseEntity.ok(ApiResponse.success(
                                        MessageConstants.VERIFICATION_REQUIRED,
                                        response));
                }

                return ResponseEntity.ok(ApiResponse.success(
                                MessageConstants.LOGIN_SUCCESS,
                                response));
        }

        /**
         * UC-AUTH-02: Logout
         * POST /v1/auth/logout
         */
        @PostMapping("/logout")
        @Operation(summary = "Đăng xuất", description = "Ghi log đăng xuất và xóa security context. Client nên xóa JWT token ở phía mình sau khi gọi API này.")
        public ResponseEntity<ApiResponse<Void>> logout(
                        HttpServletRequest httpRequest) {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

                if (authentication != null && authentication.getDetails() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> details = (Map<String, Object>) authentication.getDetails();
                        Long userId = (Long) details.get("userId");

                        String ipAddress = getClientIpAddress(httpRequest);
                        String userAgent = httpRequest.getHeader("User-Agent");

                        log.info(LogMessages.AUTH_LOGOUT_REQUEST, userId);

                        ApiResponse<Void> response = authService.logout(userId, ipAddress, userAgent);

                        // Clear security context
                        SecurityContextHolder.clearContext();

                        return ResponseEntity.ok(response);
                }

                return ResponseEntity.ok(ApiResponse.success(MessageConstants.LOGOUT_SUCCESS));
        }

        /**
         * Get current user info from token
         * GET /v1/auth/me
         */
        @GetMapping("/me")
        @Operation(summary = "Lấy thông tin người dùng hiện tại", description = "Trả về thông tin user đang đăng nhập dựa trên JWT token. Bao gồm userId, email, fullName, roleCodes, warehouseIds.")
        public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser() {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

                if (authentication == null || !(authentication.getDetails() instanceof Map)) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(ApiResponse.error(MessageConstants.NOT_AUTHENTICATED));
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> details = (Map<String, Object>) authentication.getDetails();
                Long userId = (Long) details.get("userId");

                UserResponse user = authService.getCurrentUser(userId);

                return ResponseEntity.ok(ApiResponse.success(
                                MessageConstants.CURRENT_USER_SUCCESS,
                                user));
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