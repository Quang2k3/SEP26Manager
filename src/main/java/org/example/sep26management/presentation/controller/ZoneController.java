package org.example.sep26management.presentation.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.request.CreateZoneRequest;
import org.example.sep26management.application.dto.request.UpdateZoneRequest;
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

@RestController
@RequestMapping("/v1/zones")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('MANAGER')") // ✅ MANAGER ONLY
public class ZoneController {

    private final ZoneService zoneService;

    /**
     * Create Zone - MANAGER only
     * POST /api/v1/zones
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ZoneResponse>> createZone(
            @Valid @RequestBody CreateZoneRequest request,
            HttpServletRequest httpRequest) {
        Long currentUserId = getCurrentUserId();
        log.info("Creating zone: {} by user: {}", request.getZoneCode(), currentUserId);

        ApiResponse<ZoneResponse> response = zoneService.createZone(request, currentUserId);

        if (Boolean.FALSE.equals(response.getSuccess())) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get all zones
     * GET /api/v1/zones
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ZoneResponse>>> getAllZones() {
        log.info("Fetching all zones");

        ApiResponse<List<ZoneResponse>> response = zoneService.getAllZones();
        return ResponseEntity.ok(response);
    }

    /**
     * Get zone by ID
     * GET /api/v1/zones/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ZoneResponse>> getZoneById(@PathVariable Long id) {
        log.info("Fetching zone with ID: {}", id);

        ApiResponse<ZoneResponse> response = zoneService.getZoneById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Update zone - MANAGER only
     * PUT /api/v1/zones/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ZoneResponse>> updateZone(
            @PathVariable Long id,
            @Valid @RequestBody UpdateZoneRequest request,
            HttpServletRequest httpRequest) {
        Long currentUserId = getCurrentUserId();
        log.info("Updating zone ID: {} by user: {}", id, currentUserId);

        ApiResponse<ZoneResponse> response = zoneService.updateZone(id, request, currentUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete zone (soft delete) - MANAGER only
     * DELETE /api/v1/zones/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteZone(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long currentUserId = getCurrentUserId();
        log.info("Deleting zone ID: {} by user: {}", id, currentUserId);

        ApiResponse<String> response = zoneService.deleteZone(id, currentUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get zones by warehouse code
     * GET /api/v1/zones/warehouse/{warehouseCode}
     */
    @GetMapping("/warehouse/{warehouseCode}")
    public ResponseEntity<ApiResponse<List<ZoneResponse>>> getZonesByWarehouse(
            @PathVariable String warehouseCode) {
        log.info("Fetching zones for warehouse: {}", warehouseCode);

        ApiResponse<List<ZoneResponse>> response = zoneService.getZonesByWarehouse(warehouseCode);
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
}
