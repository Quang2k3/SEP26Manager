package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QcInspectionResponse {

    @Schema(description = "ID của phiếu kiểm định QC", example = "1")
    private Long inspectionId;
    @Schema(description = "ID của kho bãi", example = "100")
    private Long warehouseId;
    @Schema(description = "ID của lô hàng (Lot) bị lỗi", example = "50")
    private Long lotId;
    @Schema(description = "Mã phiếu kiểm định", example = "QC-20231001-001")
    private String inspectionCode;
    @Schema(description = "Trạng thái phiếu QC", example = "PENDING", allowableValues = { "PENDING", "INSPECTED",
            "DECIDED" })
    private String status;

    // Lot info
    @Schema(description = "Mã lô hàng", example = "LOT-12345")
    private String lotNumber;
    @Schema(description = "ID của sản phẩm (SKU)", example = "20")
    private Long skuId;
    @Schema(description = "Mã sản phẩm", example = "SKU-IPHONE15")
    private String skuCode;
    @Schema(description = "Tên sản phẩm", example = "iPhone 15 Pro Max")
    private String skuName;

    // Inspection details
    @Schema(description = "ID của nhân viên QC đã kiểm tra", example = "5")
    private Long inspectedBy;
    @Schema(description = "Tên của nhân viên QC đã kiểm tra", example = "Nguyễn Văn QC")
    private String inspectedByName;
    @Schema(description = "Thời gian kiểm tra", example = "2023-10-01T15:30:00")
    private LocalDateTime inspectedAt;
    @Schema(description = "Ghi chú của nhân viên QC", example = "Hộp bị ướt, sản phẩm bên trong có dấu hiệu ẩm mạch.")
    private String remarks;
    @Schema(description = "ID hình ảnh đính kèm", example = "105")
    private Long attachmentId;

    // Manager decision
    @Schema(description = "Quyết định xử lý của Manager", example = "RETURN", allowableValues = { "SCRAP", "RETURN",
            "DOWNGRADE" })
    private String decision; // SCRAP, RETURN, DOWNGRADE

    @Schema(description = "Thời gian tạo phiếu", example = "2023-10-01T10:00:00")
    private LocalDateTime createdAt;
}
