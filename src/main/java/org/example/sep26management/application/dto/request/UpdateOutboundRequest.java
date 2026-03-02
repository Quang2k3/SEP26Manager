package org.example.sep26management.application.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * UC-OUT-02: Update Outbound Order
 * BR-OUT-06: only creator can edit DRAFT
 * BR-OUT-08: stock availability rechecked on edit
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UpdateOutboundRequest {

    // ─── Sales Order updatable fields ───
    private Long customerId;
    private LocalDate deliveryDate;   // BR-OUT-02: >= today
    private String referenceOrderCode;

    // ─── Internal Transfer updatable fields ───
    private Long destinationWarehouseId;
    private String transferReason;
    private String receiverName;
    private String receiverPhone;
    private LocalDate transferDate;

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