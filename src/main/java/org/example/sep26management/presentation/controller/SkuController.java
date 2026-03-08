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
import java.util.List;
import java.util.Map;

/**
 * SkuController — SKU master data APIs
 *
 * UC-B04 View SKU List      (All roles)
 * UC-B05 View SKU Detail    (All roles)
 * UC-B06 Search SKU         (All roles — used by barcode scan flow)
 * UC-B07 Configure Threshold (MANAGER)
 * UC-B08 Import from Excel  (MANAGER)
 *
 * warehouseId không cần truyền vào param — lấy tự động từ JWT token
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
     * GET /v1/skus/{skuId}
     */
    @GetMapping("/{skuId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Chi tiết SKU", description = "Xem thông tin chi tiết SKU: skuCode, skuName, barcode, weight, volume, category, image,...")
    public ResponseEntity<ApiResponse<SkuResponse>> getSkuDetail(
            @PathVariable Long skuId) {
        log.info("GET /v1/skus/{} — view SKU detail", skuId);
        return ResponseEntity.ok(skuService.getSkuDetail(skuId));
    }

    // ─────────────────────────────────────────────────────────────
    // UC-B06: Search SKU
    // BR-SKU-06: partial, case-insensitive, skuCode + skuName
    // GET /v1/skus/search?keyword=abc&page=0&size=20
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
    // PUT /v1/skus/{skuId}/threshold
    // ─────────────────────────────────────────────────────────────

    @PutMapping("/{skuId}/threshold")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Cấu hình ngưỡng tồn kho (Manager)",
            description = "Set minQty và maxQty cho SKU. "
    )
    public ResponseEntity<ApiResponse<SkuThresholdResponse>> configureThreshold(
            @PathVariable Long skuId,
            @Valid @RequestBody ConfigureSkuThresholdRequest request,
            HttpServletRequest httpRequest) {

        Long userId      = getCurrentUserId();
        Long warehouseId = getCurrentWarehouseId();   // ← thêm dòng này

        return ResponseEntity.ok(skuService.configureThreshold(
                skuId,
                request,
                warehouseId,                          // ← truyền vào đây
                userId,
                getClientIpAddress(httpRequest),
                httpRequest.getHeader("User-Agent")));
    }

    // ─────────────────────────────────────────────────────────────
    // UC-B07: Get SKU Threshold
    // GET /api/v1/skus/{skuId}/threshold
    // warehouseId lấy từ JWT token
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/{skuId}/threshold")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xem ngưỡng tồn kho", description = "Lấy cấu hình ngưỡng tồn kho (minQty, maxQty) của SKU. warehouseId lấy tự động từ JWT token.")
    public ResponseEntity<ApiResponse<SkuThresholdResponse>> getThreshold(
            Authentication authentication,
            @PathVariable Long skuId) {

        Long warehouseId = extractWarehouseId(authentication);
        return ResponseEntity.ok(skuService.getThreshold(skuId, warehouseId));
    }

    // ─────────────────────────────────────────────────────────────
    // UC-B08: Import SKU from Excel
    // POST /v1/skus/import (multipart/form-data)
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
     * PATCH /v1/skus/{skuId}/assign-category
     */
    @PatchMapping("/{skuId}/assign-category")
    @PreAuthorize("hasAnyRole('MANAGER','KEEPER')")
    @Operation(summary = "Gán category cho SKU",
            description = "Gán/thay đổi category của SKU.\n\n"
                    + "**Data yêu cầu:**\n"
                    + "- `@PathVariable skuId`: ID của SKU — lấy từ kết quả tìm kiếm SKU (`GET /v1/skus/search`).\n"
                    + "- `Body.categoryCode`: Mã category — **LẤY TỪ** API `GET /v1/categories` (field `categoryCode`). "
                    + "FE hiển thị `categoryName` cho người dùng chọn, sau đó gửi `categoryCode` lên đây. BE tự resolve ra `categoryId` nội bộ.\n\n"
                    + "👉 Việc thay đổi category sẽ ảnh hưởng đến zone gợi ý khi putaway.")
    public ResponseEntity<ApiResponse<SkuResponse>> assignCategoryToSku(
            @PathVariable Long skuId,
            @Valid @RequestBody AssignCategoryToSkuRequest request,
            HttpServletRequest httpRequest) {

        Long updatedBy = getCurrentUserId();
        return ResponseEntity.ok(skuService.assignCategoryToSku(
                skuId, request, updatedBy,
                getClientIpAddress(httpRequest),
                httpRequest.getHeader("User-Agent")));
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private Long extractWarehouseId(Authentication auth) {
        if (auth != null && auth.getDetails() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) auth.getDetails();
            Object raw = map.get("warehouseIds");
            if (raw instanceof List<?> list && !list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof Long) return (Long) first;
                if (first instanceof Integer) return ((Integer) first).longValue();
                if (first instanceof Number) return ((Number) first).longValue();
            }
        }
        throw new RuntimeException("Cannot extract warehouseId from token");
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
            if (uid instanceof Long) return (Long) uid;
            if (uid instanceof Integer) return ((Integer) uid).longValue();
            if (uid != null) return Long.parseLong(uid.toString());
        }
        throw new RuntimeException(MessageConstants.USER_ID_NOT_FOUND);
    }

    private Long getCurrentWarehouseId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) throw new RuntimeException(MessageConstants.NOT_AUTHENTICATED);
        Object details = auth.getDetails();
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
     * GET /v1/skus/barcode/{barcode}
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
     * GET /v1/skus/code/{skuCode}
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