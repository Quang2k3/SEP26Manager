package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.sep26management.application.dto.request.ReceivingOrderRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.PageResponse;
import org.example.sep26management.application.dto.response.ReceivingOrderResponse;
import org.example.sep26management.application.dto.response.GrnResponse;
import org.example.sep26management.application.service.ReceivingOrderService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/receiving-orders")

@RequiredArgsConstructor
@Tag(name = "Receiving Orders (GRN)", description = "Quản lý phiếu nhập kho (Goods Receipt Note). "
        + "Quy trình: Tạo DRAFT → Submit (PENDING_COUNT) → Keeper quét barcode → Finalize Count (SUBMITTED) → QC duyệt → GRN.")
public class ReceivingOrderController {

    private final ReceivingOrderService receivingOrderService;

    /** GET /v1/receiving-orders?status=SUBMITTED */
    @GetMapping
    @Operation(summary = "Danh sách Phiếu nhập kho (List GRN)", description = "Lấy danh sách các phiếu nhập kho (GRN) để hiển thị lên bảng.\n\n"
            + "**Data yêu cầu:** \n"
            + "- `Query.status` (Tùy chọn): Lọc theo trạng thái, ví dụ: DRAFT, SUBMITTED, APPROVED.\n"
            + "- `Query.page` (Tùy chọn): Trang kết quả, mặc định là `0`.\n"
            + "- `Query.size` (Tùy chọn): Kích thước trang, mặc định là `10`.\n\n"
            + "👉 **Kết quả:** Trả về danh sách. FE dùng thuộc tính `receivingCode` (GRN ID) hoặc `receivingId` của mỗi dòng để thao tác tính năng Detail, Duyệt (Approve)...")
    public ApiResponse<PageResponse<ReceivingOrderResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return receivingOrderService.listOrders(status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết phiếu nhập kho", description = "Xem chi tiết một phiếu nhập dự kiến bao gồm list các sản phẩm (SKU) bên trong. \n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable id`: Mã Phiếu Nhận Hàng (LẤY TỪ: attribute `receivingId` của API GET danh sách `GET /v1/receiving-orders` hoặc kết quả API POST tạo bảng nháp).")
    public ApiResponse<ReceivingOrderResponse> get(@PathVariable Long id) {
        return receivingOrderService.getOrder(id);
    }

    /** POST /v1/receiving-orders — Keeper creates DRAFT */
    @PostMapping
    @Operation(summary = "Tạo phiếu nhập kho nháp (Keeper)", description = "Keeper tạo phiếu nhập kho DRAFT dựa trên chứng từ với các mặt hàng và số lượng dự kiến. \n\n"
            + "**Data yêu cầu:** \n"
            + "- `ReceivingOrderRequest`: Gồm `warehouseId` (kho user chọn), type, supplier, list của `skuCode` và `expectedQty`.\n"
            + "👉 **Kết quả:** Trả về Phiếu với trạng thái DRAFT. FE lưu lại mã `receivingId` (hoặc `receivingCode`) để gọi tiếp các API cập nhật số lượng.")
    public ApiResponse<ReceivingOrderResponse> createDraftOrder(
            @Valid @RequestBody ReceivingOrderRequest request,
            Authentication auth) {
        Long userId = extractUserId(auth);
        Long warehouseId = request.getWarehouseId();
        return receivingOrderService.createDraftOrder(request, warehouseId, userId);
    }

    /** PUT /v1/receiving-orders/{id} — Keeper updates DRAFT order */
    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật phiếu nhập kho nháp (Keeper)", description = "Cập nhật thông tin phiếu nhập kho khi còn ở trạng thái DRAFT.\\n\\n"
            + "⚠️ **CHỈ cho phép khi status = DRAFT.** Sau khi submit, phiếu bị khóa chỉnh sửa.\\n\\n"
            + "**Data yêu cầu:** \\n"
            + "- `@PathVariable id`: Mã Phiếu Nhận Hàng.\\n"
            + "- `ReceivingOrderRequest`: Có thể cập nhật `sourceType`, `supplierCode`, `note`, `sourceReferenceCode`, `items`.\\n"
            + "- Nếu truyền `items`, danh sách items cũ sẽ bị **thay thế hoàn toàn** bằng items mới.")
    public ApiResponse<ReceivingOrderResponse> updateDraftOrder(
            @PathVariable Long id,
            @Valid @RequestBody ReceivingOrderRequest request,
            Authentication auth) {
        return receivingOrderService.updateDraftOrder(id, request, extractUserId(auth));
    }

    /** DELETE /v1/receiving-orders/{id} — Keeper deletes DRAFT order */
    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa phiếu nhập kho nháp (Keeper)", description = "Xóa phiếu nhập kho khi còn ở trạng thái DRAFT.\\n\\n"
            + "⚠️ **CHỈ cho phép khi status = DRAFT.** Phiếu đã submit không thể xóa.\\n\\n"
            + "**Data yêu cầu:** \\n"
            + "- `@PathVariable id`: Mã Phiếu Nhận Hàng cần xóa.")
    public ApiResponse<Void> deleteDraftOrder(
            @PathVariable Long id,
            Authentication auth) {
        return receivingOrderService.deleteDraftOrder(id, extractUserId(auth));
    }

    @PutMapping("/{id}/lines")
    @Operation(summary = "Cập nhật số liệu dự kiến (Keeper)", description = "Chỉnh sửa thông tin items trên phiếu nhập kho.\n\n"
            + "⚠️ **CHỈ cho phép khi status = DRAFT.** Sau khi submit (PENDING_COUNT), phiếu sẽ bị khóa chỉnh sửa.\n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable id`: Mã Phiếu Nhận Hàng.\n"
            + "- `UpdateReceivingLinesRequest`: Danh sách items với `receivedQty`, `note`.")
    public ApiResponse<ReceivingOrderResponse> updateLines(
            @PathVariable Long id,
            @Valid @RequestBody org.example.sep26management.application.dto.request.UpdateReceivingLinesRequest request,
            Authentication auth) {
        return receivingOrderService.updateLines(id, request, extractUserId(auth));
    }

    /** POST /v1/receiving-orders/{id}/submit — Keeper: DRAFT → PENDING_COUNT */
    @PostMapping("/{id}/submit")
    @Operation(summary = "Trình duyệt phiếu nhập kho (Keeper)", description = "Keeper submit phiếu. Trạng thái chuyển từ **DRAFT → PENDING_COUNT**.\n\n"
            + "Sau khi submit, phiếu bị **khóa chỉnh sửa** (không thể gọi updateLines nữa). "
            + "Keeper bắt đầu quét barcode để kiểm đếm thực tế.\n\n"
            + "👉 **Bước tiếp theo:** Quét barcode xong → gọi `POST /{id}/finalize-count` để chốt số lượng.\n\n"
            + "**Data yêu cầu:**\n"
            + "- `@PathVariable id`: Mã Phiếu (receivingId).\n"
            + "- Body: Không cần")
    public ApiResponse<ReceivingOrderResponse> submit(
            @PathVariable Long id,
            Authentication auth) {
        return receivingOrderService.submit(id, extractUserId(auth));
    }

    /** POST /v1/receiving-orders/{id}/finalize-count — Keeper: PENDING_COUNT → SUBMITTED */
    @PostMapping("/{id}/finalize-count")
    @Operation(summary = "Chốt kiểm đếm (Keeper)", description = "Keeper hoàn tất quét barcode, chốt số lượng thực nhận.\n\n"
            + "Trạng thái chuyển từ **PENDING_COUNT → SUBMITTED**. "
            + "Hệ thống tự động sync dữ liệu từ scan session (nếu có) vào phiếu.\n\n"
            + "👉 **Bước tiếp theo:** QC kiểm tra chất lượng (`POST /{id}/qc-approve` hoặc `POST /{id}/qc-submit-session`).\n\n"
            + "**Data yêu cầu:**\n"
            + "- `@PathVariable id`: Mã Phiếu (receivingId).\n"
            + "- Body: Không cần")
    public ApiResponse<ReceivingOrderResponse> finalizeCount(
            @PathVariable Long id,
            Authentication auth) {
        return receivingOrderService.finalizeCount(id, extractUserId(auth));
    }

    /** POST /v1/receiving-orders/{id}/qc-approve — QC */
    @PostMapping("/{id}/qc-approve")
    @PreAuthorize("hasRole('QC')")
    @Operation(summary = "QC xác nhận chất lượng OK", description = "QC xác nhận lô hàng đạt chất lượng (100% pass hoặc đã báo cáo sự cố xong). Chuyển phiếu từ SUBMITTED thành QC_APPROVED. \n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable id`: Mã Phiếu Nhận Hàng (LẤY TỪ: attribute `receivingId` của API GET danh sách `GET /v1/receiving-orders?status=SUBMITTED`).\n\n"
            + "👉 **Sau bước này:** Keeper mới được phép tạo GRN (`POST /v1/receiving-orders/{id}/generate-grn`).")
    public ApiResponse<ReceivingOrderResponse> qcApprove(
            @PathVariable Long id,
            Authentication auth) {
        return receivingOrderService.qcApprove(id, extractUserId(auth));
    }

    /** POST /v1/receiving-orders/{id}/qc-submit-session — QC Scanner */
    @PostMapping("/{id}/qc-submit-session")
    @PreAuthorize("hasRole('QC')")
    @Operation(summary = "QC Scanner hoàn tất quá trình quét (QC)", description = "Sau khi QC hoàn tất việc quét mã, gửi yêu cầu kết thúc chốt số lượng dựa trên session quét. Tự động đối chiếu với số Keeper nhận trước đó.\n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable id`: Mã Phiếu Nhận Hàng (LẤY TỪ: attribute `receivingId` của API GET danh sách).\n"
            + "- `@RequestParam sessionId`: ID session quét từ Redis.\n\n"
            + "👉 **Note:** Hệ thống tự sinh `Incident` nếu có hàng `FAIL`. Nếu 100% `PASS`, đơn sẽ lên `QC_APPROVED`.")
    public ApiResponse<Map<String, Object>> qcSubmitSession(
            @PathVariable Long id,
            @RequestParam String sessionId,
            Authentication auth) {
        return receivingOrderService.qcSubmitSession(id, sessionId, extractUserId(auth));
    }

    /** POST /v1/receiving-orders/{id}/generate-grn — Keeper */
    @PostMapping("/{id}/generate-grn")
    @Operation(summary = "Tạo phiếu nhập kho GRN (Keeper)", description = "Keeper tạo GRN sau khi QC đã xác nhận chất lượng (status = QC_APPROVED). Chuyển từ QC_APPROVED thành GRN_CREATED. \n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable id`: Mã Phiếu Nhận Hàng (LẤY TỪ: attribute `receivingId` của API GET danh sách `GET /v1/receiving-orders?status=QC_APPROVED`).\n\n"
            + "👉 **Điều kiện:** Status phải là `QC_APPROVED`. Các Incident (nếu có) phải được Manager resolve xong.")
    public ApiResponse<GrnResponse> generateGrn(
            @PathVariable Long id,
            Authentication auth) {
        return receivingOrderService.generateGrn(id, extractUserId(auth));
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

    private Long extractWarehouseId(Authentication auth) {
        if (auth != null && auth.getDetails() instanceof Map) {
            Object raw = ((Map<?, ?>) auth.getDetails()).get("warehouseIds");
            if (raw instanceof java.util.List<?> list && !list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof Long)
                    return (Long) first;
                if (first instanceof Integer)
                    return ((Integer) first).longValue();
                if (first instanceof Number)
                    return ((Number) first).longValue();
            }
        }
        throw new RuntimeException("Cannot extract warehouseId from authentication");
    }
}
