package org.example.sep26management.presentation.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.application.dto.request.*;
import org.example.sep26management.application.dto.response.*;
import org.example.sep26management.application.enums.OutboundType;
import org.example.sep26management.application.service.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * OutboundController — Full outbound lifecycle
 *
 * SCRUM-505  POST   /v1/outbound                              — Create       (KEEPER)
 * SCRUM-506  PUT    /v1/outbound/sales-orders/{id}            — Update SO    (KEEPER)
 *            PUT    /v1/outbound/transfers/{id}               — Update TF    (KEEPER)
 * SCRUM-507  PATCH  /v1/outbound/sales-orders/{id}/submit     — Submit SO    (KEEPER)
 *            PATCH  /v1/outbound/transfers/{id}/submit        — Submit TF    (KEEPER)
 * SCRUM-508  PATCH  /v1/outbound/sales-orders/{id}/approve    — Approve      (MANAGER)
 * SCRUM-509  GET    /v1/outbound                              — List         (ALL)
 *            GET    /v1/outbound/summary                      — Summary      (ALL)
 * SCRUM-510  POST   /v1/outbound/allocate                     — Allocate     (KEEPER/MANAGER)
 * SCRUM-511  POST   /v1/outbound/pick-list                    — Gen PickList (KEEPER/MANAGER)
 *            GET    /v1/outbound/pick-list/{taskId}           — Get PickList (KEEPER/MANAGER)
 */
@RestController
@RequestMapping("/v1/outbound")
@RequiredArgsConstructor
@Slf4j
public class OutboundController {

    private final OutboundService outboundService;
    private final OutboundListService outboundListService;
    private final AllocateStockService allocateStockService;
    private final PickListService pickListService;

    // ─────────────────────────────────────────────────────────────
    // SCRUM-505: Create
    // ─────────────────────────────────────────────────────────────
    @PostMapping
    @PreAuthorize("hasRole('KEEPER')")
    public ResponseEntity<ApiResponse<OutboundResponse>> createOutbound(
            @Valid @RequestBody CreateOutboundRequest request,
            HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(outboundService.createOutbound(request, getUserId(), getIp(http), ua(http)));
    }

    // ─────────────────────────────────────────────────────────────
    // SCRUM-506: Update
    // ─────────────────────────────────────────────────────────────
    @PutMapping("/sales-orders/{soId}")
    @PreAuthorize("hasRole('KEEPER')")
    public ResponseEntity<ApiResponse<OutboundResponse>> updateSalesOrder(
            @PathVariable Long soId,
            @Valid @RequestBody UpdateOutboundRequest request,
            HttpServletRequest http) {
        return ResponseEntity.ok(outboundService.updateOutbound(
                OutboundType.SALES_ORDER, soId, request, getUserId(), getIp(http), ua(http)));
    }

    @PutMapping("/transfers/{transferId}")
    @PreAuthorize("hasRole('KEEPER')")
    public ResponseEntity<ApiResponse<OutboundResponse>> updateTransfer(
            @PathVariable Long transferId,
            @Valid @RequestBody UpdateOutboundRequest request,
            HttpServletRequest http) {
        return ResponseEntity.ok(outboundService.updateOutbound(
                OutboundType.INTERNAL_TRANSFER, transferId, request, getUserId(), getIp(http), ua(http)));
    }

    // ─────────────────────────────────────────────────────────────
    // SCRUM-507: Submit
    // ─────────────────────────────────────────────────────────────
    @PatchMapping("/sales-orders/{soId}/submit")
    @PreAuthorize("hasRole('KEEPER')")
    public ResponseEntity<ApiResponse<OutboundResponse>> submitSalesOrder(
            @PathVariable Long soId,
            @RequestBody(required = false) SubmitOutboundRequest request,
            HttpServletRequest http) {
        if (request == null) request = new SubmitOutboundRequest();
        return ResponseEntity.ok(outboundService.submitOutbound(
                OutboundType.SALES_ORDER, soId, request, getUserId(), getIp(http), ua(http)));
    }

    @PatchMapping("/transfers/{transferId}/submit")
    @PreAuthorize("hasRole('KEEPER')")
    public ResponseEntity<ApiResponse<OutboundResponse>> submitTransfer(
            @PathVariable Long transferId,
            @RequestBody(required = false) SubmitOutboundRequest request,
            HttpServletRequest http) {
        if (request == null) request = new SubmitOutboundRequest();
        return ResponseEntity.ok(outboundService.submitOutbound(
                OutboundType.INTERNAL_TRANSFER, transferId, request, getUserId(), getIp(http), ua(http)));
    }

    // ─────────────────────────────────────────────────────────────
    // SCRUM-508: Approve
    // ─────────────────────────────────────────────────────────────
    @PatchMapping("/sales-orders/{soId}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<OutboundResponse>> approveOutbound(
            @PathVariable Long soId,
            @Valid @RequestBody ApproveOutboundRequest request,
            HttpServletRequest http) {
        return ResponseEntity.ok(outboundService.approveSalesOrder(
                soId, request, getUserId(), getIp(http), ua(http)));
    }

    // ─────────────────────────────────────────────────────────────
    // SCRUM-509: View Outbound List
    // GET /v1/outbound?warehouseId=1&status=DRAFT&orderType=SALES_ORDER&keyword=EXP&page=0&size=20
    // ─────────────────────────────────────────────────────────────
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<OutboundListResponse>>> listOutbound(
            @RequestParam Long warehouseId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) OutboundType orderType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long createdBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String role = getCurrentRole();
        return ResponseEntity.ok(outboundListService.listOutbound(
                warehouseId, status, orderType, keyword, createdBy,
                fromDate, toDate, getUserId(), role, page, size));
    }

    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OutboundSummaryResponse>> getSummary(
            @RequestParam Long warehouseId) {
        return ResponseEntity.ok(outboundListService.getSummary(warehouseId));
    }

    // ─────────────────────────────────────────────────────────────
    // SCRUM-510: Allocate / Reserve Stock
    // POST /v1/outbound/allocate
    // ─────────────────────────────────────────────────────────────
    @PostMapping("/allocate")
    @PreAuthorize("hasAnyRole('KEEPER','MANAGER')")
    public ResponseEntity<ApiResponse<AllocateStockResponse>> allocateStock(
            @Valid @RequestBody AllocateStockRequest request,
            HttpServletRequest http) {
        return ResponseEntity.ok(allocateStockService.allocateStock(
                request, getUserId(), getIp(http), ua(http)));
    }

    // ─────────────────────────────────────────────────────────────
    // SCRUM-511: Generate Pick List
    // POST /v1/outbound/pick-list
    // GET  /v1/outbound/pick-list/{taskId}
    // ─────────────────────────────────────────────────────────────
    @PostMapping("/pick-list")
    @PreAuthorize("hasAnyRole('KEEPER','MANAGER')")
    public ResponseEntity<ApiResponse<PickListResponse>> generatePickList(
            @Valid @RequestBody GeneratePickListRequest request,
            HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pickListService.generatePickList(request, getUserId(), getIp(http), ua(http)));
    }

    @GetMapping("/pick-list/{taskId}")
    @PreAuthorize("hasAnyRole('KEEPER','MANAGER')")
    public ResponseEntity<ApiResponse<PickListResponse>> getPickList(
            @PathVariable Long taskId) {
        return ResponseEntity.ok(pickListService.getPickList(taskId));
    }


    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────
    private Long getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) throw new RuntimeException(MessageConstants.NOT_AUTHENTICATED);
        Object details = auth.getDetails();
        if (details instanceof Map<?,?> map) {
            Object uid = map.get("userId");
            if (uid instanceof Long l) return l;
            if (uid instanceof Integer i) return i.longValue();
            if (uid != null) return Long.parseLong(uid.toString());
        }
        throw new RuntimeException(MessageConstants.USER_ID_NOT_FOUND);
    }

    private String getCurrentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "";
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .findFirst().orElse("");
    }

    private String getIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String xri = req.getHeader("X-Real-IP");
        return (xri != null && !xri.isBlank()) ? xri : req.getRemoteAddr();
    }

    private String ua(HttpServletRequest req) { return req.getHeader("User-Agent"); }
}