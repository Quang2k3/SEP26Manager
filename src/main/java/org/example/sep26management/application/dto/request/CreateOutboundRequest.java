// ===== CreateOutboundRequest.java =====
package org.example.sep26management.application.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import org.example.sep26management.application.enums.OutboundType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * UC-OUT-01: Create Outbound Order
 * BR-OUT-01: order type determines required fields
 *
 * warehouseId được lấy tự động từ JWT token của người dùng đang đăng nhập.
 * FE không cần truyền warehouseId vào body.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreateOutboundRequest {

    // warehouseId is NOT exposed to FE — resolved from JWT token in controller
    // Field này được set nội bộ bởi controller sau khi extract từ JWT
    private Long warehouseId;

    @NotNull(message = "Order type is required")
    private OutboundType orderType;

    // ─── Sales Order fields ───
    /**
     * Mã khách hàng (customerCode) — FE lấy từ dropdown danh sách customer.
     * BE tự resolve customerId nội bộ từ mã này.
     * Ví dụ: "CUS-001"
     */
    private String customerCode;       // required if SALES_ORDER
    private LocalDate deliveryDate;    // BR-OUT-02: >= today
    private String referenceOrderCode;

    // ─── Internal Transfer fields ───
    /**
     * Mã kho đích (warehouseCode) — FE lấy từ dropdown danh sách kho.
     * BE tự resolve destinationWarehouseId nội bộ từ mã này.
     * Ví dụ: "WH-HN-02"
     */
    private String destinationWarehouseCode;  // required if INTERNAL_TRANSFER
    private String transferReason;             // STOCK_BALANCING | BRANCH_REQUEST | OTHER
    private String receiverName;
    private String receiverPhone;
    private LocalDate transferDate;            // BR-OUT-02: >= today

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<OutboundItemRequest> items;

    private String note;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class OutboundItemRequest {
        @NotNull(message = "SKU ID is required")
        private Long skuId;

        @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "Quantity must be greater than 0")
        private BigDecimal quantity;

        private String note;
    }
}