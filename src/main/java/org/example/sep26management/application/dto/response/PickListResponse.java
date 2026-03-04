package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * UC-WXE-06: Generate Pick List response
 * BR-WXE-23: optimal picking route (sorted by zone → location code)
 * BR-WXE-24: SKU, lot, location traceability
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PickListResponse {

    private Long pickingTaskId;
    private String pickingTaskCode;
    private Long documentId;
    private String documentCode;
    private String status;

    private Long assignedTo;
    private String assignedToName;

    private List<PickListItem> items; // sorted by optimal route

    private LocalDateTime generatedAt;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PickListItem {
        private int sequence;          // picking order (BR-WXE-23: optimal route)
        private Long pickingTaskItemId;

        // Location info
        private Long locationId;
        private String locationCode;
        private String zoneCode;
        private String rackCode;

        // SKU info — BR-WXE-24: traceability
        private Long skuId;
        private String skuCode;
        private String skuName;

        // Lot info
        private Long lotId;
        private String lotNumber;
        private LocalDate expiryDate;

        private BigDecimal requiredQty;
        private BigDecimal pickedQty;
        private String status; // PENDING | PICKED
    }
}