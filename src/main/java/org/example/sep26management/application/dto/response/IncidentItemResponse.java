package org.example.sep26management.application.dto.response;

import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentItemResponse {

    @Schema(description = "ID chi tiết sự cố", example = "1")
    private Long incidentItemId;

    @Schema(description = "ID Sản phẩm bị lỗi", example = "150")
    private Long skuId;

    @Schema(description = "Tên Sản phẩm bị lỗi", example = "iPhone 15 Pro Max")
    private String skuName;

    @Schema(description = "Mã SKU", example = "SKU-IP15")
    private String skuCode;

    @Schema(description = "Số lượng bị lỗi/thiếu", example = "5.0")
    private BigDecimal damagedQty;

    @Schema(description = "Số lượng dự kiến", example = "10.0")
    private BigDecimal expectedQty;

    @Schema(description = "Số lượng thực tế (quét được)", example = "5.0")
    private BigDecimal actualQty;

    @Schema(description = "Mã lỗi chi tiết", example = "TORN_PACKAGING")
    private String reasonCode;

    @Schema(description = "Ghi chú thêm", example = "Thùng bị móp méo")
    private String note;
}
