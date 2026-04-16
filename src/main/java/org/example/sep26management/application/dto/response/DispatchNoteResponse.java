package org.example.sep26management.application.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
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
    /** Địa chỉ giao hàng của khách hàng */
    private String customerAddress;
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
        private String unit;
        private String lotNumber;
        private String manufactureDate;
        private String expiryDate;
        private String locationCode;
        private BigDecimal quantity;
        /** Quy cách: số chai/lon trong 1 thùng — lấy từ sku.unitsPerCarton */
        private Integer unitsPerCarton;
        /** Trọng lượng 1 thùng (kg) — lấy từ sku.weightPerCartonKg */
        private BigDecimal weightPerCartonKg;
    }
}