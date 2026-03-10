package org.example.sep26management.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceivingOrderRequest {

    @Schema(description = "Loại nguồn (Ví dụ: SUPPLIER, TRANSFER, RETURN)", example = "SUPPLIER")
    @NotBlank(message = "sourceType is required")
    private String sourceType;

    @Schema(description = "Mã chứng từ xuất/tham chiếu", example = "PO-20231015-01")
    private String sourceReferenceCode;

    @Schema(description = "Mã nhà cung cấp (Lấy từ API danh sách supplier)", example = "SUP001")
    private String supplierCode;

    @Schema(description = "ID kho chuyển tới (Nếu là Transfer)", example = "2")
    private Long sourceWarehouseId;

    @Schema(description = "Ghi chú ban đầu", example = "Xe tải 29C-12345 giao hàng lúc 10h")
    private String note;

    @Schema(description = "Danh sách các mặt hàng mong đợi (Expected Items)")
    @NotEmpty(message = "Items list cannot be empty")
    private List<ItemRequest> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemRequest {
        @Schema(description = "Mã SKU (SKU Code)", example = "SKU001")
        @NotBlank(message = "skuCode is required")
        private String skuCode;

        @Schema(description = "Số lượng mong đợi trên giấy tờ", example = "100")
        private BigDecimal expectedQty;
    }
}
