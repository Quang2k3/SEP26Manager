package org.example.sep26management.application.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response for GET /v1/outbound/sales-orders/{soId}/dispatch-note
 *
 * Generated dynamically — NOT stored in DB.
 * Only includes items with qc_result = PASS.
 */
@Data
@Builder
public class DispatchNoteResponse {

    /** DN-{soCode} */
    private String dispatchNoteCode;

    private String warehouseName;
    private String customerName;
    private LocalDateTime dispatchDate;

    private List<DispatchNoteItem> items;
    private int totalItems;

    /** Full name of the user who created the Sales Order */
    private String createdByName;

    @Data
    @Builder
    public static class DispatchNoteItem {
        private String skuCode;
        private String skuName;
        private String lotNumber;
        private String expiryDate;
        private String locationCode;
        private java.math.BigDecimal quantity;
    }
}