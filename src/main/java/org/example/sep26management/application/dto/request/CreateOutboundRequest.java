package org.example.sep26management.application.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import org.example.sep26management.application.enums.OutboundType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreateOutboundRequest {

    // Tự inject từ JWT — FE không truyền
    private Long warehouseId;

    @Schema(description = "Loại đơn xuất kho", example = "SALES_ORDER",
            allowableValues = {"SALES_ORDER", "INTERNAL_TRANSFER"})
    @NotNull(message = "Order type is required")
    private OutboundType orderType;

    // ─── SALES_ORDER ───────────────────────────────────────────
    @Schema(description = "Mã khách hàng (bắt buộc nếu SALES_ORDER)", example = "CUS-001")
    private String customerCode;

    @Schema(description = "Ngày giao hàng dự kiến (>= hôm nay)", example = "2026-12-31")
    private LocalDate deliveryDate;

    // ─── INTERNAL_TRANSFER ─────────────────────────────────────
    @Schema(description = "Mã kho đích (bắt buộc nếu INTERNAL_TRANSFER)", example = "WH-HN-02")
    private String destinationWarehouseCode;

    // ─── Chung ─────────────────────────────────────────────────
    @Schema(description = "Danh sách sản phẩm (ít nhất 1 món)")
    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<OutboundItemRequest> items;

    @Schema(description = "Ghi chú", example = "Xuất hàng đợt 1")
    private String note;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class OutboundItemRequest {

        @Schema(description = "ID sản phẩm", example = "1")
        @NotNull(message = "SKU ID is required")
        private Long skuId;

        @Schema(description = "Số lượng xuất", example = "10")
        @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "Quantity must be > 0")
        private BigDecimal quantity;

        @Schema(description = "Ghi chú", example = "Lấy lô mới")
        private String note;
    }
}