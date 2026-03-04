package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.ReceivingOrderResponse;
import org.example.sep26management.application.service.ReceivingOrderService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/receiving-orders")
@RequiredArgsConstructor
@Tag(name = "Receiving Orders (GRN)", description = "Quản lý phiếu nhập kho (Goods Receipt Note). "
        + "Quy trình: Tạo GRN từ scan session → SUBMITTED → APPROVED (Manager) → POSTED (Accountant, tự động tạo Putaway Task). "
        + "Khi POST, hệ thống tự động gợi ý vị trí putaway dựa trên zone-category matching.")
public class ReceivingOrderController {

    private final ReceivingOrderService receivingOrderService;

    /** GET /v1/receiving-orders?status=SUBMITTED */
    @GetMapping
    @Operation(summary = "Danh sách phiếu nhập kho", description = "Lấy danh sách GRN. Có thể lọc theo status: SUBMITTED, APPROVED, POSTED. "
            + "Response bao gồm thông tin supplier, warehouse, người tạo/duyệt/xác nhận.")
    public ApiResponse<List<ReceivingOrderResponse>> list(@RequestParam(required = false) String status) {
        return receivingOrderService.listOrders(status);
    }

    /** GET /v1/receiving-orders/{id} */
    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết phiếu nhập kho", description = "Lấy chi tiết 1 GRN theo ID, bao gồm danh sách receiving items (SKU, barcode, số lượng ordered/received).")
    public ApiResponse<ReceivingOrderResponse> get(@PathVariable Long id) {
        return receivingOrderService.getOrder(id);
    }

    /** POST /v1/receiving-orders/{id}/submit — Keeper */
    @PostMapping("/{id}/submit")
    @Operation(summary = "Gửi phiếu nhập kho (Keeper)", description = "Keeper gửi GRN để Manager duyệt. Chuyển status từ DRAFT → SUBMITTED. "
            + "Yêu cầu: GRN phải có ít nhất 1 item với receivedQty > 0.")
    public ApiResponse<ReceivingOrderResponse> submit(
            @PathVariable Long id,
            Authentication auth) {
        return receivingOrderService.submit(id, extractUserId(auth));
    }

    /** POST /v1/receiving-orders/{id}/approve — Manager only */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Duyệt phiếu nhập kho (Manager)", description = "Manager duyệt GRN. Chuyển status từ SUBMITTED → APPROVED. Chỉ role MANAGER mới được phép.")
    public ApiResponse<ReceivingOrderResponse> approve(
            @PathVariable Long id,
            Authentication auth) {
        return receivingOrderService.approve(id, extractUserId(auth));
    }

    /** POST /v1/receiving-orders/{id}/post — Accountant */
    @PostMapping("/{id}/post")
    @Operation(summary = "Xác nhận nhập kho & tạo Putaway Task (Accountant)", description = "Accountant xác nhận GRN. Chuyển status APPROVED → POSTED. "
            + "Tự động: (1) Tạo inventory tại staging location, (2) Tạo Putaway Task với suggested location "
            + "dựa trên zone-category matching (ví dụ: SKU category 'HC' → zone 'Z-HC' → BIN có dung lượng trống nhất).")
    public ApiResponse<ReceivingOrderResponse> post(
            @PathVariable Long id,
            Authentication auth) {
        return receivingOrderService.post(id, extractUserId(auth));
    }

    @SuppressWarnings("unchecked")
    private Long extractUserId(Authentication auth) {
        if (auth != null && auth.getDetails() instanceof Map) {
            Object uid = ((Map<?, ?>) auth.getDetails()).get("userId");
            if (uid instanceof Long)
                return (Long) uid;
            if (uid instanceof Integer)
                return ((Integer) uid).longValue();
        }
        throw new RuntimeException("Cannot extract userId from authentication");
    }
}
