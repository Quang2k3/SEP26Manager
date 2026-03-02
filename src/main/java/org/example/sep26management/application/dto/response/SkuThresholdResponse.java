package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SkuThresholdResponse {

    private Long thresholdId;
    private Long skuId;
    private String skuCode;
    private String skuName;
    private Long warehouseId;
    private BigDecimal minQty;
    private BigDecimal maxQty;
    private BigDecimal reorderPoint;
    private BigDecimal reorderQty;
    private Boolean active;
    private String note;
    private LocalDateTime updatedAt;
}