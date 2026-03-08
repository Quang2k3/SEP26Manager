package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.sep26management.application.dto.request.CreateIncidentRequest;
import org.example.sep26management.application.dto.request.RejectRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.IncidentResponse;
import org.example.sep26management.application.dto.response.PageResponse;
import org.example.sep26management.application.service.IncidentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/incidents")
@RequiredArgsConstructor
@Tag(name = "Incidents (Gate Check)", description = "Quản lý sự cố Gate Check. "
        + "Keeper tạo Incident khi Seal đứt/sai → Manager duyệt 'Cho phép dỡ hàng' hoặc 'Từ chối'.")
public class IncidentController {

    private final IncidentService incidentService;

    /** POST /v1/incidents — Keeper tạo sự cố */
    @PostMapping
    @Operation(summary = "Tạo sự cố Gate Check (Keeper)", description = "Keeper sử dụng khi phát hiện vấn đề về Seal xe tải. "
            + "Có thể đính kèm ảnh (attachmentId) và liên kết với Lệnh nhập hàng (receivingId).")
    public ApiResponse<IncidentResponse> create(
            @Valid @RequestBody CreateIncidentRequest request,
            Authentication auth) {
        return incidentService.createIncident(request, extractUserId(auth));
    }

    /** GET /v1/incidents?status=OPEN */
    @GetMapping
    @Operation(summary = "Danh sách sự cố", description = "Lấy danh sách sự cố.\n\n"
            + "**Data yêu cầu:** \n"
            + "- `Query.status` (Tùy chọn): Lọc theo trạng thái, ví dụ: OPEN, APPROVED, REJECTED.\n"
            + "- `Query.page` (Tùy chọn): Trang kết quả, mặc định 0.\n"
            + "- `Query.size` (Tùy chọn): Kích thước trang, mặc định 10.")
    public ApiResponse<PageResponse<IncidentResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return incidentService.listIncidents(status, page, size);
    }

    /** GET /v1/incidents/{id} */
    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết sự cố", description = "Xem chi tiết một sự cố (bao gồm link ảnh, ID người báo cáo, vv). \n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable id`: Mã Incident ID lấy từ danh sách.")
    public ApiResponse<IncidentResponse> get(@PathVariable Long id) {
        return incidentService.getIncident(id);
    }

    /** POST /v1/incidents/{id}/approve — Manager cho phép dỡ hàng */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Duyệt sự cố - Cho phép dỡ hàng (Manager)", description = "Manager duyệt incident → cho phép Keeper bắt đầu dỡ hàng. "
            + "Chỉ MANAGER mới được phép.")
    public ApiResponse<IncidentResponse> approve(
            @PathVariable Long id,
            Authentication auth) {
        return incidentService.approveIncident(id, extractUserId(auth));
    }

    /** POST /v1/incidents/{id}/reject — Manager từ chối nhận xe */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Từ chối dỡ hàng (Manager)", description = "Manager từ chối incident → xe tải không được phép dỡ hàng. "
            + "Yêu cầu cung cấp lý do.")
    public ApiResponse<IncidentResponse> reject(
            @PathVariable Long id,
            @RequestBody RejectRequest request,
            Authentication auth) {
        return incidentService.rejectIncident(id, request.getReason(), extractUserId(auth));
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
