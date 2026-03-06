package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.application.dto.request.CreateZoneRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.ZoneResponse;
import org.example.sep26management.application.service.ZoneService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ZoneController — Zone master data APIs
 *
 * UC-LOC-01 POST /api/v1/zones — Create Zone (MANAGER only)
 * GET /api/v1/zones — List zones by warehouse
 */
@RestController
@RequestMapping("/v1/zones")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Zone Management", description = "Quản lý zone (khu vực) trong warehouse. "
        + "Mỗi zone có zoneCode theo convention Z-{categoryCode} để mapping với category cho putaway suggestion.")
public class ZoneController {

    private final ZoneService zoneService;

    // ─────────────────────────────────────────────────────────────
    // UC-LOC-01: Create Zone
    // BR-LOC-01: unique zone_code per warehouse
    // BR-LOC-02: zone_type ∈ {INBOUND, STORAGE, OUTBOUND, HOLD}
    // ─────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Tạo zone mới (Manager)",
            description = "Tạo zone trong warehouse. zoneCode phải unique trong warehouse. "
                    + "Convention: zoneCode = 'Z-' + categoryCode (ví dụ: Z-HC cho category HC). "
                    )
    public ResponseEntity<ApiResponse<ZoneResponse>> createZone(
            @Valid @RequestBody CreateZoneRequest request,
            HttpServletRequest httpRequest) {

        Long userId      = getCurrentUserId();
        Long warehouseId = getCurrentWarehouseId();

        ApiResponse<ZoneResponse> response = zoneService.createZone(
                request,
                warehouseId,
                userId,
                getClientIpAddress(httpRequest),
                httpRequest.getHeader("User-Agent"));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─────────────────────────────────────────────────────────────
    // List Zones by warehouse
    // GET /api/v1/zones?activeOnly=true
    // warehouseId lấy từ JWT token (không cần truyền vào param)
    // ─────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Danh sách zones", description = "Lấy danh sách zones trong warehouse. warehouseId lấy tự động từ JWT token. Có thể lọc chỉ zone active.")
    public ResponseEntity<ApiResponse<List<ZoneResponse>>> listZones(
            @RequestParam(defaultValue = "false") Boolean activeOnly,
            Authentication authentication) {

        Long warehouseId = extractWarehouseId(authentication);
        return ResponseEntity.ok(zoneService.listZones(warehouseId, activeOnly));
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private Long extractWarehouseId(Authentication auth) {
        if (auth != null && auth.getDetails() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) auth.getDetails();
            Object raw = map.get("warehouseIds");
            if (raw instanceof java.util.List<?> list && !list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof Long) return (Long) first;
                if (first instanceof Integer) return ((Integer) first).longValue();
                if (first instanceof Number) return ((Number) first).longValue();
            }
        }
        throw new RuntimeException("Cannot extract warehouseId from token");
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
            if (uid instanceof Integer)
                return ((Integer) uid).longValue();
            if (uid != null)
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
}