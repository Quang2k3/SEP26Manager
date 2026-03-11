package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.sep26management.application.enums.IncidentCategory;
import org.example.sep26management.application.dto.request.CreateIncidentRequest;
import org.example.sep26management.application.dto.request.RejectRequest;
import org.example.sep26management.application.dto.request.ResolveIncidentRequest;
import org.example.sep26management.application.dto.request.ResolveShortageRequest;
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

    /** POST /v1/incidents/{id}/resolve-shortage — Manager xử lý sự cố thiếu hàng */
    @PostMapping("/{id}/resolve-shortage")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Xử lý sự cố thiếu hàng (Manager)", description = "Manager xử lý sự cố khi số lượng quét được ít hơn số lượng mong đợi "
            + "(thường được hệ thống tự động tạo khi hoàn tất đếm).\n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable id`: Mã Sự cố (Incident ID).\n"
            + "- `ResolveShortageRequest.resolution`: Chọn `CLOSE_SHORT` (Chốt thiếu, kết thúc đơn) hoặc `WAIT_BACKORDER` (Chờ giao bù, nhập từng phần).")
    public ApiResponse<IncidentResponse> resolveShortage(
            @PathVariable Long id,
            @Valid @RequestBody ResolveShortageRequest request,
            Authentication auth) {
        return incidentService.resolveShortage(id, request.getResolution(), extractUserId(auth));
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
