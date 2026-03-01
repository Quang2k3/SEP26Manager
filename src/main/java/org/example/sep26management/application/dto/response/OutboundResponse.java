package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.example.sep26management.application.enums.OutboundType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response for all Outbound UC-OUT-01/02/03/04
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OutboundResponse {

    private Long documentId;       // so_id or transfer_id
    private String documentCode;   // EXP-SAL-... or EXP-INT-...
    private OutboundType orderType;
    private String status;
    private Long warehouseId;

    // Sales Order fields
    private Long customerId;
    private String customerName;
    private LocalDate deliveryDate;
    private String referenceOrderCode;

    // Internal Transfer fields
    private Long destinationWarehouseId;
    private String destinationWarehouseName;
    private String transferReason;
    private String receiverName;
    private String receiverPhone;
    private LocalDate transferDate;

    private List<OutboundItemResponse> items;
    private String note;

    private Long createdBy;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Stock warnings — BR-OUT-03/04
    private List<StockWarning> stockWarnings;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OutboundItemResponse {
        private Long itemId;
        private Long skuId;
        private String skuCode;
        private String skuName;
        private BigDecimal requestedQty;
        private BigDecimal availableQty;  // BR-OUT-03: real-time
        private boolean insufficientStock; // true if requested > available
        private String note;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StockWarning {
        private Long skuId;
        private String skuCode;
        private BigDecimal requestedQty;
        private BigDecimal availableQty;
        private String message;
    }
}