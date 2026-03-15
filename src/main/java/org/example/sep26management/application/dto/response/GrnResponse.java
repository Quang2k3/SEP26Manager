package org.example.sep26management.application.dto.response;

import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrnResponse {
    @Schema(description = "ID phiếu nhập kho (GRN)", example = "1")
    private Long grnId;

    @Schema(description = "Mã phiếu (GRN Code)", example = "GRN-12345678")
    private String grnCode;

    @Schema(description = "ID phiếu nhận (Receiving Order ID)", example = "10")
    private Long receivingId;

    @Schema(description = "ID kho", example = "1")
    private Long warehouseId;

    @Schema(description = "Loại nguồn", example = "SUPPLIER")
    private String sourceType;

    @Schema(description = "ID nhà cung cấp", example = "5")
    private Long supplierId;
    private String supplierName;

    @Schema(description = "Mã chứng từ xuất/tham chiếu", example = "PO-20231015-01")
    private String sourceReferenceCode;

    @Schema(description = "Trạng thái (DRAFT, PENDING_APPROVAL, APPROVED, REJECTED)", example = "DRAFT")
    private String status;

    @Schema(description = "ID người tạo", example = "2")
    private Long createdBy;

    @Schema(description = "Thời gian tạo mới")
    private LocalDateTime createdAt;

    @Schema(description = "Thời gian cập nhật gần nhất")
    private LocalDateTime updatedAt;

    @Schema(description = "ID người duyệt", example = "5")
    private Long approvedBy;

    @Schema(description = "Thời gian duyệt")
    private LocalDateTime approvedAt;

    @Schema(description = "Ghi chú phiếu nhập kho", example = "Nhập kho lô hàng Apple")
    private String note;

    @Schema(description = "Danh sách sản phẩm nhập kho")
    private List<GrnItemResponse> items;
}