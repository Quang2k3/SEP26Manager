package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.sep26management.application.dto.request.QcDecisionRequest;
import org.example.sep26management.application.dto.request.UpdateQcInspectionRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.QcInspectionResponse;
import org.example.sep26management.application.service.QcInspectionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/qc-inspections")
@RequiredArgsConstructor
@Tag(name = "QC Inspections", description = "Quản lý kiểm định chất lượng. "
        + "QC xem danh sách hàng chờ kiểm tra → Lập QC Report → Manager duyệt phương án xử lý.")
public class QcInspectionController {

    private final QcInspectionService qcService;

    /** GET /v1/qc-inspections?status=PENDING */
    @GetMapping
    @Operation(summary = "Danh sách kiểm định QC", description = "Lấy danh sách QC inspections. Lọc theo status: PENDING (chờ QC), INSPECTED (đã kiểm), DECIDED (Manager đã quyết định).")
    public ApiResponse<List<QcInspectionResponse>> list(
            @RequestParam(required = false) String status) {
        return qcService.listInspections(status);
    }

    /** GET /v1/qc-inspections/{id} */
    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết kiểm định QC")
    public ApiResponse<QcInspectionResponse> get(@PathVariable Long id) {
        return qcService.getInspection(id);
    }

    /** PUT /v1/qc-inspections/{id} — QC submits report */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('QC')")
    @Operation(summary = "Lưu Báo cáo QC (QC role)", description = "QC nộp kết quả kiểm tra: ghi chú chi tiết, ảnh chụp hư hỏng. "
            + "Chuyển status từ PENDING → INSPECTED.")
    public ApiResponse<QcInspectionResponse> submitReport(
            @PathVariable Long id,
            @RequestBody UpdateQcInspectionRequest request,
            Authentication auth) {
        return qcService.submitReport(id, request, extractUserId(auth));
    }

    /** POST /v1/qc-inspections/{id}/decide — Manager makes final decision */
    @PostMapping("/{id}/decide")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Ra quyết định xử lý (Manager)", description = "Manager chọn phương án: SCRAP (Tiêu hủy), RETURN (Trả hàng), DOWNGRADE (Thanh lý). "
            + "Nếu RETURN, hệ thống tự sinh Lệnh xuất trả hàng.")
    public ApiResponse<QcInspectionResponse> decide(
            @PathVariable Long id,
            @Valid @RequestBody QcDecisionRequest request,
            Authentication auth) {
        return qcService.makeDecision(id, request, extractUserId(auth));
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
