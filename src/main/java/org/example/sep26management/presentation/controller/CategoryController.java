package org.example.sep26management.presentation.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.LogMessages;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.application.dto.request.CreateCategoryRequest;
import org.example.sep26management.application.dto.request.UpdateCategoryRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.CategoryResponse;
import org.example.sep26management.application.dto.response.CategoryTreeResponse;
import org.example.sep26management.application.service.CategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.example.sep26management.application.dto.response.CategoryTreeResponse;
import org.example.sep26management.application.dto.response.MapCategoryToZoneResponse;
import org.example.sep26management.application.dto.request.MapCategoryToZoneRequest;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/categories")
@RequiredArgsConstructor
@Slf4j
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * Create a new category
     * POST /api/v1/categories
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CreateCategoryRequest request,
            HttpServletRequest httpRequest) {
        try {
            Long createdBy = getCurrentUserId();
            String ipAddress = getClientIpAddress(httpRequest);
            String userAgent = httpRequest.getHeader("User-Agent");

            log.info(LogMessages.CATEGORY_CONTROLLER_CREATE_REQUEST, request.getCategoryCode(), createdBy);

            ApiResponse<CategoryResponse> response = categoryService.createCategory(
                    request, createdBy, ipAddress, userAgent);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error(LogMessages.CATEGORY_CONTROLLER_CREATE_FAILED, e.getMessage(), e);
            throw e; // Let GlobalExceptionHandler handle it
        }
    }

    /**
     * Update an existing category
     * PUT /api/v1/categories/{categoryId}
     */
    @PutMapping("/{categoryId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable Long categoryId,
            @Valid @RequestBody UpdateCategoryRequest request,
            HttpServletRequest httpRequest) {
        try {
            Long updatedBy = getCurrentUserId();
            String ipAddress = getClientIpAddress(httpRequest);
            String userAgent = httpRequest.getHeader("User-Agent");

            log.info(LogMessages.CATEGORY_CONTROLLER_UPDATE_REQUEST, categoryId, updatedBy);

            ApiResponse<CategoryResponse> response = categoryService.updateCategory(
                    categoryId, request, updatedBy, ipAddress, userAgent);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error(LogMessages.CATEGORY_CONTROLLER_UPDATE_FAILED, categoryId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get category by ID
     * GET /api/v1/categories/{categoryId}
     */
    @GetMapping("/{categoryId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategoryById(
            @PathVariable Long categoryId) {
        ApiResponse<CategoryResponse> response = categoryService.getCategoryById(categoryId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get all categories
     * GET /api/v1/categories
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllCategories() {
        ApiResponse<List<CategoryResponse>> response = categoryService.getAllCategories();
        return ResponseEntity.ok(response);
    }

    /**
     * UC: Deactivate category
     * PATCH /api/v1/categories/{categoryId}/deactivate
     */
    @PatchMapping("/{categoryId}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<CategoryResponse>> deactivateCategory(
            @PathVariable Long categoryId,
            HttpServletRequest httpRequest) {
        Long deactivatedBy = getCurrentUserId();
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        ApiResponse<CategoryResponse> response = categoryService.deactivateCategory(
                categoryId, deactivatedBy, ipAddress, userAgent);
        return ResponseEntity.ok(response);
    }

    /**
     * UC: View Category Tree
     * GET /api/v1/categories/tree?warehouseId=1
     * Shows tree with zone mapping info (convention: Z- + categoryCode)
     */
    @GetMapping("/tree")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CategoryTreeResponse>>> getCategoryTree(
            @RequestParam(required = false) Long warehouseId) {
        ApiResponse<List<CategoryTreeResponse>> response = categoryService.getCategoryTree(warehouseId);
        return ResponseEntity.ok(response);
    }

    /**
     * UC: Map Category to Zone (convention-based)
     * POST /api/v1/categories/{categoryId}/map-to-zone
     * Convention: zone_code = "Z-" + category_code
     */
    @PostMapping("/{categoryId}/map-to-zone")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<MapCategoryToZoneResponse>> mapCategoryToZone(
            @PathVariable Long categoryId,
            @Valid @RequestBody MapCategoryToZoneRequest request,
            HttpServletRequest httpRequest) {
        Long mappedBy = getCurrentUserId();
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        ApiResponse<MapCategoryToZoneResponse> response = categoryService.mapCategoryToZone(
                categoryId, request, mappedBy, ipAddress, userAgent);
        return ResponseEntity.ok(response);
    }

    // ============================================
    // HELPER METHODS (same pattern as UserManagementController)
    // ============================================

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