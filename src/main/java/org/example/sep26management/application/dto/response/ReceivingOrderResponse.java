package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReceivingOrderResponse {

    @Schema(description = "ID phiếu nhập", example = "1")
    private Long receivingId;

    @Schema(description = "Mã phiếu nhập (Sinh tự động)", example = "RCV-200501")
    private String receivingCode;

    @Schema(description = "Trạng thái (PENDING, APPROVED, REJECTED, POSTED)", example = "PENDING")
    private String status;

    @Schema(description = "ID Kho", example = "1")
    private Long warehouseId;

    @Schema(description = "Tên Kho", example = "Kho Chính Hà Nội")
    private String warehouseName;

    @Schema(description = "ID Nhà Cung Cấp", example = "5")
    private Long supplierId;

    @Schema(description = "Tên Nhà Cung Cấp", example = "Apple Inc.")
    private String supplierName;

    @Schema(description = "Loại nhập: SUPPLIER, TRANSFER, RETURN", example = "SUPPLIER")
    private String sourceType;

    @Schema(description = "Mã chứng từ liên kết: VD số hợp đồng, PO", example = "PO-2023-01")
    private String sourceReferenceCode;

    @Schema(description = "Ghi chú lúc tạo phiếu", example = "Nhập lô Iphone mới")
    private String note;

    @Schema(description = "Nhân viên scan/tạo phiếu", example = "10")
    private Long createdBy;

    @Schema(description = "Tên NV tạo", example = "Bui Quang")
    private String createdByName;

    @Schema(description = "Thời gian tạo", example = "2026-03-08T10:00:00")
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Schema(description = "Tổng số DÒNG sản phẩm", example = "2")
    private Integer totalLines;
    @Schema(description = "TỔNG số lượng sản phẩm THỰC Nhận", example = "150.0")
    private BigDecimal totalQty;

    @Schema(description = "TỔNG số lượng sản phẩm DỰ KIẾN ban đầu", example = "150.0")
    private BigDecimal totalExpectedQty;

    @Schema(description = "Danh sách chi tiết từng lô hàng nhập")
    private List<ReceivingItemResponse> items;
}
