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
    @Operation(summary = "Tạo báo cáo sự cố (Keeper)", description = "Keeper tạo báo cáo sự cố. \n"
            + "- Nếu `category=GATE`: Báo cáo các vấn đề về Seal, thùng container (trước khi dỡ hàng).\n"
            + "- Nếu `category=QUALITY`: Báo cáo thừa thiếu, hư hỏng sản phẩm (trong/sau khi dỡ hàng).")
    public ApiResponse<IncidentResponse> create(
            @Valid @RequestBody CreateIncidentRequest request,
            Authentication auth) {
        return incidentService.createIncident(request, extractUserId(auth));
    }

    /** GET /v1/incidents?status=OPEN&category=GATE */
    @GetMapping
    @Operation(summary = "Danh sách báo cáo sự cố", description = "Lấy danh sách các báo cáo sự cố.\n\n"
            + "**Hiển thị theo loại phiếu:** \n"
            + "- Truyền `category=GATE` để lấy danh sách 'Phiếu báo cáo Gate Check' (Seal/Container).\n"
            + "- Truyền `category=QUALITY` để lấy danh sách 'Phiếu báo cáo chất lượng' (Thừa thiếu/Hư hỏng SKU).\n\n"
            + "**Data yêu cầu:** \n"
            + "- `Query.status` (Tùy chọn): OPEN, APPROVED, REJECTED, RESOLVED.\n"
            + "- `Query.category` (Tùy chọn): GATE, QUALITY.\n"
            + "- `Query.page` (Tùy chọn): Trang mặc định 0.\n"
            + "- `Query.size` (Tùy chọn): Kích thước mặc định 10.")
    public ApiResponse<PageResponse<IncidentResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) IncidentCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return incidentService.listIncidents(status, category, page, size);
    }

    /** GET /v1/incidents/{id} */
    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết sự cố", description = "Xem chi tiết một sự cố (bao gồm link ảnh, ID người báo cáo, vv). \n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable id`: Mã Sự cố (Incident ID). LẤY TỪ: attribute `id` trong mảng kết quả của API danh sách `GET /v1/incidents` hoặc kết quả trả về khi tạo mới sự cố.")
    public ApiResponse<IncidentResponse> get(@PathVariable Long id) {
        return incidentService.getIncident(id);
    }

    /** POST /v1/incidents/{id}/approve — Manager cho phép dỡ hàng */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Duyệt sự cố - Cho phép dỡ hàng (Manager)", description = "Manager duyệt incident → cho phép Keeper bắt đầu dỡ hàng. "
            + "Chỉ MANAGER mới được phép.\n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable id`: Mã Sự cố (Incident ID). LẤY TỪ: attribute `id` của API danh sách sự cố.")
    public ApiResponse<IncidentResponse> approve(
            @PathVariable Long id,
            Authentication auth) {
        return incidentService.approveIncident(id, extractUserId(auth));
    }

    /** POST /v1/incidents/{id}/reject — Manager từ chối nhận xe */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Từ chối dỡ hàng (Manager)", description = "Manager từ chối incident → xe tải không được phép dỡ hàng. "
            + "Yêu cầu cung cấp lý do.\n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable id`: Mã Sự cố (Incident ID). LẤY TỪ: attribute `id` của API danh sách sự cố.")
    public ApiResponse<IncidentResponse> reject(
            @PathVariable Long id,
            @RequestBody RejectRequest request,
            Authentication auth) {
        return incidentService.rejectIncident(id, request.getReason(), extractUserId(auth));
    }

    /** POST /v1/incidents/{id}/resolve — Manager xử lý sự cố chất lượng */
    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Xử lý sự cố chất lượng (Manager)", description = "Manager xử lý sự cố chất lượng sau khi dỡ hàng, quyết định số lượng Pass, Return, Scrap cho từng item lỗi.\n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable id`: Mã Sự cố (Incident ID). LẤY TỪ: attribute `id` của API danh sách sự cố.\n"
            + "- `ResolveIncidentRequest.resolutions[].incidentItemId`: LẤY TỪ: attribute `items[].incidentItemId` khi gọi chi tiết sự cố (`GET /v1/incidents/{id}`).")
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
