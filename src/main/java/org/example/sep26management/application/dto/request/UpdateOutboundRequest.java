package org.example.sep26management.application.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * UC-OUT-02: Update Outbound Order
 * BR-OUT-06: only creator can edit DRAFT
 * BR-OUT-08: stock availability rechecked on edit
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateOutboundRequest {

    // ─── Sales Order updatable fields ───
    @Schema(description = "(Bắt buộc nếu SALES_ORDER) Mã khách hàng - FE lấy từ dropdown KH", example = "CUS-001")
    private String customerCode;

    @Schema(description = "Ngày dự kiến giao hàng", example = "2025-12-31")
    private LocalDate deliveryDate; // BR-OUT-02: >= today

    @Schema(description = "Mã chứng từ tham chiếu (Số SO, PO KH gửi, v.v...)", example = "SO-2309-001")
    private String referenceOrderCode;

    // ─── Internal Transfer updatable fields ───
    @Schema(description = "(Bắt buộc nếu INTERNAL_TRANSFER) Mã kho nhận (Đích đến)", example = "WH-HN-02")
    private String destinationWarehouseCode;

    @Schema(description = "Lý do chuyển kho", example = "STOCK_BALANCING")
    private String transferReason;

    @Schema(description = "Tên người nhận (Bên kho đích)", example = "Nguyễn Văn B")
    private String receiverName;

    @Schema(description = "SĐT người nhận", example = "0987654321")
    private String receiverPhone;

    @Schema(description = "Ngày dự kiến chuyển hàng", example = "2025-12-31")
    private LocalDate transferDate;

    @Schema(description = "Danh sách sản phẩm cần xuất kho")
    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<OutboundItemRequest> items;

    @Schema(description = "Ghi chú chung cho phiếu update", example = "Sửa số lượng đợt 1")
    private String note;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OutboundItemRequest {
        @Schema(description = "ID của SKU", example = "100")
        @NotNull(message = "SKU ID is required")
        private Long skuId;

        @Schema(description = "Số lượng YÊU CẦU xuất cập nhật", example = "30")
        @NotNull
        @DecimalMin(value = "0.0", inclusive = false, message = "Quantity must be greater than 0")
        private BigDecimal quantity;

        @Schema(description = "Ghi chú cập nhật", example = "Lấy lô mới")
        private String note;
    }
}