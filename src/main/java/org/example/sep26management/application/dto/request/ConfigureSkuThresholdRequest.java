package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * UC-B07: Configure SKU Threshold
 * BR-SKU-07: min và max phải là số nguyên dương, min < max
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfigureSkuThresholdRequest {

    /**
     * Min stock threshold — positive integer [BR-SKU-07]
     */
    @Schema(description = "Ngưỡng tồn dưới tối thiểu", example = "10.0")
    @Min(value = 0, message = "Min threshold must be a non-negative number")
    private BigDecimal minQty;

    /**
     * Max stock threshold — positive integer, must be > minQty [BR-SKU-07]
     */
    @Schema(description = "Ngưỡng tồn trên tối đa", example = "100.0")
    @Min(value = 1, message = "Max threshold must be a positive number")
    private BigDecimal maxQty;

    @Schema(description = "Điểm đặt hàng lại", example = "20.0")
    private BigDecimal reorderPoint;

    @Schema(description = "Số lượng đặt hàng lại", example = "80.0")
    private BigDecimal reorderQty;

    @Schema(description = "Ghi chú thêm", example = "Hàng bán chạy tháng 10")
    private String note;
}