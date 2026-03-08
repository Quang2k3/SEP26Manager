package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.sep26management.application.dto.request.QcDecisionRequest;
import org.example.sep26management.application.dto.request.UpdateQcInspectionRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.PageResponse;
import org.example.sep26management.application.dto.response.QcInspectionResponse;
import org.example.sep26management.application.service.QcInspectionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/qc-inspections")
@RequiredArgsConstructor
@Tag(name = "QC Inspections", description = "Quản lý kiểm định chất lượng (QC). "
        + "LƯU Ý: Không có API tạo phiếu kiểm định riêng vì hệ thống TỰ ĐỘNG sinh ra phiếu QC Inspection (trạng thái PENDING) "
        + "khi luồng Scan Nhập Kho (Scan Event) ghi nhận hàng hóa bị lỗi (condition = FAIL kèm lý do rách, móp, v.v). "
        + "Quy trình: Scanner gửi FAIL event → Server tự tạo phiếu QC → QC xem danh sách chờ khám (list) → Click vào 1 phiếu để xem chi tiết bằng ID (get) → "
        + "Lập QC Report (thêm chi tiết, ảnh) → Manager duyệt phương án xử lý cuối cùng.")
public class QcInspectionController {

    private final QcInspectionService qcService;

    /** GET /v1/qc-inspections?status=PENDING */
    @GetMapping
    @Operation(summary = "Danh sách phiếu kiểm định QC", description = "Lấy danh sách các phiếu QC. Tham số `status` đóng vai trò là FILTER (bộ lọc).\n\n"
            + "**Data yêu cầu:** \n"
            + "- `Query.status` (Tùy chọn): Lọc theo trạng thái, ví dụ: PENDING, INSPECTED, DECIDED.\n"
            + "- `Query.page` (Tùy chọn): Trang kết quả, mặc định 0.\n"
            + "- `Query.size` (Tùy chọn): Kích thước trang, mặc định 10.\n\n"
            + "👉 **Kết quả:** Trả về danh sách. Kết quả bao gồm ID (`qcInspectionId`) của từng phiếu để QC có thể nhấn vào xem chi tiết.")
    public ApiResponse<PageResponse<QcInspectionResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return qcService.listInspections(status, page, size);
    }

    /** GET /v1/qc-inspections/{id} */
    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết phiếu kiểm định", description = "Đọc chi tiết sản phẩm lỗi và hình ảnh đính kèm để QC ra quyết định. \n\n"
            + "**Data yêu cầu:**\n"
            + "- `@PathVariable id`: Mã ID phiếu kiểm định (Hay gọi là **QC Inspection ID**), **LẤY TỪ** response của API danh sách lô hàng lỗi `GET /v1/qc-inspections` bên trên.")
    public ApiResponse<QcInspectionResponse> get(@PathVariable Long id) {
        return qcService.getInspection(id);
    }

    /** PUT /v1/qc-inspections/{id} — QC submits report */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('QC')")
    @Operation(summary = "QC nộp báo cáo kiểm định", description = "QC nhập thông tin chi tiết về lỗi, đính kèm hình ảnh. Sau khi nộp, trạng thái phiếu QC chuyển từ `PENDING` sang `INSPECTED`.\n\n"
            + "**Data yêu cầu:**\n"
            + "- `@PathVariable id`: Mã ID phiếu kiểm định (QC Inspection ID), **LẤY TỪ** response của API danh sách lô hàng lỗi `GET /v1/qc-inspections`.\n"
            + "- `@RequestBody UpdateQcInspectionRequest`: Dữ liệu báo cáo QC, bao gồm `note` (ghi chú) và `imageUrls` (danh sách URL ảnh).")
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

    /** POST /v1/qc-inspections/mock-data — Generate mock QC data */
    @PostMapping("/mock-data")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Tạo dữ liệu QC mẫu", description = "Tạo 5 phiếu kiểm định QC mẫu (PENDING) từ các lô hàng (Lot) có sẵn trong kho. Dùng để FE test giao diện.")
    public ApiResponse<java.util.List<QcInspectionResponse>> generateMockData(Authentication auth) {
        return qcService.generateMockData(extractUserId(auth));
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
