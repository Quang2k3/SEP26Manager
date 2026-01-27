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

    @GetMapping
    public ResponseEntity<ApiResponse<UserListResponse>> getUserList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        try {
            log.info("Fetching user list - page: {}, size: {}", page, size);

            ApiResponse<UserListResponse> response = userManagementService.getUserList(
                    keyword,
                    role,
                    status,
                    page,
                    size
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting user list: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to get user list: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserById(@PathVariable Long id) {
        try {
            log.info("Fetching user by ID: {}", id);

            ApiResponse<UserProfileResponse> response = userManagementService.getUserById(id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting user by ID: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to get user: " + e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request,
            HttpServletRequest httpRequest
    ) {
        try {
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
        } catch (Exception e) {
            log.error("Error creating user: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to create user: " + e.getMessage()));
        }
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<ApiResponse<UserProfileResponse>> assignRole(
            @PathVariable Long id,
            @Valid @RequestBody AssignRoleRequest request,
            HttpServletRequest httpRequest
    ) {
        try {
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
        } catch (Exception e) {
            log.error("Error assigning role: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to assign role: " + e.getMessage()));
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request,
            HttpServletRequest httpRequest
    ) {
        try {
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
        } catch (Exception e) {
            log.error("Error updating user status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to update status: " + e.getMessage()));
        }
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            throw new RuntimeException("Not authenticated");
        }

        Object details = authentication.getDetails();
        if (details instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> detailsMap = (Map<String, Object>) details;
            Object userIdObj = detailsMap.get("userId");

            if (userIdObj instanceof Long) {
                return (Long) userIdObj;
            } else if (userIdObj instanceof Integer) {
                return ((Integer) userIdObj).longValue();
            } else if (userIdObj != null) {
                return Long.parseLong(userIdObj.toString());
            }
        }

        throw new RuntimeException("User ID not found in authentication");
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}