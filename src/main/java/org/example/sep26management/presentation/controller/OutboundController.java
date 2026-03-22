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
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.example.sep26management.application.service.ReceivingSessionService;
import org.example.sep26management.application.dto.request.ResolveOutboundDamageRequest;
import org.example.sep26management.application.dto.request.ResolveOutboundShortageRequest;
import org.example.sep26management.application.dto.response.IncidentResponse;
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
    private final ReceivingSessionService receivingSessionService;
    private final DispatchPdfService dispatchPdfService;
    private final SignedNoteService signedNoteService;
    private final Cloudinary cloudinary;
    private final PickSignedNoteService pickSignedNoteService;
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
    @GetMapping("/{documentId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Chi tiết lệnh xuất kho (kèm items)", description = "Trả về thông tin đầy đủ gồm danh sách SKU. Query param `orderType`: SALES_ORDER hoặc INTERNAL_TRANSFER.")
    public ResponseEntity<ApiResponse<OutboundResponse>> getOutboundDetail(
            @PathVariable Long documentId,
            @RequestParam(defaultValue = "SALES_ORDER") String orderType) {
        return ResponseEntity.ok(outboundService.getOutboundDetail(documentId, orderType));
    }

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
    // ─────────────────────────────────────────────────────────────
    // DELETE: Xóa lệnh xuất DRAFT
    // ─────────────────────────────────────────────────────────────
    @DeleteMapping("/sales-orders/{soId}")
    @PreAuthorize("hasRole('KEEPER')")
    @Operation(summary = "Xóa Sales Order (chỉ DRAFT)")
    public ResponseEntity<ApiResponse<Void>> deleteSalesOrder(
            @PathVariable Long soId,
            HttpServletRequest http) {
        return ResponseEntity.ok(outboundService.deleteSalesOrder(soId, getUserId(), getIp(http), ua(http)));
    }

    @DeleteMapping("/transfers/{transferId}")
    @PreAuthorize("hasRole('KEEPER')")
    @Operation(summary = "Xóa Internal Transfer (chỉ DRAFT)")
    public ResponseEntity<ApiResponse<Void>> deleteTransfer(
            @PathVariable Long transferId,
            HttpServletRequest http) {
        return ResponseEntity.ok(outboundService.deleteTransfer(transferId, getUserId(), getIp(http), ua(http)));
    }

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

    @PostMapping("/allocate/report-shortage")
    @PreAuthorize("hasRole('KEEPER')")
    @Operation(summary = "Báo thiếu hàng lên Manager",
            description = "Khi allocate không đủ tồn kho, Keeper tạo incident SHORTAGE để Manager xử lý. "
                    + "Đơn hàng tạm dừng chờ bổ sung hàng hoặc huỷ.")
    public ResponseEntity<ApiResponse<org.example.sep26management.application.dto.response.IncidentResponse>> reportShortage(
            @Valid @RequestBody AllocateStockRequest request,
            HttpServletRequest http) {
        return ResponseEntity.ok(allocateStockService.reportShortage(
                request.getDocumentId(), request.getOrderType(),
                getUserId(), getIp(http), ua(http)));
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
    @PreAuthorize("hasAnyRole('KEEPER','MANAGER','QC')")
    @Operation(summary = "Chi tiết Pick List theo taskId")
    public ResponseEntity<ApiResponse<PickListResponse>> getPickList(@PathVariable Long taskId) {
        return ResponseEntity.ok(pickListService.getPickList(taskId));
    }

    @GetMapping("/pick-list/by-document/{documentId}")
    @PreAuthorize("hasAnyRole('KEEPER','MANAGER','QC')")
    @Operation(summary = "Lấy Pick List theo documentId (soId)",
            description = "Tự động tìm picking task đang active cho đơn hàng. "
                    + "Dùng khi FE mở lại modal và không có taskId trong state.")
    public ResponseEntity<ApiResponse<PickListResponse>> getPickListByDocument(@PathVariable Long documentId) {
        return ResponseEntity.ok(pickListService.getPickListByDocument(documentId, getWarehouseId()));
    }

    @PatchMapping("/pick-list/{taskId}/confirm-picked")
    @PreAuthorize("hasRole('KEEPER')")
    @Operation(summary = "Keeper xác nhận đã lấy đủ hàng",
            description = "Chuyển picking task OPEN/IN_PROGRESS → PICKED. Bắt buộc trước khi bắt đầu QC.")
    public ResponseEntity<ApiResponse<PickListResponse>> confirmPicked(
            @PathVariable Long taskId, HttpServletRequest http) {
        return ResponseEntity.ok(pickListService.confirmPicked(taskId, getUserId(), getIp(http), ua(http)));
    }

    @PostMapping("/pick-list/{taskId}/scan-url")
    @PreAuthorize("hasRole('KEEPER')")
    @Operation(summary = "Tạo scan URL cho Picking (KEEPER)",
            description = "Tạo phiên scan và trả về URL + token để Keeper mở trên điện thoại quét mã hàng trong Pick List.\n\n"
                    + "- Mode `outbound_picking`: Keeper quét từng SKU trong pick list, khi đủ bấm **'Gửi sang QC'** — task sẽ chuyển PICKED → QC_IN_PROGRESS.\n"
                    + "- URL trả về dùng để tạo QR code hiển thị trên màn hình Keeper.")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPickingScanUrl(
            @PathVariable Long taskId,
            Authentication auth,
            HttpServletRequest http) {
        Long userId = getUserId();
        Long warehouseId = getWarehouseId();

        // Tạo hoặc reuse scan session
        var sessionResp = receivingSessionService.createSession(warehouseId, userId);
        String sessionId = sessionResp.getData().getSessionId();

        // Sinh scan token với role KEEPER
        String role = getCurrentRole();
        var tokenResp = receivingSessionService.generateScanToken(sessionId, userId, role);
        String scanToken = tokenResp.getData().get("scanToken");

        // Build URL trang scanner với mode=outbound_picking&taskId=...
        String base = http.getScheme() + "://" + http.getServerName()
                + (http.getServerPort() == 80 || http.getServerPort() == 443 ? ""
                : ":" + http.getServerPort());
        String scanUrl = base + "/v1/scan?token=" + scanToken
                + "&mode=outbound_picking&taskId=" + taskId + "&v=qr3";

        Map<String, String> result = new java.util.LinkedHashMap<>();
        result.put("scanUrl", scanUrl);
        result.put("scanToken", scanToken);
        result.put("sessionId", sessionId);
        result.put("taskId", String.valueOf(taskId));

        return ResponseEntity.ok(ApiResponse.success("Scan URL created for picking task " + taskId, result));
    }

    // ═════════════════════════════════════════════════════════════
    // NEW — QC Scan + Dispatch
    // ═════════════════════════════════════════════════════════════

    @PostMapping("/pick-list/{taskId}/start-qc")
    @PreAuthorize("hasAnyRole('KEEPER','QC')")
    @Operation(summary = "Bắt đầu phiên QC",
            description = "Chuyển picking task `PICKED` → `QC_IN_PROGRESS`, Sales Order `PICKING` → `QC_SCAN`.")
    public ResponseEntity<ApiResponse<Void>> startQcSession(@PathVariable Long taskId) {
        return ResponseEntity.ok(outboundQcService.startQcSession(taskId, getUserId()));
    }

    /**
     * POST /v1/outbound/pick-list/{taskId}/submit-qc
     * Nhan toan bo ket qua scan PASS/FAIL tu FE (1 lan duy nhat),
     * set qcResult cho tung item roi finalizeQc.
     */
    @PostMapping("/pick-list/{taskId}/submit-qc")
    @PreAuthorize("hasAnyRole('KEEPER','QC')")
    @Operation(summary = "Nop ket qua QC toan bo")
    public ResponseEntity<ApiResponse<QcSummaryResponse>> submitQcResults(
            @PathVariable Long taskId,
            @RequestBody java.util.List<QcScanRequest> results) {
        return ResponseEntity.ok(outboundQcService.submitQcResults(taskId, results, getUserId()));
    }

    @PostMapping("/pick-list/{taskId}/finalize-qc")
    @PreAuthorize("hasAnyRole('KEEPER','QC')")
    @Operation(summary = "Hoàn tất phiên QC từ điện thoại",
            description = "Gọi từ mobile khi bấm 'Kết thúc Scan'. Auto-PASS tất cả items chưa scan, cập nhật SO -> QC_SCAN.")
    public ResponseEntity<ApiResponse<QcSummaryResponse>> finalizeQc(@PathVariable Long taskId) {
        return ResponseEntity.ok(outboundQcService.finalizeQc(taskId, getUserId()));
    }

    @PostMapping("/qc-scan")
    @PreAuthorize("hasAnyRole('KEEPER','QC')")
    @Operation(summary = "QC Scan từng item",
            description = "Ghi nhận kết quả QC: `PASS` / `FAIL` / `HOLD`.\n\n"
                    + "- **FAIL**: bắt buộc có `reason`.\n"
                    + "- Task phải ở trạng thái `QC_IN_PROGRESS`.")
    public ResponseEntity<ApiResponse<Void>> qcScanItem(@Valid @RequestBody QcScanRequest request) {
        return ResponseEntity.ok(outboundQcService.scanItem(request, getUserId()));
    }

    @GetMapping("/pick-list/{taskId}/qc-summary")
    @PreAuthorize("hasAnyRole('KEEPER','MANAGER','QC')")
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
    // GET dispatch PDF URL (để FE redirect tải về)
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/sales-orders/{soId}/dispatch-pdf")
    @PreAuthorize("hasAnyRole('KEEPER','MANAGER','ACCOUNTANT')")
    @Operation(
            summary = "Lấy URL Phiếu Xuất Kho PDF",
            description = "Trả về URL Cloudinary của Phiếu Xuất Kho PDF.\n\n"
                    + "- Nếu PDF đã có: trả ngay URL đã lưu.\n"
                    + "- Nếu chưa có (ví dụ SO dispatch trước khi tính năng này live): tạo mới và trả về.\n"
                    + "- FE redirect user đến URL này để tải file PDF."
    )
    public ResponseEntity<ApiResponse<Map<String, String>>> getDispatchPdfUrl(@PathVariable Long soId) {
        String pdfUrl = dispatchPdfService.getOrCreatePdfUrl(soId);
        return ResponseEntity.ok(ApiResponse.success("OK", Map.of(
                "soId",   String.valueOf(soId),
                "pdfUrl", pdfUrl
        )));
    }

    @PostMapping("/sales-orders/{soId}/dispatch-pdf/regenerate")
    @PreAuthorize("hasAnyRole('KEEPER','MANAGER')")
    @Operation(
            summary = "Tạo lại Phiếu Xuất Kho PDF",
            description = "Tạo lại PDF (ví dụ sau khi chỉnh sửa thông tin). Ghi đè URL cũ."
    )
    public ResponseEntity<ApiResponse<Map<String, String>>> regenerateDispatchPdf(@PathVariable Long soId) {
        String pdfUrl = dispatchPdfService.generateAndUploadPdf(soId);
        return ResponseEntity.ok(ApiResponse.success("PDF regenerated", Map.of(
                "soId",   String.valueOf(soId),
                "pdfUrl", pdfUrl
        )));
    }

    // ─────────────────────────────────────────────────────────────
    // SIGNED NOTE — Upload ảnh phiếu xuất kho đã ký (via QR điện thoại)
    // ─────────────────────────────────────────────────────────────

    /**
     * POST /v1/outbound/sales-orders/{soId}/signed-note
     * Không cần JWT — điện thoại scan QR không có token.
     * Dùng token ngắn hạn trong URL để xác thực.
     */
    @PostMapping(value = "/sales-orders/{soId}/signed-note",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload ảnh phiếu xuất kho đã ký",
            description = "Điện thoại scan QR → chụp ảnh → POST ảnh lên đây. Không cần JWT.")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadSignedNote(
            @PathVariable Long soId,
            @RequestParam("photo") org.springframework.web.multipart.MultipartFile photo) {
        return ResponseEntity.ok(signedNoteService.uploadSignedNote(soId, photo));
    }

    /**
     * POST /v1/outbound/qc-photo
     * Upload ảnh hàng hỏng từ điện thoại scan QC FAIL.
     * Không gắn với SO cụ thể — trả về URL 上 Cloudinary.
     */
    @PostMapping(value = "/qc-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload ảnh hàng hỏng QC FAIL")
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> uploadQcPhoto(
            @RequestParam("photo") org.springframework.web.multipart.MultipartFile photo) {
        if (photo == null || photo.isEmpty())
            return ResponseEntity.badRequest().body(ApiResponse.error("Vui lòng chọn ảnh."));
        try {
            String publicId = "qc_damage/" + System.currentTimeMillis();
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> result = cloudinary.uploader().upload(
                    photo.getBytes(),
                    ObjectUtils.asMap("public_id", publicId, "resource_type", "image",
                            "quality", "auto:good", "fetch_format", "auto"));
            String url = (String) result.get("secure_url");
            if (url == null) return ResponseEntity.status(500).body(ApiResponse.error("Upload thất bại"));
            return ResponseEntity.ok(ApiResponse.success("OK", java.util.Map.of("url", url)));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error("Lỗi upload: " + e.getMessage()));
        }
    }

    @GetMapping("/sales-orders/{soId}/signed-note")
    @PreAuthorize("hasAnyRole('KEEPER','MANAGER','ACCOUNTANT')")
    @Operation(summary = "Xem ảnh phiếu xuất kho đã ký")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSignedNote(@PathVariable Long soId) {
        return ResponseEntity.ok(signedNoteService.getSignedNote(soId));
    }

    /**
     * POST /v1/outbound/sales-orders/{soId}/pick-signed-note
     * Nhân viên scan QR → chụp ảnh phiếu lấy hàng đã ký → upload.
     * Không cần JWT — dùng QR token ngắn hạn.
     */
    @PostMapping(value = "/sales-orders/{soId}/pick-signed-note",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload ảnh phiếu lấy hàng đã ký (nhân viên kho)",
            description = "Nhân viên scan QR từ màn hình picking → chụp ảnh phiếu → POST lên đây.")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadPickSignedNote(
            @PathVariable Long soId,
            @RequestParam("photo") org.springframework.web.multipart.MultipartFile photo) {
        return ResponseEntity.ok(pickSignedNoteService.uploadPickSignedNote(soId, photo));
    }

    // ─── THÊM VÀO OutboundController.java — sau endpoint finalize-qc ─────────────
// Dán 2 endpoint này vào sau dòng:
//   public ResponseEntity<ApiResponse<QcSummaryResponse>> finalizeQc(...)

    // ─── [V20] Resolve DAMAGE Incident (Manager) ──────────────────────────────
    @PostMapping("/incidents/{incidentId}/resolve-damage")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(
            summary = "Manager xử lý hàng hỏng QC (DAMAGE)",
            description = "Sau khi QC FAIL tạo Incident DAMAGE:\n\n"
                    + "- `RETURN_SCRAP`: trừ tồn hàng hỏng → SO → PICKING để Keeper re-pick\n"
                    + "- `ACCEPT`: chấp nhận xuất luôn hàng lỗi → SO → QC_SCAN → DISPATCHED"
    )
    public ResponseEntity<ApiResponse<IncidentResponse>> resolveOutboundDamage(
            @PathVariable Long incidentId,
            @Valid @RequestBody ResolveOutboundDamageRequest request) {
        return ResponseEntity.ok(
                outboundQcService.resolveOutboundDamage(incidentId, request, getUserId()));
    }

    // ─── [V20] Resolve SHORTAGE Incident (Manager) ────────────────────────────
    @PostMapping("/incidents/{incidentId}/resolve-shortage")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(
            summary = "Manager xử lý thiếu hàng (SHORTAGE)",
            description = "Sau khi Keeper báo thiếu hàng:\n\n"
                    + "- `WAIT_BACKORDER`: SO → WAITING_STOCK, chờ nhập bù rồi Keeper tự re-Allocate\n"
                    + "- `CLOSE_SHORT`: cắt orderedQty về available → SO → APPROVED → re-Allocate ngay"
    )
    public ResponseEntity<ApiResponse<IncidentResponse>> resolveOutboundShortage(
            @PathVariable Long incidentId,
            @Valid @RequestBody ResolveOutboundShortageRequest request) {
        return ResponseEntity.ok(
                outboundQcService.resolveOutboundShortage(incidentId, request, getUserId()));
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