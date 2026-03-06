package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.sep26management.application.dto.request.RejectRequest;
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
        + "Quy trình: Tạo GRN từ scan session → SUBMITTED → APPROVED (Manager) → POSTED (QC, tự động tạo Putaway Task). "
        + "Khi POST, hệ thống tự động gợi ý vị trí putaway dựa trên zone-category matching.")
public class ReceivingOrderController {

    private final ReceivingOrderService receivingOrderService;

    /** GET /v1/receiving-orders?status=SUBMITTED */
    @GetMapping
    @Operation(summary = "Danh sách phiếu nhập kho (GRN)", description = "Lấy danh sách các phiếu nhập. \n\n"
            + "**Data yêu cầu:** \n"
            + "- `Query.status` (Tùy chọn): Bộ lọc trạng thái (`SUBMITTED`, `APPROVED`, `POSTED`). Nếu không truyền sẽ lấy tất cả.\n\n"
            + "👉 **Kết quả:** Trả về danh sách phiếu nhập, mỗi lệnh chứa một mã `id` (Hay còn gọi là GRN ID). FE cần lấy `id` này để dùng cho các API xem chi tiết, duyệt, hoặc xác nhận.")
    public ApiResponse<List<ReceivingOrderResponse>> list(@RequestParam(required = false) String status) {
        return receivingOrderService.listOrders(status);
    }

    /** GET /v1/receiving-orders/{id} */
    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết phiếu nhập kho", description = "Xem chi tiết một GRN bao gồm list các sản phẩm (SKU) bên trong. \n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable id`: Mã GRN ID, **LẤY TỪ** api danh sách `GET /v1/receiving-orders` hoặc api tạo GRN trước đó.")
    public ApiResponse<ReceivingOrderResponse> get(@PathVariable Long id) {
        return receivingOrderService.getOrder(id);
    }

    /** POST /v1/receiving-orders/{id}/submit — Keeper */
    @PostMapping("/{id}/submit")
    @Operation(summary = "Trình duyệt phiếu nhập kho (Keeper)", description = "Keeper gửi GRN yêu cầu Manager duyệt. Chuyển phiếu từ DRAFT thành SUBMITTED. \n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable id`: Mã GRN ID cần duyệt (Lấy từ URL hoặc Table row ID).")
    public ApiResponse<ReceivingOrderResponse> submit(
            @PathVariable Long id,
            Authentication auth) {
        return receivingOrderService.submit(id, extractUserId(auth));
    }

    /** POST /v1/receiving-orders/{id}/approve — Manager only */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Duyệt phiếu nhập kho (Manager)", description = "Điểm danh và xác nhận phiếu hợp lệ. Chuyển từ SUBMITTED thành APPROVED. \n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable id`: Mã GRN ID (Lấy từ URL hoặc Table row ID).\n"
            + "👉 **Note:** Yêu cầu quyền bộ đàm tài khoản có ROLE_MANAGER.")
    public ApiResponse<ReceivingOrderResponse> approve(
            @PathVariable Long id,
            Authentication auth) {
        return receivingOrderService.approve(id, extractUserId(auth));
    }

    /** POST /v1/receiving-orders/{id}/reject — Manager/Keeper */
    @PostMapping("/{id}/reject")
    @Operation(summary = "Từ chối phiếu nhập kho", description = "Hủy bỏ hoặc trả lại phiếu sai. Chuyển trạng thái sang REJECTED. \n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable id`: Mã GRN ID.\n"
            + "- `Body.reason`: **BẮT BUỘC**, Chuỗi string diễn giải lý do tại sao Keeper/Manager từ chối phiếu này.")
    public ApiResponse<ReceivingOrderResponse> reject(
            @PathVariable Long id,
            @RequestBody RejectRequest request,
            Authentication auth) {
        return receivingOrderService.reject(id, request.getReason(), extractUserId(auth));
    }

    /** POST /v1/receiving-orders/{id}/post — QC */
    @PostMapping("/{id}/post")
    @Operation(summary = "Cất hàng vào Trạm Chờ (Staging) & Tự động tạo Task Xếp Kệ", description = "Hành động kết thúc quy trình nhận hàng. Trạng thái chuyển thành POSTED. \n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable id`: Mã GRN ID của phiếu đã APPROVED.\n\n"
            + "👉 **Phép thuật:** API này sẽ TỰ ĐỘNG sinh ra 1 công việc dọn kho gọi là **Putaway Task**. Giao diện tiếp theo FE cần dẫn người dùng sang màn hình Putaway để xem các task vừa đc sinh ra này.")
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
