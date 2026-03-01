package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.example.sep26management.application.enums.OccupancyStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * UC-LOC-06: View Bin Occupancy
 * Shows: zone/aisle/rack/bin code, capacity, occupied qty, occupancy status
 * BR-LOC-20: real-time from inventory_snapshot
 * BR-LOC-21: FULL when occupied >= max capacity
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BinOccupancyResponse {

    private Long locationId;
    private String locationCode;

    // Hierarchy info — BR-LOC-16
    private Long zoneId;
    private String zoneCode;
    private Long parentLocationId;     // RACK id
    private String parentLocationCode; // RACK code
    private Long grandParentLocationId;     // AISLE id
    private String grandParentLocationCode; // AISLE code

    // Capacity config
    private BigDecimal maxWeightKg;
    private BigDecimal maxVolumeM3;

    // Real-time occupancy — BR-LOC-23
    private BigDecimal occupiedQty;
    private BigDecimal reservedQty;
    private BigDecimal availableQty;  // max - occupied - reserved (if max configured)

    /** BR-LOC-21: EMPTY / PARTIAL / FULL */
    private OccupancyStatus occupancyStatus;

    private Boolean isPickingFace;
    private Boolean isStaging;
    private Boolean active;

    /** UC-LOC-06 3c: detailed inventory in bin (optional, only when requested) */
    private List<BinInventoryItem> inventoryItems;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BinInventoryItem {
        private Long skuId;
        private String skuCode;
        private String skuName;
        private Long lotId;
        private String lotNumber;
        private java.time.LocalDate expiryDate;
        private BigDecimal quantity;
        private BigDecimal reservedQty;
    }
}