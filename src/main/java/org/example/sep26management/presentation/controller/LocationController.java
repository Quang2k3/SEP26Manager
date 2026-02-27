package org.example.sep26management.presentation.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.application.dto.request.CreateLocationRequest;
import org.example.sep26management.application.dto.request.UpdateLocationRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.LocationResponse;
import org.example.sep26management.application.dto.response.PageResponse;
import org.example.sep26management.application.enums.LocationType;
import org.example.sep26management.application.service.LocationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * LocationController — Location master data APIs
 *
 * SCRUM-273  POST   /api/v1/locations               — Create Location    (MANAGER)
 * SCRUM-274  PUT    /api/v1/locations/{locationId}   — Update Location    (MANAGER)
 * SCRUM-275  PATCH  /api/v1/locations/{locationId}/deactivate — Deactivate (MANAGER)
 * SCRUM-276  GET    /api/v1/locations               — View Location List  (MANAGER)
 *            GET    /api/v1/locations/{locationId}   — View Location Detail
 */
@RestController
@RequestMapping("/v1/locations")
@RequiredArgsConstructor
@Slf4j
public class LocationController {

    private final LocationService locationService;

    // ─────────────────────────────────────────────────────────────
    // SCRUM-273: Create Location (UC-LOC-02)
    // ─────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<LocationResponse>> createLocation(
            @Valid @RequestBody CreateLocationRequest request,
            HttpServletRequest httpRequest) {

        Long userId = getCurrentUserId();
        ApiResponse<LocationResponse> response = locationService.createLocation(
                request, userId,
                getClientIp(httpRequest),
                httpRequest.getHeader("User-Agent"));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

// ─────────────────────────────────────────────────────────────
    // SCRUM-274: Update Location (UC-LOC-03)
    // ─────────────────────────────────────────────────────────────

    @PutMapping("/{locationId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<LocationResponse>> updateLocation(
            @PathVariable Long locationId,
            @Valid @RequestBody UpdateLocationRequest request,
            HttpServletRequest httpRequest) {

        Long userId = getCurrentUserId();
        return ResponseEntity.ok(locationService.updateLocation(
                locationId, request, userId,
                getClientIp(httpRequest),
                httpRequest.getHeader("User-Agent")));
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