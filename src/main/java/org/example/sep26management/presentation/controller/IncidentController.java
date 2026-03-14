package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.sep26management.application.enums.IncidentCategory;
import org.example.sep26management.application.dto.request.CreateIncidentRequest;
import org.example.sep26management.application.dto.request.RejectRequest;
import org.example.sep26management.application.dto.request.ResolveIncidentRequest;

import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.IncidentResponse;
import org.example.sep26management.application.dto.response.PageResponse;
import org.example.sep26management.application.service.IncidentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/incidents")
@RequiredArgsConstructor
@Tag(name = "Incidents (Reports)", description = "Quản lý các loại báo cáo sự cố. Bao gồm: Gate Check (Seal/Container) và Báo cáo chất lượng sản phẩm (Quality/Quantity).")
public class IncidentController {

    private final IncidentService incidentService;

    /** POST /v1/incidents — Keeper tạo sự cố */
    @PostMapping
    @Operation(summary = "Tạo báo cáo sự cố (Keeper)",
            description = "Keeper tạo báo cáo sự cố thủ công. Có 2 loại:\n\n"
                    + "## category = GATE (Gate Check)\n"
                    + "Phát hiện seal/container bị hỏng **trước khi dỡ hàng**.\n"
                    + "→ `receivingId` = **null** (chưa có phiếu nhập)\n"
                    + "→ Manager cần approve (cho dỡ) hoặc reject (không cho dỡ)\n\n"
                    + "**Ví dụ Gate Check:**\n"
                    + "```json\n"
                    + "{\n"
                    + "  \"warehouseId\": 1,\n"
                    + "  \"incidentType\": \"SEAL_BROKEN\",\n"
                    + "  \"category\": \"GATE\",\n"
                    + "  \"description\": \"Seal container bị rách, không nguyên vẹn\"\n"
                    + "}\n"
                    + "```\n\n"
                    + "## category = QUALITY\n"
                    + "Báo cáo hư hỏng sản phẩm **trong/sau khi dỡ hàng**.\n"
                    + "→ `receivingId` = ID phiếu nhập (LẤY TỪ `GET /v1/receiving-orders`)\n\n"
                    + "**Ví dụ Quality:**\n"
                    + "```json\n"
                    + "{\n"
                    + "  \"warehouseId\": 1,\n"
                    + "  \"incidentType\": \"DAMAGE\",\n"
                    + "  \"category\": \"QUALITY\",\n"
                    + "  \"description\": \"2 thùng bị móp\",\n"
                    + "  \"receivingId\": 15,\n"
                    + "  \"items\": [\n"
                    + "    { \"skuId\": 1, \"damagedQty\": 2, \"reasonCode\": \"TORN_PACKAGING\", \"note\": \"Thùng móp\" }\n"
                    + "  ]\n"
                    + "}\n"
                    + "```\n\n"
                    + "**incidentType values:**\n"
                    + "- `SEAL_BROKEN`, `SEAL_MISMATCH`, `PACKAGING_DAMAGE` → dùng cho GATE\n"
                    + "- `DAMAGE`, `SHORTAGE`, `OVERAGE`, `OTHER` → dùng cho QUALITY")
    public ApiResponse<IncidentResponse> create(
            @Valid @RequestBody CreateIncidentRequest request,
            Authentication auth) {
        return incidentService.createIncident(request, extractUserId(auth));
    }

    /** GET /v1/incidents?status=OPEN&category=GATE */
    @GetMapping
    @Operation(summary = "Danh sách báo cáo sự cố",
            description = "Lấy danh sách sự cố. Lọc theo `category` và `status`.\n\n"
                    + "## Phân biệt 2 loại màn hình:\n\n"
                    + "| Màn hình | Gọi API với |\n"
                    + "|---|---|\n"
                    + "| Gate Check (Seal/Cont) | `category=GATE` |\n"
                    + "| Chất lượng (Thừa/Thiếu/Hỏng) | `category=QUALITY` |\n\n"
                    + "**Status values:** `OPEN`, `APPROVED`, `REJECTED`, `RESOLVED`\n\n"
                    + "👉 Lấy `incidentId` từ response → dùng cho approve/reject/resolve-discrepancy")
    public ApiResponse<PageResponse<IncidentResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) IncidentCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return incidentService.listIncidents(status, category, page, size);
    }

    /** GET /v1/incidents/{id} */
    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết sự cố",
            description = "Xem chi tiết sự cố bao gồm danh sách items sai lệch.\n\n"
                    + "**Data yêu cầu:**\n"
                    + "- `@PathVariable id`: Incident ID. LẤY TỪ: `GET /v1/incidents` → `incidentId`\n\n"
                    + "**Response chứa:**\n"
                    + "- `items[].incidentItemId`: Dùng cho `resolve-discrepancy` API\n"
                    + "- `items[].reasonCode`: `SHORTAGE` hoặc `OVERAGE` (dùng để hiện đúng action cho FE)\n"
                    + "- `items[].expectedQty`, `items[].actualQty`: Để hiển thị bảng so sánh\n"
                    + "- `items[].damagedQty`: Số lượng sai lệch (= |expected - actual|)")
    public ApiResponse<IncidentResponse> get(@PathVariable Long id) {
        return incidentService.getIncident(id);
    }

    /** POST /v1/incidents/{id}/approve — Manager cho phép dỡ hàng */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Duyệt sự cố — Cho phép dỡ hàng (Manager)",
            description = "**Chỉ dùng cho Gate Check (GATE category).**\n\n"
                    + "Manager xem báo cáo seal/container → quyết định vẫn cho Keeper dỡ hàng.\n\n"
                    + "**Data yêu cầu:**\n"
                    + "- `@PathVariable id`: Incident ID. LẤY TỪ: `GET /v1/incidents?category=GATE&status=OPEN` → `incidentId`\n"
                    + "- Body: Không cần\n\n"
                    + "→ Status: `OPEN` → `APPROVED`\n"
                    + "→ Keeper được phép bắt đầu dỡ hàng")
    public ApiResponse<IncidentResponse> approve(
            @PathVariable Long id,
            Authentication auth) {
        return incidentService.approveIncident(id, extractUserId(auth));
    }

    /** POST /v1/incidents/{id}/reject — Manager từ chối nhận xe */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Từ chối dỡ hàng (Manager)",
            description = "**Chỉ dùng cho Gate Check (GATE category).**\n\n"
                    + "Manager từ chối → xe tải không được dỡ hàng.\n\n"
                    + "**Data yêu cầu:**\n"
                    + "- `@PathVariable id`: Incident ID. LẤY TỪ: `GET /v1/incidents?category=GATE&status=OPEN` → `incidentId`\n\n"
                    + "**Ví dụ request body:**\n"
                    + "```json\n"
                    + "{ \"reason\": \"Seal container bị phá, nghi ngờ hàng bị đánh tráo\" }\n"
                    + "```\n\n"
                    + "→ Status: `OPEN` → `REJECTED`")
    public ApiResponse<IncidentResponse> reject(
            @PathVariable Long id,
            @RequestBody RejectRequest request,
            Authentication auth) {
        return incidentService.rejectIncident(id, request.getReason(), extractUserId(auth));
    }

    /** POST /v1/incidents/{id}/resolve — Manager xử lý sự cố chất lượng */
    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Xử lý sự cố chất lượng — hàng hỏng (Manager)",
            description = "**Dùng cho QC damage (hàng hỏng vật lý)** — KHÔNG phải thừa/thiếu.\n\n"
                    + "Manager quyết định số lượng PASS (nhập kho), RETURN (trả NCC), SCRAP (huỷ bỏ) cho từng item hỏng.\n\n"
                    + "**Ví dụ request body:**\n"
                    + "```json\n"
                    + "{\n"
                    + "  \"resolutions\": [\n"
                    + "    { \"incidentItemId\": 5, \"action\": \"PASS\", \"quantity\": 3 },\n"
                    + "    { \"incidentItemId\": 5, \"action\": \"RETURN\", \"quantity\": 2 }\n"
                    + "  ],\n"
                    + "  \"note\": \"3 thùng còn tốt, 2 thùng bị móp trả NCC\"\n"
                    + "}\n"
                    + "```\n\n"
                    + "**Actions:** `PASS` (nhập kho), `RETURN` (trả NCC), `SCRAP` / `DOWNGRADE` (huỷ/hạ cấp)\n\n"
                    + "→ Để xử lý **thừa/thiếu số lượng**, dùng `POST /v1/incidents/{id}/resolve-discrepancy` thay thế")
    public ApiResponse<IncidentResponse> resolve(
            @PathVariable Long id,
            @Valid @RequestBody ResolveIncidentRequest request,
            Authentication auth) {
        return incidentService.resolveIncident(id, request, extractUserId(auth));
    }

    /** POST /v1/incidents/{id}/resolve-discrepancy — Manager xử lý sai lệch số lượng */
    @PostMapping("/{id}/resolve-discrepancy")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Xử lý sự cố sai lệch số lượng (Manager)",
            description = "Manager xử lý sai lệch số lượng (thừa/thiếu) **từng item riêng biệt**.\n\n"
                    + "## Quy trình\n"
                    + "1. Gọi `GET /v1/incidents/{id}` để lấy danh sách items kèm `incidentItemId` và `reasonCode`\n"
                    + "2. Dựa vào `reasonCode` để hiển thị lựa chọn phù hợp cho Manager\n"
                    + "3. Gửi kết quả quyết định xuống API này\n\n"
                    + "## Actions theo loại sai lệch\n\n"
                    + "| reasonCode | Actions khả dụng | Ý nghĩa |\n"
                    + "|---|---|---|\n"
                    + "| `SHORTAGE` | `CLOSE_SHORT` | Chốt thiếu, chấp nhận số lượng đã nhận |\n"
                    + "| `SHORTAGE` | `WAIT_BACKORDER` | Chờ NCC giao bù phần thiếu |\n"
                    + "| `OVERAGE` | `ACCEPT` | Nhận hàng thừa, nhập kho tất cả |\n"
                    + "| `OVERAGE` | `RETURN` | Trả hàng thừa cho NCC |\n\n"
                    + "## Ví dụ đầy đủ\n\n"
                    + "**Bước 1**: `GET /v1/incidents/9` trả ra:\n"
                    + "```json\n"
                    + "\"items\": [\n"
                    + "  { \"incidentItemId\": 11, \"skuCode\": \"SKU001\", \"expectedQty\": 5, \"actualQty\": 4, \"reasonCode\": \"SHORTAGE\" },\n"
                    + "  { \"incidentItemId\": 12, \"skuCode\": \"SKU002\", \"expectedQty\": 5, \"actualQty\": 8, \"reasonCode\": \"OVERAGE\" },\n"
                    + "  { \"incidentItemId\": 13, \"skuCode\": \"SKU003\", \"expectedQty\": 5, \"actualQty\": 3, \"reasonCode\": \"SHORTAGE\" }\n"
                    + "]\n"
                    + "```\n\n"
                    + "**Bước 2**: FE hiển thị cho Manager chọn action cho từng item, "
                    + "ví dụ: SKU001 thiếu 1 → chọn 'Chốt thiếu', SKU002 thừa 3 → chọn 'Trả hàng'\n\n"
                    + "**Bước 3**: Gửi request:\n"
                    + "```json\n"
                    + "{\n"
                    + "  \"items\": [\n"
                    + "    { \"incidentItemId\": 11, \"action\": \"CLOSE_SHORT\" },\n"
                    + "    { \"incidentItemId\": 12, \"action\": \"RETURN\" },\n"
                    + "    { \"incidentItemId\": 13, \"action\": \"WAIT_BACKORDER\" }\n"
                    + "  ],\n"
                    + "  \"note\": \"Đã liên hệ NCC về phần thiếu SKU003\"\n"
                    + "}\n"
                    + "```\n\n"
                    + "**Kết quả**: Incident → RESOLVED, Đơn hàng → SUBMITTED (QC kiểm tra).")
    public ApiResponse<IncidentResponse> resolveDiscrepancy(
            @PathVariable Long id,
            @Valid @RequestBody org.example.sep26management.application.dto.request.ResolveDiscrepancyRequest request,
            Authentication auth) {
        return incidentService.resolveDiscrepancy(id, request, extractUserId(auth));
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
}
