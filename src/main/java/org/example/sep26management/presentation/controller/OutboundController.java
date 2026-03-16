package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * SCRUM-505 POST   /v1/outbound
 * SCRUM-506 PUT    /v1/outbound/sales-orders/{soId}
 *           PUT    /v1/outbound/transfers/{transferId}
 * SCRUM-507 PATCH  /v1/outbound/sales-orders/{soId}/submit
 *           PATCH  /v1/outbound/transfers/{transferId}/submit
 * SCRUM-508 PATCH  /v1/outbound/sales-orders/{soId}/approve
 *           PATCH  /v1/outbound/sales-orders/{soId}/reject
 * SCRUM-509 GET    /v1/outbound
 *           GET    /v1/outbound/summary
 * SCRUM-510 POST   /v1/outbound/allocate
 * SCRUM-511 POST   /v1/outbound/pick-list
 *           GET    /v1/outbound/pick-list/{taskId}
 * NEW       POST   /v1/outbound/pick-list/{taskId}/start-qc
 *           POST   /v1/outbound/qc-scan
 *           GET    /v1/outbound/pick-list/{taskId}/qc-summary
 *           GET    /v1/outbound/sales-orders/{soId}/dispatch-note
 *           POST   /v1/outbound/sales-orders/{soId}/dispatch
 */
@RestController
@RequestMapping("/v1/outbound")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Outbound Management", description = "Quản lý quy trình xuất kho (Outbound). "
        + "Lifecycle: DRAFT → SUBMITTED → APPROVED → ALLOCATED → PICKING → QC_SCAN → DISPATCHED")
public class OutboundController {

    private final OutboundService outboundService;
    private final OutboundListService outboundListService;
    private final AllocateStockService allocateStockService;
    private final PickListService pickListService;
    private final OutboundQcService outboundQcService;

    // ─────────────────────────────────────────────────────────────
    // SCRUM-505: Create
    // createOutbound(request, createdBy, ip, ua) — warehouseId injected vào request
    // ─────────────────────────────────────────────────────────────
    @PostMapping
    @PreAuthorize("hasRole('KEEPER')")
    @Operation(summary = "Tạo lệnh xuất kho (DRAFT)", description = "Tạo một lệnh xuất kho mới (Sales Order hoặc Internal Transfer).\n\n"
            + "- `Body.orderType`: `SALES_ORDER` hoặc `INTERNAL_TRANSFER`.\n"
            + "- `Body.customerCode`: Bắt buộc nếu SALES_ORDER. Lấy từ `GET /v1/customers`.\n"
            + "- `Body.destinationWarehouseCode`: Bắt buộc nếu INTERNAL_TRANSFER.\n"
            + "- `warehouseId` lấy tự động từ JWT — FE không cần truyền.")
    public ResponseEntity<ApiResponse<OutboundResponse>> createOutbound(
            @Valid @RequestBody CreateOutboundRequest request,
            HttpServletRequest http) {
        // Inject warehouseId từ JWT vào request — FE không cần truyền
        request.setWarehouseId(getWarehouseId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(outboundService.createOutbound(request, getUserId(), getIp(http), ua(http)));
    }

    // ─────────────────────────────────────────────────────────────
    // SCRUM-506: Update
    // updateOutbound(OutboundType, id, request, userId, ip, ua)
    // ─────────────────────────────────────────────────────────────
    @PutMapping("/sales-orders/{soId}")
    @PreAuthorize("hasRole('KEEPER')")
    @Operation(summary = "Cập nhật Sales Order (DRAFT)", description = "Cập nhật lệnh xuất kho loại Sales Order đang ở trạng thái DRAFT.\n\n"
            + "- `@PathVariable soId`: Lấy từ `documentId` của `GET /v1/outbound`.")
    public ResponseEntity<ApiResponse<OutboundResponse>> updateSalesOrder(
            @PathVariable Long soId,
            @Valid @RequestBody UpdateOutboundRequest request,
            HttpServletRequest http) {
        return ResponseEntity.ok(outboundService.updateOutbound(
                OutboundType.SALES_ORDER, soId, request, getUserId(), getIp(http), ua(http)));
    }

    @PutMapping("/transfers/{transferId}")
    @PreAuthorize("hasRole('KEEPER')")
    @Operation(summary = "Cập nhật Internal Transfer (DRAFT)")
    public ResponseEntity<ApiResponse<OutboundResponse>> updateTransfer(
            @PathVariable Long transferId,
            @Valid @RequestBody UpdateOutboundRequest request,
            HttpServletRequest http) {
        return ResponseEntity.ok(outboundService.updateOutbound(
                OutboundType.INTERNAL_TRANSFER, transferId, request, getUserId(), getIp(http), ua(http)));
    }

    // ─────────────────────────────────────────────────────────────
    // SCRUM-507: Submit
    // submitOutbound(OutboundType, id, request, userId, ip, ua)
    // ─────────────────────────────────────────────────────────────
    @PatchMapping("/sales-orders/{soId}/submit")
    @PreAuthorize("hasRole('KEEPER')")
    @Operation(summary = "Submit Sales Order → SUBMITTED")
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
    @Operation(summary = "Submit Internal Transfer → SUBMITTED")
    public ResponseEntity<ApiResponse<OutboundResponse>> submitTransfer(
            @PathVariable Long transferId,
            @RequestBody(required = false) SubmitOutboundRequest request,
            HttpServletRequest http) {
        if (request == null) request = new SubmitOutboundRequest();
        return ResponseEntity.ok(outboundService.submitOutbound(
                OutboundType.INTERNAL_TRANSFER, transferId, request, getUserId(), getIp(http), ua(http)));
    }

    // ─────────────────────────────────────────────────────────────
    // SCRUM-508: Approve / Reject
    // approveSalesOrder(soId, ApproveOutboundRequest, managerId, ip, ua)
    // rejectOutbound(soId, reason, managerId, ip, ua)
    // ─────────────────────────────────────────────────────────────
    @PatchMapping("/sales-orders/{soId}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Duyệt lệnh xuất kho (MANAGER)", description = "Manager duyệt Sales Order. Trạng thái: PENDING_APPROVAL → APPROVED.\n\n"
            + "- `@PathVariable soId`: Lấy từ `documentId` của `GET /v1/outbound`.\n"
            + "- `Body.approved`: `true` = Duyệt.\n"
            + "- `Body.note` (tuỳ chọn): Ghi chú khi duyệt.")
    public ResponseEntity<ApiResponse<OutboundResponse>> approveSalesOrder(
            @PathVariable Long soId,
            @Valid @RequestBody ApproveOutboundRequest request,
            HttpServletRequest http) {
        return ResponseEntity.ok(outboundService.approveSalesOrder(
                soId, request, getUserId(), getIp(http), ua(http)));
    }

    @PatchMapping("/sales-orders/{soId}/reject")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Từ chối lệnh xuất kho (MANAGER)", description = "Manager từ chối Sales Order. Trạng thái: PENDING_APPROVAL → REJECTED.\n\n"
            + "- `Body.reason`: Lý do từ chối (tối thiểu 20 ký tự).")
    public ResponseEntity<ApiResponse<OutboundResponse>> rejectSalesOrder(
            @PathVariable Long soId,
            @RequestBody RejectRequest request,
            HttpServletRequest http) {
        return ResponseEntity.ok(outboundService.rejectOutbound(
                soId, request.getReason(), getUserId(), getIp(http), ua(http)));
    }

    // ─────────────────────────────────────────────────────────────
    // SCRUM-509: List + Summary
    // listOutbound(warehouseId, status, orderType, keyword, createdBy,
    //              fromDate, toDate, currentUserId, currentUserRole, page, size)
    // getSummary(warehouseId)
    // ─────────────────────────────────────────────────────────────
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sách lệnh xuất kho")
    public ResponseEntity<ApiResponse<PageResponse<OutboundListResponse>>> listOutbound(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) OutboundType orderType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long createdBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(outboundListService.listOutbound(
                getWarehouseId(), status, orderType, keyword,
                createdBy, fromDate, toDate,
                getUserId(), getCurrentRole(), page, size));
    }

    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Thống kê lệnh xuất kho")
    public ResponseEntity<ApiResponse<OutboundSummaryResponse>> getSummary() {
        return ResponseEntity.ok(outboundListService.getSummary(getWarehouseId()));
    }

    // ─────────────────────────────────────────────────────────────
    // SCRUM-510: Allocate
    // allocateStock(AllocateStockRequest, userId, ip, ua)
    // ─────────────────────────────────────────────────────────────
    @PostMapping("/allocate")
    @PreAuthorize("hasAnyRole('KEEPER','MANAGER')")
    @Operation(summary = "Phân bổ / Khóa tồn kho (FEFO)", description = "Khóa số lượng tồn theo FEFO trước khi đi lấy hàng.\n\n"
            + "- `Body.documentId`: Outbound ID đã APPROVED.\n"
            + "- `Body.orderType`: `SALES_ORDER` hoặc `INTERNAL_TRANSFER`.")
    public ResponseEntity<ApiResponse<AllocateStockResponse>> allocateStock(
            @Valid @RequestBody AllocateStockRequest request,
            HttpServletRequest http) {
        return ResponseEntity.ok(allocateStockService.allocateStock(
                request, getUserId(), getIp(http), ua(http)));
    }

    // ─────────────────────────────────────────────────────────────
    // SCRUM-511: Pick List
    // generatePickList(GeneratePickListRequest, userId, ip, ua)
    // ─────────────────────────────────────────────────────────────
    @PostMapping("/pick-list")
    @PreAuthorize("hasAnyRole('KEEPER','MANAGER')")
    @Operation(summary = "Tạo Pick List", description = "Tạo lộ trình lấy hàng từ tồn kho đã Allocate.\n\n"
            + "- `Body.documentId`: Outbound ID đã Allocate.\n"
            + "- `Body.orderType`: `SALES_ORDER` hoặc `INTERNAL_TRANSFER`.\n"
            + "- `Body.assignedTo` (tuỳ chọn): ID nhân viên được phân công.")
    public ResponseEntity<ApiResponse<PickListResponse>> generatePickList(
            @Valid @RequestBody GeneratePickListRequest request,
            HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pickListService.generatePickList(
                        request, getUserId(), getIp(http), ua(http)));
    }

    @GetMapping("/pick-list/{taskId}")
    @PreAuthorize("hasAnyRole('KEEPER','MANAGER')")
    @Operation(summary = "Chi tiết Pick List")
    public ResponseEntity<ApiResponse<PickListResponse>> getPickList(@PathVariable Long taskId) {
        return ResponseEntity.ok(pickListService.getPickList(taskId));
    }

    @PatchMapping("/pick-list/{taskId}/confirm-picked")
    @PreAuthorize("hasRole('KEEPER')")
    @Operation(summary = "Xác nhận đã lấy hàng (KEEPER)",
            description = "Keeper xác nhận đã lấy đủ hàng theo Pick List.\n\n"
                    + "- Chuyển trạng thái picking task: `OPEN`/`IN_PROGRESS` → `PICKED`.\n"
                    + "- Bước bắt buộc trước khi bắt đầu QC (`start-qc` yêu cầu task ở trạng thái `PICKED`).")
    public ResponseEntity<ApiResponse<PickListResponse>> confirmPicked(
            @PathVariable Long taskId,
            HttpServletRequest http) {
        return ResponseEntity.ok(pickListService.confirmPicked(taskId, getUserId(), getIp(http), ua(http)));
    }

    // ═════════════════════════════════════════════════════════════
    // NEW — QC Scan + Dispatch
    // ═════════════════════════════════════════════════════════════

    @PostMapping("/pick-list/{taskId}/start-qc")
    @PreAuthorize("hasRole('KEEPER')")
    @Operation(summary = "Bắt đầu phiên QC (KEEPER)",
            description = "Chuyển picking task `PICKED` → `QC_IN_PROGRESS`, Sales Order `PICKING` → `QC_SCAN`.")
    public ResponseEntity<ApiResponse<Void>> startQcSession(@PathVariable Long taskId) {
        return ResponseEntity.ok(outboundQcService.startQcSession(taskId, getUserId()));
    }

    @PostMapping("/qc-scan")
    @PreAuthorize("hasRole('KEEPER')")
    @Operation(summary = "QC Scan từng item (KEEPER)",
            description = "Ghi nhận kết quả QC: `PASS` / `FAIL` / `HOLD`.\n\n"
                    + "- **FAIL**: bắt buộc có `reason`.\n"
                    + "- Task phải ở trạng thái `QC_IN_PROGRESS`.")
    public ResponseEntity<ApiResponse<Void>> qcScanItem(@Valid @RequestBody QcScanRequest request) {
        return ResponseEntity.ok(outboundQcService.scanItem(request, getUserId()));
    }

    @GetMapping("/pick-list/{taskId}/qc-summary")
    @PreAuthorize("hasAnyRole('KEEPER','MANAGER')")
    @Operation(summary = "Tóm tắt kết quả QC",
            description = "Trả về: `totalItems`, `passCount`, `failCount`, `holdCount`, `pendingCount`.")
    public ResponseEntity<ApiResponse<QcSummaryResponse>> getQcSummary(@PathVariable Long taskId) {
        return ResponseEntity.ok(outboundQcService.getQcSummary(taskId));
    }

    @GetMapping("/sales-orders/{soId}/dispatch-note")
    @PreAuthorize("hasAnyRole('KEEPER','MANAGER')")
    @Operation(summary = "Phiếu xuất kho (Dispatch Note)",
            description = "Sinh Dispatch Note từ item PASS. Không lưu DB.\n\n"
                    + "- 400 nếu còn item chưa QC scan (BR-QC-03).\n"
                    + "- 400 nếu còn incident OPEN (BR-QC-04).")
    public ResponseEntity<ApiResponse<DispatchNoteResponse>> getDispatchNote(@PathVariable Long soId) {
        return ResponseEntity.ok(outboundQcService.generateDispatchNote(soId));
    }

    @PostMapping("/sales-orders/{soId}/dispatch")
    @PreAuthorize("hasRole('KEEPER')")
    @Operation(summary = "Xác nhận Xuất Kho (KEEPER)",
            description = "Trừ tồn kho, tạo DISPATCH transaction, đóng reservation, "
                    + "cập nhật task → COMPLETED, SO → DISPATCHED.\n\n"
                    + "- 400 nếu còn item chưa QC scan (BR-DISPATCH-02).\n"
                    + "- 400 nếu còn incident OPEN (BR-DISPATCH-03).")
    public ResponseEntity<ApiResponse<Void>> confirmDispatch(@PathVariable Long soId) {
        return ResponseEntity.ok(outboundQcService.confirmDispatch(soId, getUserId()));
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers — giữ nguyên theo file gốc
    // ─────────────────────────────────────────────────────────────
    private Long getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null)
            throw new RuntimeException(MessageConstants.NOT_AUTHENTICATED);
        Object details = auth.getDetails();
        if (details instanceof Map<?, ?> map) {
            Object uid = map.get("userId");
            if (uid instanceof Long l)      return l;
            if (uid instanceof Integer i)   return i.longValue();
            if (uid != null)                return Long.parseLong(uid.toString());
        }
        throw new RuntimeException(MessageConstants.USER_ID_NOT_FOUND);
    }

    private Long getWarehouseId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null)
            throw new RuntimeException(MessageConstants.NOT_AUTHENTICATED);
        Object details = auth.getDetails();
        if (details instanceof Map<?, ?> map) {
            Object raw = map.get("warehouseIds");
            if (raw instanceof List<?> list && !list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof Long l)    return l;
                if (first instanceof Integer i) return i.longValue();
                if (first instanceof Number n)  return n.longValue();
                if (first != null)              return Long.parseLong(first.toString());
            }
        }
        throw new RuntimeException("Warehouse ID not found in token. Ensure your account is assigned to a warehouse.");
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

    private String ua(HttpServletRequest req) {
        return req.getHeader("User-Agent");
    }
}