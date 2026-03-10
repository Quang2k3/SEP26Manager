package org.example.sep26management.application.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceivingItemResponse {

    @Schema(description = "ID Dòng sản phẩm", example = "1")
    private Long receivingItemId;

    @Schema(description = "ID Sản phẩm", example = "150")
    private Long skuId;

    @Schema(description = "Mã SKU", example = "SKU-IP15")
    private String skuCode;

    @Schema(description = "Tên Sản Phẩm", example = "iPhone 15 Pro Max")
    private String skuName;

    @Schema(description = "Đơn vị tính", example = "Cái")
    private String unit;

    @Schema(description = "Số lượng", example = "10.0")
    private BigDecimal receivedQty;

    @Schema(description = "Mã Lô", example = "LOT-2023-A")
    private String lotNumber;

    @Schema(description = "Ngày hết hạn (Nếu có)", example = "2025-12-31")
    private LocalDate expiryDate;

    @Schema(description = "Ngày sản xuất (Nếu có)", example = "2023-10-01")
    private LocalDate manufactureDate;

    @Schema(description = "Ghi chú thêm", example = "Hàng đợt 1")
    private String note;

    @Schema(description = "Số lượng mong đợi", example = "10.0")
    private BigDecimal expectedQty;

    @Schema(description = "Tình trạng quét (PASS = Hàng tốt, FAIL = Hàng lỗi)", example = "PASS", allowableValues = {
            "PASS", "FAIL" })
    private String condition;

    @Schema(description = "Mã lý do lỗi (Nếu condition = FAIL)", example = "DENTED")
    private String reasonCode;
}
