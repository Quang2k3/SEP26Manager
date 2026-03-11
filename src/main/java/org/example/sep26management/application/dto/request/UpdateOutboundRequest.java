package org.example.sep26management.application.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UpdateOutboundRequest {

    // ─── SALES_ORDER ───────────────────────────────────────────
    @Schema(description = "Mã khách hàng mới (tuỳ chọn)", example = "CUS-002")
    private String customerCode;

    @Schema(description = "Ngày giao hàng mới (>= hôm nay)", example = "2026-12-31")
    private LocalDate deliveryDate;

    // ─── INTERNAL_TRANSFER ─────────────────────────────────────
    @Schema(description = "Mã kho đích mới (tuỳ chọn)", example = "WH-HN-03")
    private String destinationWarehouseCode;

    @Schema(description = "Ngày chuyển hàng mới (>= hôm nay)", example = "2026-12-31")
    private LocalDate transferDate;

    // ─── Chung ─────────────────────────────────────────────────
    @Schema(description = "Danh sách sản phẩm cập nhật")
    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<OutboundItemRequest> items;

    @Schema(description = "Ghi chú", example = "Cập nhật số lượng đợt 1")
    private String note;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class OutboundItemRequest {

        @Schema(description = "ID sản phẩm", example = "1")
        @NotNull(message = "SKU ID is required")
        private Long skuId;

        @Schema(description = "Số lượng mới", example = "20")
        @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "Quantity must be > 0")
        private BigDecimal quantity;

        @Schema(description = "Ghi chú", example = "Đổi số lượng")
        private String note;
    }
}