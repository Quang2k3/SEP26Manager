package org.example.sep26management.application.dto.response;

import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrnItemResponse {
    @Schema(description = "ID sản phẩm trong GRN", example = "1")
    private Long grnItemId;

    @Schema(description = "ID SKU", example = "100")
    private Long skuId;

    @Schema(description = "Mã SKU", example = "IP14-PRO")
    private String skuCode;

    @Schema(description = "Tên SKU", example = "iPhone 14 Pro")
    private String skuName;

    @Schema(description = "Số lượng nhập kho (đã trừ hàng lỗi / bị từ chối)", example = "10.0")
    private BigDecimal quantity;

    @Schema(description = "Số lô (Lot Number)", example = "LOT-20231015")
    private String lotNumber;

    @Schema(description = "Ngày sản xuất (Manufacture Date)", example = "2023-10-15")
    private java.time.LocalDate manufactureDate;

    @Schema(description = "Ngày hết hạn (Expiry Date)", example = "2024-10-15")
    private java.time.LocalDate expiryDate;
}
