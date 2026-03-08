// ===== CreateOutboundRequest.java =====
package org.example.sep26management.application.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import org.example.sep26management.application.enums.OutboundType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * UC-OUT-01: Create Outbound Order
 * BR-OUT-01: order type determines required fields
 *
 * warehouseId được lấy tự động từ JWT token của người dùng đang đăng nhập.
 * FE không cần truyền warehouseId vào body.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOutboundRequest {

    @Schema(description = "ID Kho xuất (Mặc định lấy từ Token người dùng đăng nhập)")
    private Long warehouseId;

    @Schema(description = "Loại đơn xuất kho (Bán hàng hoặc Chuyển kho nội bộ)", example = "SALES_ORDER", allowableValues = {
            "SALES_ORDER", "INTERNAL_TRANSFER" })
    @NotNull(message = "Order type is required")
    private OutboundType orderType;

    // ─── Sales Order fields ───
    @Schema(description = "(Bắt buộc nếu SALES_ORDER) Mã khách hàng - FE lấy từ dropdown KH", example = "CUS-001")
    private String customerCode; // required if SALES_ORDER

    @Schema(description = "Ngày dự kiến giao hàng", example = "2025-12-31")
    private LocalDate deliveryDate; // BR-OUT-02: >= today

    @Schema(description = "Mã chứng từ tham chiếu (Số SO, PO KH gửi, v.v...)", example = "SO-2309-001")
    private String referenceOrderCode;

    // ─── Internal Transfer fields ───
    @Schema(description = "(Bắt buộc nếu INTERNAL_TRANSFER) Mã kho nhận (Đích đến)", example = "WH-HN-02")
    private String destinationWarehouseCode; // required if INTERNAL_TRANSFER

    @Schema(description = "Lý do chuyển kho", example = "STOCK_BALANCING")
    private String transferReason; // STOCK_BALANCING | BRANCH_REQUEST | OTHER

    @Schema(description = "Tên người nhận (Bên kho đích)", example = "Nguyễn Văn B")
    private String receiverName;

    @Schema(description = "SĐT người nhận", example = "0987654321")
    private String receiverPhone;

    @Schema(description = "Ngày dự kiến chuyển hàng", example = "2025-12-31")
    private LocalDate transferDate; // BR-OUT-02: >= today

    @Schema(description = "Danh sách sản phẩm cần xuất kho (Ít nhất 1 món)")
    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<OutboundItemRequest> items;

    @Schema(description = "Ghi chú chung cho phiếu xuất", example = "Xuất hàng đợt 1")
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

        @Schema(description = "Số lượng YÊU CẦU xuất", example = "50")
        @NotNull
        @DecimalMin(value = "0.0", inclusive = false, message = "Quantity must be greater than 0")
        private BigDecimal quantity;

        @Schema(description = "Ghi chú cho từng dòng", example = "Lấy hàng mới nhất")
        private String note;
    }
}