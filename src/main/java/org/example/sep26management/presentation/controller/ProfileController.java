package org.example.sep26management.presentation.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.request.ChangePasswordRequest;
import org.example.sep26management.application.dto.request.UpdateProfileRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.UserProfileResponse;
import org.example.sep26management.application.service.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/profile")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class ProfileController {

    private final ProfileService profileService;

    /**
     * UC-PERS-03: View Personal Profile
     * GET /api/v1/profile
     */
    @GetMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile() {
        Long userId = getCurrentUserId();
        log.info("Fetching profile for user ID: {}", userId);

        ApiResponse<UserProfileResponse> response = profileService.getProfile(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * UC-PERS-04: Update Personal Profile
     * PUT /api/v1/profile
     */
    @PutMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @Valid @ModelAttribute UpdateProfileRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = getCurrentUserId();
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        log.info("Updating profile for user ID: {}", userId);

        ApiResponse<UserProfileResponse> response = profileService.updateProfile(
                userId,
                request,
                ipAddress,
                userAgent
        );

        return ResponseEntity.ok(response);
    }

    /**
     * UC-PERS-02: Change Password
     * POST /api/v1/profile/change-password
     */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = getCurrentUserId();
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        log.info("Changing password for user ID: {}", userId);

        ApiResponse<Void> response = profileService.changePassword(
                userId,
                request,
                ipAddress,
                userAgent
        );

        return ResponseEntity.ok(response);
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getDetails() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> details = (Map<String, Object>) authentication.getDetails();
            return (Long) details.get("userId");
        }

        throw new RuntimeException("User not authenticated");
    }


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