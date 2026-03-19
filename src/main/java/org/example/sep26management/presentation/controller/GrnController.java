package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.sep26management.application.dto.request.RejectRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.GrnResponse;
import org.example.sep26management.application.dto.response.PageResponse;
import org.example.sep26management.application.service.GrnService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/grns")
@RequiredArgsConstructor
@Tag(name = "Goods Receipt Note (GRN)", description = "Quản lý Phiếu nhập kho chính thức (GRN). GRN được tạo tự động từ Receiving Order sau khi kiểm đếm hoặc xử lý sự cố hoàn tất.")
public class GrnController {

    private final GrnService grnService;

    @GetMapping
    @Operation(summary = "Danh sách Phiếu nhập kho (List GRN)",
            description = "Lấy danh sách GRN (Good Receipt Note) đã được generate từ Receiving Order.\n\n"
                    + "**Lọc theo status:**\n"
                    + "- `PENDING_APPROVAL`: GRN chờ Manager duyệt\n"
                    + "- `APPROVED`: GRN đã duyệt, sẵn sàng nhập kho\n"
                    + "- `REJECTED`: GRN bị từ chối\n"
                    + "- `POSTED`: GRN đã nhập kho (đã tạo Putaway Task)\n\n"
                    + "**Data yêu cầu:**\n"
                    + "- `Query.status` (Tùy chọn): Lọc theo trạng thái\n"
                    + "- `Query.page` (Tùy chọn): Trang, mặc định 0\n"
                    + "- `Query.size` (Tùy chọn): Kích thước, mặc định 10\n\n"
                    + "👉 Lấy `grnId` từ response để dùng cho các API approve/reject/post bên dưới")
    public ApiResponse<PageResponse<GrnResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        Long warehouseId = extractWarehouseId(auth);
        return grnService.listGrns(warehouseId, status, page, size);
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Keeper gửi GRN cho Manager duyệt",
            description = "Chuyển ReceivingOrder.status từ GRN_CREATED → PENDING_APPROVAL để Manager thấy trong dashboard.")
    public ApiResponse<GrnResponse> submit(@PathVariable Long id) {
        return grnService.submitToManager(id);
    }

    @GetMapping("/by-receiving/{receivingId}")
    @Operation(summary = "Lấy GRN theo Receiving Order", description = "Keeper dùng để kiểm tra GRN đã được tạo từ Receiving Order chưa.\n\n"
            + "**Data yêu cầu:**\n"
            + "- `@PathVariable receivingId`: ID của Receiving Order.")
    public ApiResponse<GrnResponse> getByReceivingId(@PathVariable Long receivingId) {
        return grnService.getByReceivingId(receivingId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết GRN",
            description = "Xem chi tiết 1 GRN bao gồm danh sách SKU với số lượng nhập kho, lot number, ngày sản xuất, hạn sử dụng.\n\n"
                    + "**Data yêu cầu:**\n"
                    + "- `@PathVariable id`: GRN ID. LẤY TỪ: `GET /v1/grns` → `grnId`\n\n"
                    + "**Response chứa:**\n"
                    + "- `items[].skuCode`, `items[].quantity`: SKU và số lượng nhập kho\n"
                    + "- `items[].lotNumber`, `items[].expiryDate`: Thông tin lot (tự động generate)")
    public ApiResponse<GrnResponse> get(@PathVariable Long id) {
        return grnService.getGrn(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Duyệt phiếu GRN (Manager)",
            description = "Manager xác nhận GRN hợp lệ. Chuyển từ `PENDING_APPROVAL` → `APPROVED`.\n\n"
                    + "**Data yêu cầu:**\n"
                    + "- `@PathVariable id`: GRN ID. LẤY TỪ: `GET /v1/grns?status=PENDING_APPROVAL` → `grnId`\n"
                    + "- Body: Không cần\n\n"
                    + "👉 **Bước tiếp theo:** Gọi `POST /v1/grns/{id}/post` để thực thi nhập kho")
    public ApiResponse<GrnResponse> approve(@PathVariable Long id, Authentication auth) {
        return grnService.approve(id, extractUserId(auth));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Từ chối phiếu GRN (Manager)",
            description = "Manager từ chối GRN. Chuyển thành `REJECTED`.\n\n"
                    + "**Data yêu cầu:**\n"
                    + "- `@PathVariable id`: GRN ID. LẤY TỪ: `GET /v1/grns` → `grnId`\n"
                    + "- `Body.reason` (String): Lý do từ chối\n\n"
                    + "**Ví dụ request body:**\n"
                    + "```json\n"
                    + "{ \"reason\": \"Số lượng không khớp với chứng từ\" }\n"
                    + "```")
    public ApiResponse<GrnResponse> reject(
            @PathVariable Long id,
            @RequestBody RejectRequest request,
            Authentication auth) {
        return grnService.reject(id, request.getReason(), extractUserId(auth));
    }

    @PostMapping("/{id}/post")
    @Operation(summary = "Thực thi Nhập Kho — Post GRN",
            description = "Cất hàng vào Trạm Chờ (Staging), ghi nhận Inventory Transaction & **TỰ ĐỘNG tạo Putaway Task**.\n\n"
                    + "Chuyển GRN từ `APPROVED` → `POSTED`.\n\n"
                    + "**Data yêu cầu:**\n"
                    + "- `@PathVariable id`: GRN ID. LẤY TỪ: `GET /v1/grns?status=APPROVED` → `grnId`\n"
                    + "- Body: Không cần\n\n"
                    + "**Sau khi post thành công:**\n"
                    + "- Hệ thống tạo Putaway Task tự động\n"
                    + "- FE chuyển sang màn hình Putaway: `GET /v1/putaway-tasks?status=OPEN`\n"
                    + "- Hoặc lấy task theo GRN: `GET /v1/putaway-tasks/grn/{grnId}`")
    public ApiResponse<GrnResponse> post(@PathVariable Long id, Authentication auth) {
        return grnService.post(id, extractUserId(auth));
    }

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
        // Trả null nếu không có warehouseId — service fallback về findAll
        if (auth != null && auth.getDetails() instanceof Map) {
            @SuppressWarnings("unchecked")
            Object raw = ((Map<?, ?>) auth.getDetails()).get("warehouseIds");
            if (raw instanceof java.util.List<?> list && !list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof Long) return (Long) first;
                if (first instanceof Integer) return ((Integer) first).longValue();
                if (first instanceof Number) return ((Number) first).longValue();
                if (first != null) {
                    try { return Long.parseLong(first.toString()); } catch (NumberFormatException ignored) {}
                }
            }
        }
        return null;
    }
}