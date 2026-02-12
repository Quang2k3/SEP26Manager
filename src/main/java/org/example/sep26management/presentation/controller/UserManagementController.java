package org.example.sep26management.presentation.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.LogMessages;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.application.dto.request.AssignRoleRequest;
import org.example.sep26management.application.dto.request.CreateUserRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.UserListResponse;
import org.example.sep26management.application.dto.response.UserResponse;
import org.example.sep26management.application.service.UserManagementService;
import org.example.sep26management.domain.enums.UserStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * UserManagementController - Handles user management operations
 * Requires ADMIN or MANAGER role to access
 */
@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('MANAGER')")
public class UserManagementController {

    private final UserManagementService userManagementService;

    /**
     * Create a new user
     * POST /api/v1/users/create
     * 
     * @param request     CreateUserRequest with user details
     * @param httpRequest HTTP request for IP and user agent extraction
     * @return ApiResponse with created user details
     */
    @PostMapping("/create-user")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request,
            HttpServletRequest httpRequest) {
        try {
            Long createdBy = getCurrentUserId();
            String ipAddress = getClientIpAddress(httpRequest);
            String userAgent = httpRequest.getHeader("User-Agent");

            log.info(LogMessages.USER_CREATING, request.getEmail());

            ApiResponse<UserResponse> response = userManagementService.createUser(
                    request,
                    createdBy,
                    ipAddress,
                    userAgent);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error(LogMessages.USER_CREATION_FAILED, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to create user: " + e.getMessage()));
        }
    }

    /**
     * Get list of users with pagination and filtering
     * GET /api/v1/users
     * 
     * @param keyword Optional keyword to search by email or name
     * @param status  Optional status filter (ACTIVE, INACTIVE, PENDING_VERIFY,
     *                LOCKED)
     * @param page    Page number (default: 0)
     * @param size    Page size (default: 10)
     * @return ApiResponse with paginated user list
     */
    @GetMapping("/list-users")
    public ResponseEntity<ApiResponse<UserListResponse>> getUserList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            log.info(LogMessages.USER_LIST_REQUEST, keyword, status, page, size);

            ApiResponse<UserListResponse> response = userManagementService.getUserList(
                    keyword, status, page, size);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error(LogMessages.USER_LIST_FETCH_FAILED, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to get user list: " + e.getMessage()));
        }
    }

    /**
     * Assign a new role to a user
     * PUT /api/v1/users/{userId}/assign-role
     * Only MANAGER can perform this action (enforced by class-level @PreAuthorize)
     * 
     * @param userId      Target user ID whose role will be changed
     * @param request     AssignRoleRequest containing the new role
     * @param httpRequest HTTP request for IP and user agent extraction
     * @return ApiResponse with updated user details
     */
    @PutMapping("/{userId}/assign-role")
    public ResponseEntity<ApiResponse<UserResponse>> assignRole(
            @PathVariable Long userId,
            @Valid @RequestBody AssignRoleRequest request,
            HttpServletRequest httpRequest) {
        try {
            Long managerId = getCurrentUserId();
            String managerEmail = getCurrentUserEmail();
            String ipAddress = getClientIpAddress(httpRequest);
            String userAgent = httpRequest.getHeader("User-Agent");

            log.info(LogMessages.USER_ROLE_ASSIGNMENT_REQUEST, managerId, request.getRole(), userId);

            ApiResponse<UserResponse> response = userManagementService.assignRole(
                    userId,
                    request.getRole().name(),
                    managerId,
                    managerEmail,
                    ipAddress,
                    userAgent);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error(LogMessages.USER_ROLE_ASSIGNMENT_CONTROLLER_FAILED, userId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to assign role: " + e.getMessage()));
        }
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    /**
     * Get current user ID from security context
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            throw new RuntimeException(MessageConstants.NOT_AUTHENTICATED);
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

        throw new RuntimeException(MessageConstants.USER_ID_NOT_FOUND);
    }

    /**
     * Get current user email from security context
     */
    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            throw new RuntimeException(MessageConstants.NOT_AUTHENTICATED);
        }

        Object details = authentication.getDetails();
        if (details instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> detailsMap = (Map<String, Object>) details;
            Object emailObj = detailsMap.get("email");

            if (emailObj != null) {
                return emailObj.toString();
            }
        }

        // Fallback to principal name if email not in details
        return authentication.getName();
    }

    /**
     * Extract client IP address from HTTP request
     * Checks X-Forwarded-For and X-Real-IP headers first
     */
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