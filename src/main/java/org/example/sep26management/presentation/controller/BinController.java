package org.example.sep26management.presentation.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.application.dto.response.*;
import org.example.sep26management.application.enums.OccupancyStatus;
import org.example.sep26management.application.service.BinService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * BinController — Bin occupancy and capacity APIs
 *
 * SCRUM-277  GET   /api/v1/bins/occupancy             — View Bin Occupancy (MANAGER + KEEPER)
 *            GET   /api/v1/bins/{locationId}/occupancy — View Single Bin Detail
 * SCRUM-278  GET   /api/v1/bins/search-empty          — Search Empty Bin  (MANAGER + KEEPER)
 * SCRUM-279  PATCH /api/v1/bins/{locationId}/capacity — Configure Bin Capacity (MANAGER only)
 */
@RestController
@RequestMapping("/v1/bins")
@RequiredArgsConstructor
@Slf4j
public class BinController {

    private final BinService binService;

    // ─────────────────────────────────────────────────────────────
    // SCRUM-277: View Bin Occupancy (UC-LOC-06)
    // GET /api/v1/bins/occupancy?warehouseId=1&zoneId=2&occupancyStatus=PARTIAL&page=0&size=20
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/occupancy")
    @PreAuthorize("hasAnyRole('MANAGER','KEEPER')")
    public ResponseEntity<ApiResponse<PageResponse<BinOccupancyResponse>>> viewBinOccupancy(
            @RequestParam Long warehouseId,
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) OccupancyStatus occupancyStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(binService.viewBinOccupancy(
                warehouseId, zoneId, occupancyStatus, page, size));
    }

    /**
     * UC-LOC-06 3c: View detailed inventory in a specific bin
     * GET /api/v1/bins/{locationId}/occupancy
     */
    @GetMapping("/{locationId}/occupancy")
    @PreAuthorize("hasAnyRole('MANAGER','KEEPER')")
    public ResponseEntity<ApiResponse<BinOccupancyResponse>> getBinDetail(
            @PathVariable Long locationId) {

        return ResponseEntity.ok(binService.getBinDetail(locationId));
    }



    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) throw new RuntimeException(MessageConstants.NOT_AUTHENTICATED);
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

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) return xff.split(",")[0].trim();
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isEmpty()) return xri;
        return request.getRemoteAddr();
    }
}