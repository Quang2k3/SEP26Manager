package org.example.sep26management.presentation.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.application.dto.request.*;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.OutboundResponse;
import org.example.sep26management.application.enums.OutboundType;
import org.example.sep26management.application.service.OutboundService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * OutboundController
 *
 * SCRUM-505  POST  /api/v1/outbound                           — Create  (KEEPER)
 * SCRUM-506  PUT   /api/v1/outbound/{type}/{id}               — Update  (KEEPER)
 * SCRUM-507  PATCH /api/v1/outbound/{type}/{id}/submit        — Submit  (KEEPER)
 * SCRUM-508  PATCH /api/v1/outbound/sales-orders/{soId}/approve — Approve (MANAGER)
 */
@RestController
@RequestMapping("/v1/outbound")
@RequiredArgsConstructor
@Slf4j
public class OutboundController {

    private final OutboundService outboundService;

    // ─────────────────────────────────────────────────────────────
    // SCRUM-505: Create Outbound Order
    // POST /api/v1/outbound
    // ─────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('KEEPER')")
    public ResponseEntity<ApiResponse<OutboundResponse>> createOutbound(
            @Valid @RequestBody CreateOutboundRequest request,
            HttpServletRequest http) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(outboundService.createOutbound(request, getCurrentUserId(),
                        getClientIp(http), http.getHeader("User-Agent")));
    }

    // ─────────────────────────────────────────────────────────────
    // SCRUM-506: Update Outbound Order
    // PUT /api/v1/outbound/sales-orders/{soId}
    // PUT /api/v1/outbound/transfers/{transferId}
    // ─────────────────────────────────────────────────────────────

    @PutMapping("/sales-orders/{soId}")
    @PreAuthorize("hasRole('KEEPER')")
    public ResponseEntity<ApiResponse<OutboundResponse>> updateSalesOrder(
            @PathVariable Long soId,
            @Valid @RequestBody UpdateOutboundRequest request,
            HttpServletRequest http) {

        return ResponseEntity.ok(outboundService.updateOutbound(
                OutboundType.SALES_ORDER, soId, request,
                getCurrentUserId(), getClientIp(http), http.getHeader("User-Agent")));
    }

    @PutMapping("/transfers/{transferId}")
    @PreAuthorize("hasRole('KEEPER')")
    public ResponseEntity<ApiResponse<OutboundResponse>> updateTransfer(
            @PathVariable Long transferId,
            @Valid @RequestBody UpdateOutboundRequest request,
            HttpServletRequest http) {

        return ResponseEntity.ok(outboundService.updateOutbound(
                OutboundType.INTERNAL_TRANSFER, transferId, request,
                getCurrentUserId(), getClientIp(http), http.getHeader("User-Agent")));
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