package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.application.dto.request.AssignCategoryToSkuRequest;
import org.example.sep26management.application.dto.request.ConfigureSkuThresholdRequest;
import org.example.sep26management.application.dto.request.SearchSkuRequest;
import org.example.sep26management.application.dto.response.*;
import org.example.sep26management.application.service.SkuService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * SkuController — SKU master data APIs
 * <p>
 * UC-B04 View SKU List (All roles)
 * UC-B05 View SKU Detail (All roles)
 * UC-B06 Search SKU by barcode (All roles — used by barcode scan flow)
 */
@RestController
@RequestMapping("/v1/skus")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "SKU Management", description = "Quản lý mặt hàng (SKU): xem, tìm kiếm, import Excel, cấu hình ngưỡng tồn kho, "
        + "tra cứu theo barcode/skuCode, gán category cho putaway suggestion.")
public class SkuController {

    private final SkuService skuService;

    /**
     * UC-268: View SKU Detail
     * GET /api/v1/skus/{skuId}
     */
    @GetMapping("/{skuId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Chi tiết SKU", description = "Xem thông tin chi tiết SKU: skuCode, skuName, barcode, weight, volume, category, image,...")
    public ResponseEntity<ApiResponse<SkuResponse>> getSkuDetail(
            @PathVariable Long skuId) {
        log.info("GET /v1/skus/{} — view SKU detail", skuId);
        ApiResponse<SkuResponse> response = skuService.getSkuDetail(skuId);
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────────────────────
    // UC-B06: Search SKU
    // BR-SKU-06: partial, case-insensitive, skuCode + skuName
    // GET /api/v1/skus/search?keyword=abc&page=0&size=20
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Tìm kiếm SKU", description = "Tìm kiếm SKU theo keyword (partial, case-insensitive trên skuCode + skuName). Phân trang.")
    public ResponseEntity<ApiResponse<PageResponse<SkuResponse>>> searchSku(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        SearchSkuRequest request = SearchSkuRequest.builder()
                .keyword(keyword)
                .page(page)
                .size(size)
                .build();

        return ResponseEntity.ok(skuService.searchSku(request));
    }

    // ─────────────────────────────────────────────────────────────
    // UC-B07: Configure SKU Threshold
    // PUT /api/v1/skus/{skuId}/threshold
    // ─────────────────────────────────────────────────────────────

    @PutMapping("/{skuId}/threshold")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Cấu hình ngưỡng tồn kho (Manager)", description = "Set minQty và maxQty cho SKU trong warehouse. Dùng để cảnh báo khi tồn kho vượt ngưỡng.")
    public ResponseEntity<ApiResponse<SkuThresholdResponse>> configureThreshold(
            @PathVariable Long skuId,
            @Valid @RequestBody ConfigureSkuThresholdRequest request,
            HttpServletRequest httpRequest) {

        Long userId = getCurrentUserId();
        return ResponseEntity.ok(skuService.configureThreshold(
                skuId, request, userId,
                getClientIpAddress(httpRequest),
                httpRequest.getHeader("User-Agent")));
    }

    @GetMapping("/{skuId}/threshold")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xem ngưỡng tồn kho", description = "Lấy cấu hình ngưỡng tồn kho (minQty, maxQty) của SKU trong warehouse.")
    public ResponseEntity<ApiResponse<SkuThresholdResponse>> getThreshold(
            @PathVariable Long skuId,
            @RequestParam Long warehouseId) {
        return ResponseEntity.ok(skuService.getThreshold(skuId, warehouseId));
    }

    // ─────────────────────────────────────────────────────────────
    // UC-B08: Import SKU from Excel
    // POST /api/v1/skus/import (multipart/form-data)
    // BR-IMP-01: max 5MB, max 1000 rows, .xlsx only
    // ─────────────────────────────────────────────────────────────

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Import SKU từ Excel (Manager)", description = "Upload file .xlsx để import hàng loạt SKU. Max 5MB, max 1000 dòng. "
            + "File cần các cột: skuCode, skuName, barcode, unitOfMeasure,...")
    public ResponseEntity<ApiResponse<ImportSkuResultResponse>> importSku(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest httpRequest) throws IOException {

        Long userId = getCurrentUserId();
        return ResponseEntity.ok(skuService.importSkuFromExcel(
                file, userId,
                getClientIpAddress(httpRequest),
                httpRequest.getHeader("User-Agent")));
    }

    /**
     * Assign category to SKU
     * PATCH /api/v1/skus/{skuId}/assign-category
     */
    @PatchMapping("/{skuId}/assign-category")
    @Operation(summary = "Gán category cho SKU", description = "Gán/thay đổi category của SKU. Việc thay đổi category sẽ ảnh hưởng đến zone gợi ý khi putaway.")
    public ResponseEntity<ApiResponse<SkuResponse>> assignCategoryToSku(
            @PathVariable Long skuId,
            @Valid @RequestBody AssignCategoryToSkuRequest request,
            HttpServletRequest httpRequest) {

        Long updatedBy = getCurrentUserId();
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        ApiResponse<SkuResponse> response = skuService.assignCategoryToSku(
                skuId, request, updatedBy, ipAddress, userAgent);

        return ResponseEntity.ok(response);
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null)
            throw new RuntimeException(MessageConstants.NOT_AUTHENTICATED);
        Object details = auth.getDetails();
        if (details instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) details;
            Object uid = map.get("userId");
            if (uid instanceof Long)
                return (Long) uid;
            else if (uid instanceof Integer)
                return ((Integer) uid).longValue();
            else if (uid != null)
                return Long.parseLong(uid.toString());
        }
        throw new RuntimeException(MessageConstants.USER_ID_NOT_FOUND);
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty())
            return xff.split(",")[0].trim();
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isEmpty())
            return xri;
        return request.getRemoteAddr();
    }

    /**
     * UC-B06 — Lookup SKU by barcode.
     * Called by the scan-event flow when an iPhone scans a product.
     * Also useful for quick debug/test via Swagger.
     * <p>
     * GET /api/v1/skus/barcode/{barcode}
     *
     * @param barcode barcode value scanned from the product
     * @return 200 with SkuResponse if found, 404 if no active SKU matches
     */
    @GetMapping("/barcode/{barcode}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Tra cứu SKU theo barcode", description = "Tìm SKU active theo barcode. Dùng cho quét barcode từ iPhone hoặc test nhanh trên Swagger.")
    public ResponseEntity<ApiResponse<SkuResponse>> findByBarcode(
            @PathVariable String barcode) {

        log.info("GET /v1/skus/barcode/{} — barcode lookup", barcode);

        ApiResponse<SkuResponse> response = skuService.findByBarcode(barcode);

        if (Boolean.TRUE.equals(response.getSuccess())) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(404).body(response);
    }

    /**
     * Lookup SKU by SKU code.
     * <p>
     * GET /api/v1/skus/code/{skuCode}
     *
     * @param skuCode the SKU code (e.g. SKU001)
     * @return 200 with SkuResponse if found, 404 if not found
     */
    @GetMapping("/code/{skuCode}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Tra cứu SKU theo mã", description = "Tìm SKU theo skuCode (ví dụ: SKU001). Trả về 404 nếu không tìm thấy.")
    public ResponseEntity<ApiResponse<SkuResponse>> findBySkuCode(
            @PathVariable String skuCode) {

        log.info("GET /v1/skus/code/{} — skuCode lookup", skuCode);

        ApiResponse<SkuResponse> response = skuService.findBySkuCode(skuCode);

        if (Boolean.TRUE.equals(response.getSuccess())) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(404).body(response);
    }
}