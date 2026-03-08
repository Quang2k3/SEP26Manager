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
 * SCRUM-505 POST /v1/outbound — Create (KEEPER)
 * SCRUM-506 PUT /v1/outbound/sales-orders/{id} — Update SO (KEEPER)
 * PUT /v1/outbound/transfers/{id} — Update TF (KEEPER)
 * SCRUM-507 PATCH /v1/outbound/sales-orders/{id}/submit — Submit SO (KEEPER)
 * PATCH /v1/outbound/transfers/{id}/submit — Submit TF (KEEPER)
 * SCRUM-508 PATCH /v1/outbound/sales-orders/{id}/approve — Approve (MANAGER)
 * SCRUM-509 GET /v1/outbound — List (ALL)
 * GET /v1/outbound/summary — Summary (ALL)
 * SCRUM-510 POST /v1/outbound/allocate — Allocate (KEEPER/MANAGER)
 * SCRUM-511 POST /v1/outbound/pick-list — Gen PickList (KEEPER/MANAGER)
 * GET /v1/outbound/pick-list/{taskId} — Get PickList (KEEPER/MANAGER)
 */
@RestController
@RequestMapping("/v1/outbound")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Outbound Management", description = "Quản lý quy trình xuất kho (Outbound) cho Đơn hàng bán (Sales Order) hoặc Chuyển kho nội bộ (Internal Transfer). "
        + "Quy trình: Tạo lệnh xuất → Submit (DRAFT -> SUBMITTED) → Manager duyệt (APPROVED) → Phân bổ tồn kho (Allocate) → Tạo danh sách lấy hàng (Gen Pick List) → Lấy hàng (Picking) → Xác nhận xuất kho thành công.")
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
    @Operation(summary = "Tạo lệnh xuất kho (DRAFT)", description = "Tạo một lệnh xuất kho mới (Sales Order hoặc Internal Transfer).\n\n"
            + "**Data yêu cầu:** \n"
            + "- `Body.orderType`: Chọn `SALES_ORDER` hoặc `INTERNAL_TRANSFER`.\n"
            + "- `Body.customerCode`: Mã khách hàng — **LẤY TỪ** API `GET /v1/customers`. FE hiển thị tên, gửi mã code. (Bắt buộc nếu `SALES_ORDER`).\n"
            + "- `Body.destinationWarehouseCode`: Mã kho đích — **LẤY TỪ** API `GET /v1/warehouses`. (Bắt buộc nếu `INTERNAL_TRANSFER`).\n"
            + "- `Body.items`: Danh sách các món hàng (`skuCode` hoặc `skuId`) và số lượng cần xuất (`quantity`).\n\n"
            + "👉 **Lưu ý:** `warehouseId` (kho nguồn) được lấy tự động từ JWT token — FE **không cần truyền** vào.\n\n"
            + "👉 **Kết quả:** Trả về 1 lệnh xuất có trạng thái `DRAFT` và kèm theo thuộc tính `documentId` (Hay còn gọi là **Outbound ID**). FE sẽ DÙNG `documentId` này cho tất cả các bước tiếp theo.")
    public ResponseEntity<ApiResponse<OutboundResponse>> createOutbound(
            @Valid @RequestBody CreateOutboundRequest request,
            HttpServletRequest http) {
        // Inject warehouseId from JWT — FE does not need to send this
        request.setWarehouseId(getWarehouseId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(outboundService.createOutbound(request, getUserId(), getIp(http), ua(http)));
    }

    // ─────────────────────────────────────────────────────────────
    // SCRUM-506: Update
    // ─────────────────────────────────────────────────────────────
    @PutMapping("/sales-orders/{soId}")
    @PreAuthorize("hasRole('KEEPER')")
    @Operation(summary = "Cập nhật đơn bán hàng (Keeper)",
            description = "Cập nhật yêu cầu xuất kho loại Đơn Bán Hàng (SO) đang ở trạng thái DRAFT.\n\n"
                    + "**Data yêu cầu:**\n"
                    + "- `@PathVariable soId`: Mã **Outbound ID** (lấy từ danh sách `GET /v1/outbound`, field `documentId`).\n"
                    + "- `Body.customerCode` (tuỳ chọn): Mã khách hàng mới — **LẤY TỪ** `GET /v1/customers`.\n"
                    + "- `Body.items`: Danh sách hàng cần cập nhật.")
    public ResponseEntity<ApiResponse<OutboundResponse>> updateSalesOrder(
            @PathVariable Long soId,
            @Valid @RequestBody UpdateOutboundRequest request,
            HttpServletRequest http) {
        return ResponseEntity.ok(outboundService.updateOutbound(
                OutboundType.SALES_ORDER, soId, request, getUserId(), getIp(http), ua(http)));
    }

    @PutMapping("/transfers/{transferId}")
    @PreAuthorize("hasRole('KEEPER')")
    @Operation(summary = "Cập nhật đơn chuyển kho (Keeper)",
            description = "Cập nhật yêu cầu xuất kho loại Chuyển Kho (Transfer) đang ở trạng thái DRAFT.\n\n"
                    + "**Data yêu cầu:**\n"
                    + "- `@PathVariable transferId`: Mã **Outbound ID** (lấy từ danh sách `GET /v1/outbound`, field `documentId`).\n"
                    + "- `Body.destinationWarehouseCode` (tuỳ chọn): Mã kho đích mới — **LẤY TỪ** `GET /v1/warehouses`.\n"
                    + "- `Body.items`: Danh sách hàng cần cập nhật.")
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
    @Operation(summary = "Trình duyệt lệnh xuất (Submit)", description = "Nhân viên gửi lệnh xuất kho lên cho Manager duyệt.\n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable id`: Mã **Outbound ID** (Lấy từ bảng danh sách hoặc từ lúc tạo DRAFT). \n"
            + "👉 Trạng thái chuyển từ `DRAFT` thành `SUBMITTED`.")
    public ResponseEntity<ApiResponse<OutboundResponse>> submitSalesOrder(
            @PathVariable Long soId,
            @RequestBody(required = false) SubmitOutboundRequest request,
            HttpServletRequest http) {
        if (request == null)
            request = new SubmitOutboundRequest();
        return ResponseEntity.ok(outboundService.submitOutbound(
                OutboundType.SALES_ORDER, soId, request, getUserId(), getIp(http), ua(http)));
    }

    @PatchMapping("/transfers/{transferId}/submit")
    @PreAuthorize("hasRole('KEEPER')")
    @Operation(summary = "Trình duyệt đơn chuyển kho (Keeper)", description = "Gửi Lệnh chuyển kho (Transfer) yêu cầu Manager duyệt. Chuyển trạng thái từ DRAFT/REJECTED sang SUBMITTED.")
    public ResponseEntity<ApiResponse<OutboundResponse>> submitTransfer(
            @PathVariable Long transferId,
            @RequestBody(required = false) SubmitOutboundRequest request,
            HttpServletRequest http) {
        if (request == null)
            request = new SubmitOutboundRequest();
        return ResponseEntity.ok(outboundService.submitOutbound(
                OutboundType.INTERNAL_TRANSFER, transferId, request, getUserId(), getIp(http), ua(http)));
    }

    // ─────────────────────────────────────────────────────────────
    // SCRUM-508: Approve
    // ─────────────────────────────────────────────────────────────
    @PatchMapping("/sales-orders/{soId}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Duyệt lệnh xuất kho (Manager)", description = "Manager đồng ý cho phép xuất lô hàng này.\n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable id`: Mã **Outbound ID**.\n"
            + "👉 Trạng thái chuyển từ `SUBMITTED` thành `APPROVED`.")
    public ResponseEntity<ApiResponse<OutboundResponse>> approveOutbound(
            @PathVariable Long soId,
            @Valid @RequestBody ApproveOutboundRequest request,
            HttpServletRequest http) {
        return ResponseEntity.ok(outboundService.approveSalesOrder(
                soId, request, getUserId(), getIp(http), ua(http)));
    }

    // ─────────────────────────────────────────────────────────────
    // SCRUM-509: View Outbound List
    // GET
    // /v1/outbound?warehouseId=1&status=DRAFT&orderType=SALES_ORDER&keyword=EXP&page=0&size=20
    // ─────────────────────────────────────────────────────────────
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sách lệnh xuất kho", description = "Lấy danh sách các đơn xuất kho để hiển thị lên bảng.\n\n"
            + "**Data yêu cầu:** \n"
            + "- `warehouseId` được lấy **tự động từ JWT token** — FE **không cần truyền**.\n"
            + "- Các params tuỳ chọn: `status`, `orderType`, `keyword` dùng để filter.\n\n"
            + "👉 **Kết quả:** Trả về danh sách. FE lấy thuộc tính `documentId` ở mỗi dòng để thao tác detail, submit, approve...")
    public ResponseEntity<ApiResponse<PageResponse<OutboundListResponse>>> listOutbound(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) OutboundType orderType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long createdBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long warehouseId = getWarehouseId(); // auto-resolved from JWT
        String role = getCurrentRole();
        return ResponseEntity.ok(outboundListService.listOutbound(
                warehouseId, status, orderType, keyword, createdBy,
                fromDate, toDate, getUserId(), role, page, size));
    }

    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Thống kê tình trạng lệnh xuất", description = "Lấy dữ liệu thống kê số lượng đơn lệnh xuất theo trạng thái. warehouseId lấy tự động từ JWT.")
    public ResponseEntity<ApiResponse<OutboundSummaryResponse>> getSummary() {
        Long warehouseId = getWarehouseId();
        return ResponseEntity.ok(outboundListService.getSummary(warehouseId));
    }

    // ─────────────────────────────────────────────────────────────
    // SCRUM-510: Allocate / Reserve Stock
    // POST /v1/outbound/allocate
    // ─────────────────────────────────────────────────────────────
    @PostMapping("/allocate")
    @PreAuthorize("hasAnyRole('KEEPER','MANAGER')")
    @Operation(summary = "Giữ/Khóa Hàng (Allocate Stock)", description = "Đây là bước TỰ ĐỘNG CẦN THIẾT trước khi đi lấy hàng. Hệ thống quét kho và khóa số lượng tồn thực tế lại để đơn này không bị thiếu hàng.\n\n"
            + "**Data yêu cầu:** \n"
            + "- `Body.documentId`: Chính là cái mã **Outbound ID** (lấy từ Lệnh Xuất đã Approved).\n"
            + "- `Body.orderType`: `SALES_ORDER` hoặc `INTERNAL_TRANSFER`.\n"
            + "👉 **Kết quả:** Tồn kho (Inventory) chuyển từ AVAILABLE sang ALLOCATED.")
    public ResponseEntity<ApiResponse<AllocateStockResponse>> allocateStock(
            @Valid @RequestBody AllocateStockRequest request,
            HttpServletRequest http) {
        return ResponseEntity.ok(allocateStockService.allocateStock(
                request, getUserId(), getIp(http), ua(http)));
    }

    // ─────────────────────────────────────────────────────────────
    // SCRUM-511: Generate Pick List
    // POST /v1/outbound/pick-list
    // GET /v1/outbound/pick-list/{taskId}
    // ─────────────────────────────────────────────────────────────
    @PostMapping("/pick-list")
    @PreAuthorize("hasAnyRole('KEEPER','MANAGER')")
    @Operation(summary = "Tạo lộ trình đi lấy hàng (Generate Pick List)",
            description = "Gom những đơn đã Khóa hàng để in ra 1 tờ giấy (điện tử) chỉ đường cho nhân viên đi lấy hàng.\n\n"
                    + "**Data yêu cầu:** \n"
                    + "- `Body.documentId`: Mã **Outbound ID** — **LẤY TỪ** danh sách `GET /v1/outbound` (field `documentId`). Đây là đơn đã Allocate.\n"
                    + "- `Body.orderType`: `SALES_ORDER` hoặc `INTERNAL_TRANSFER`.\n"
                    + "- `Body.assignedTo` (tuỳ chọn): ID nhân viên được phân công lấy hàng.\n\n"
                    + "👉 **Kết quả:** Trả về 1 cái mã `pickingTaskId` (Mã Pick List). FE giữ `pickingTaskId` này để nhân viên mở danh sách lấy hàng chi tiết bằng API bên dưới.")
    public ResponseEntity<ApiResponse<PickListResponse>> generatePickList(
            @Valid @RequestBody GeneratePickListRequest request,
            HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pickListService.generatePickList(request, getUserId(), getIp(http), ua(http)));
    }

    @GetMapping("/pick-list/{taskId}")
    @PreAuthorize("hasAnyRole('KEEPER','MANAGER')")
    @Operation(summary = "Đọc danh sách Lấy hàng (Chi tiết Pick List)", description = "Xem và đi nhặt hàng theo sự chỉ dẫn.\n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable taskId`: Mã **Pick List Task ID** lấy từ response lúc tạo mới ở trên.\n"
            + "👉 **Kết quả:** Trả về 1 list báo cho thủ kho biết: Hãy đến kệ Z-HC lấy 2 cái điện thoại ra đây đóng gói.")
    public ResponseEntity<ApiResponse<PickListResponse>> getPickList(
            @PathVariable Long taskId) {
        return ResponseEntity.ok(pickListService.getPickList(taskId));
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────
    private Long getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null)
            throw new RuntimeException(MessageConstants.NOT_AUTHENTICATED);
        Object details = auth.getDetails();
        if (details instanceof Map<?, ?> map) {
            Object uid = map.get("userId");
            if (uid instanceof Long l)
                return l;
            if (uid instanceof Integer i)
                return i.longValue();
            if (uid != null)
                return Long.parseLong(uid.toString());
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
        if (auth == null)
            return "";
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .findFirst().orElse("");
    }

    private String getIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank())
            return xff.split(",")[0].trim();
        String xri = req.getHeader("X-Real-IP");
        return (xri != null && !xri.isBlank()) ? xri : req.getRemoteAddr();
    }

    private String ua(HttpServletRequest req) {
        return req.getHeader("User-Agent");
    }
}