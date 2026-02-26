package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

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

    @NotNull(message = "Warehouse ID is required")
    private Long warehouseId;

    /**
     * Min stock threshold — positive integer [BR-SKU-07]
     */
    @Min(value = 0, message = "Min threshold must be a non-negative number")
    private BigDecimal minQty;

    /**
     * Max stock threshold — positive integer, must be > minQty [BR-SKU-07]
     */
    @Min(value = 1, message = "Max threshold must be a positive number")
    private BigDecimal maxQty;

    private BigDecimal reorderPoint;

    private BigDecimal reorderQty;

    private String note;
}