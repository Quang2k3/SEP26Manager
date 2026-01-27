package org.example.sep26management.presentation.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.request.AssignRoleRequest;
import org.example.sep26management.application.dto.request.CreateUserRequest;
import org.example.sep26management.application.dto.request.UpdateUserStatusRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.UserListResponse;
import org.example.sep26management.application.dto.response.UserProfileResponse;
import org.example.sep26management.application.service.UserManagementService;
import org.example.sep26management.domain.enums.UserRole;
import org.example.sep26management.domain.enums.UserStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('MANAGER')")
public class UserManagementController {

    private final UserManagementService userManagementService;

    /**
     * UC-MUA-02: View List User
     * GET /api/v1/users
     */
    @GetMapping
    public ResponseEntity<ApiResponse<UserListResponse>> getUserList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("Fetching user list - page: {}, size: {}", page, size);

        ApiResponse<UserListResponse> response = userManagementService.getUserList(
                keyword,
                role,
                status,
                page,
                size
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Get User by ID
     * GET /api/v1/users/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserById(
            @PathVariable Long id
    ) {
        log.info("Fetching user by ID: {}", id);

        ApiResponse<UserProfileResponse> response = userManagementService.getUserById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * UC-MUA-01: Create Account User
     * POST /api/v1/users
     */
    @PostMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request,
            HttpServletRequest httpRequest
    ) {
        Long currentUserId = getCurrentUserId();
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        log.info("Creating new user with email: {}", request.getEmail());

        ApiResponse<UserProfileResponse> response = userManagementService.createUser(
                request,
                currentUserId,
                ipAddress,
                userAgent
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * UC-MUA-03: Assign Role
     * PATCH /api/v1/users/{id}/role
     */
    @PatchMapping("/{id}/role")
    public ResponseEntity<ApiResponse<UserProfileResponse>> assignRole(
            @PathVariable Long id,
            @Valid @RequestBody AssignRoleRequest request,
            HttpServletRequest httpRequest
    ) {
        Long currentUserId = getCurrentUserId();
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        log.info("Assigning role {} to user ID: {}", request.getRole(), id);

        ApiResponse<UserProfileResponse> response = userManagementService.assignRole(
                id,
                request,
                currentUserId,
                ipAddress,
                userAgent
        );

        return ResponseEntity.ok(response);
    }

    /**
     * UC-MUA-04: Active / Inactive User
     * PATCH /api/v1/users/{id}/status
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request,
            HttpServletRequest httpRequest
    ) {
        Long currentUserId = getCurrentUserId();
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        log.info("Updating status for user ID: {} to {}", id, request.getStatus());

        ApiResponse<UserProfileResponse> response = userManagementService.updateUserStatus(
                id,
                request,
                currentUserId,
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