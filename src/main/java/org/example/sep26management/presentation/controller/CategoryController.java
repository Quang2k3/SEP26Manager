package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.example.sep26management.application.dto.response.PageResponse;
import org.example.sep26management.application.dto.response.MapCategoryToZoneResponse;
import org.example.sep26management.application.service.CategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/categories")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Category Management", description = "Quản lý danh mục hàng hóa (Category). "
        + "Mỗi SKU thuộc 1 category, và category được mapping với zone theo convention Z-{categoryCode} cho putaway suggestion.")
public class CategoryController {

    private final CategoryService categoryService;

    // ─────────────────────────────────────────────────────────────
    // POST /v1/categories — Tạo category mới
    // ─────────────────────────────────────────────────────────────
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Tạo category mới",
            description = "Tạo category với categoryCode và categoryName. categoryCode phải unique. Chỉ ADMIN/MANAGER.")
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
            throw e;
        }
    }

    /**
     * Update an existing category
     * PUT /v1/categories/{categoryId}
     */
    @PutMapping("/{categoryId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Cập nhật category",
            description = "Cập nhật categoryName, description. Chỉ ADMIN/MANAGER.")
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
     * GET /v1/categories/{categoryId}
     */
    @GetMapping("/{categoryId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Chi tiết category",
            description = "Lấy thông tin chi tiết 1 category theo ID.")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategoryById(
            @PathVariable Long categoryId) {
        ApiResponse<CategoryResponse> response = categoryService.getCategoryById(categoryId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get all categories
     * GET /v1/categories
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sách categories", description = "Lấy tất cả categories (bao gồm active và inactive).\n\n"
            + "**Data yêu cầu:** \n"
            + "- `Query.page` (Tùy chọn): Trang kết quả, mặc định 0.\n"
            + "- `Query.size` (Tùy chọn): Kích thước trang, mặc định 10.")
    public ResponseEntity<ApiResponse<PageResponse<CategoryResponse>>> getAllCategories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        ApiResponse<PageResponse<CategoryResponse>> response = categoryService.getAllCategories(page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * UC: Deactivate category
     * PATCH /v1/categories/{categoryId}/deactivate
     */
    @PatchMapping("/{categoryId}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Vô hiệu hóa category",
            description = "Vô hiệu hóa category. SKU thuộc category này sẽ không được gợi ý putaway nữa.")
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
     * GET /v1/categories/tree?warehouseId=1
     * Shows tree with zone mapping info (convention: Z- + categoryCode)
     */
    @GetMapping("/tree")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cây category-zone",
            description = "Hiển thị cây category kèm thông tin zone mapping (convention: Z-{categoryCode}). ")
    public ResponseEntity<ApiResponse<List<CategoryTreeResponse>>> getCategoryTree() {
        Long warehouseId = getCurrentWarehouseId();
        ApiResponse<List<CategoryTreeResponse>> response = categoryService.getCategoryTree(warehouseId);
        return ResponseEntity.ok(response);
    }

    /**
     * UC: Map Category to Zone (convention-based)
     * POST /v1/categories/{categoryId}/map-to-zone
     * Convention: zone_code = "Z-" + category_code
     */
    @PostMapping("/{categoryId}/map-to-zone")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Map category vào zone",
            description = "Mapping category → zone theo convention: zoneCode = 'Z-' + categoryCode. "
                    )
    public ResponseEntity<ApiResponse<MapCategoryToZoneResponse>> mapCategoryToZone(
            @PathVariable Long categoryId,
            HttpServletRequest httpRequest) {
        Long mappedBy = getCurrentUserId();
        Long warehouseId = getCurrentWarehouseId();
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        ApiResponse<MapCategoryToZoneResponse> response = categoryService.mapCategoryToZone(
                categoryId, warehouseId, mappedBy, ipAddress, userAgent);
        return ResponseEntity.ok(response);
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    /**
     * Lấy userId từ JWT token (SecurityContext).
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
            if (userIdObj instanceof Long)    return (Long) userIdObj;
            if (userIdObj instanceof Integer) return ((Integer) userIdObj).longValue();
            if (userIdObj != null)            return Long.parseLong(userIdObj.toString());
        }
        throw new RuntimeException(MessageConstants.USER_ID_NOT_FOUND);
    }

    /**
     * Lấy warehouseId đầu tiên từ JWT token (SecurityContext).
     */
    private Long getCurrentWarehouseId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new RuntimeException(MessageConstants.NOT_AUTHENTICATED);
        }
        Object details = authentication.getDetails();
        if (details instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) details;
            Object raw = map.get("warehouseIds");
            if (raw instanceof List<?> list && !list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof Long l)    return l;
                if (first instanceof Integer i) return i.longValue();
                if (first instanceof Number n)  return n.longValue();
                if (first != null)              return Long.parseLong(first.toString());
            }
        }
        throw new RuntimeException(
                "Warehouse ID not found in token. Ensure your account is assigned to a warehouse.");
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