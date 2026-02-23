package org.example.sep26management.presentation.controller;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.application.dto.request.AssignCategoryToSkuRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.SkuResponse;
import org.example.sep26management.application.service.SkuService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

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
public class SkuController {

    private final SkuService skuService;
    /**
     * Assign category to SKU
     * PATCH /api/v1/skus/{skuId}/assign-category
     */
    @PatchMapping("/{skuId}/assign-category")
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
        if (auth == null) throw new RuntimeException(MessageConstants.NOT_AUTHENTICATED);
        Object details = auth.getDetails();
        if (details instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) details;
            Object uid = map.get("userId");
            if (uid instanceof Long) return (Long) uid;
            else if (uid instanceof Integer) return ((Integer) uid).longValue();
            else if (uid != null) return Long.parseLong(uid.toString());
        }
        throw new RuntimeException(MessageConstants.USER_ID_NOT_FOUND);
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) return xff.split(",")[0].trim();
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isEmpty()) return xri;
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