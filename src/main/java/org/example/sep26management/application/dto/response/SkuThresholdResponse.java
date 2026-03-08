package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SkuThresholdResponse {

    @Schema(description = "ID Config Mức Tồn Theo Kho", example = "1")
    private Long thresholdId;
    @Schema(description = "SKU ID", example = "100")
    private Long skuId;
    @Schema(description = "Mã SP", example = "SKU-IP15")
    private String skuCode;
    @Schema(description = "Tên SP", example = "Iphone 15 Pro")
    private String skuName;
    @Schema(description = "Kho Đang Thiết Lập ID", example = "1")
    private Long warehouseId;
    @Schema(description = "Mức Trữ Tối Thiểu (Cảnh báo Thấp)", example = "10.0")
    private BigDecimal minQty;
    @Schema(description = "Mức Trữ Tối Đa (Cảnh Báo Vượt Trần Kho)", example = "1000.0")
    private BigDecimal maxQty;
    @Schema(description = "Điểm Báo Mua Thêm (Reorder Point)", example = "50.0")
    private BigDecimal reorderPoint;
    @Schema(description = "Số lượng mua bù kiến nghị (Reorder Qty)", example = "200.0")
    private BigDecimal reorderQty;
    @Schema(description = "Hoạt Đồng", example = "true")
    private Boolean active;
    @Schema(description = "Notes bổ sung", example = "Chỉ tiêu nhập đợt 1")
    private String note;
    @Schema(description = "Cập nhật ngày", example = "2026-03-08T10:00:00")
    private LocalDateTime updatedAt;
}