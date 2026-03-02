package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.example.sep26management.application.enums.OccupancyStatus;

import java.math.BigDecimal;

/**
 * UC-LOC-07: Search Empty Bin
 * Shows: bin code, zone/location hierarchy, total capacity, available capacity, status
 * BR-LOC-27: sorted by putaway priority (least residual space first)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmptyBinResponse {

    private Long locationId;
    private String locationCode;

    // Hierarchy
    private Long zoneId;
    private String zoneCode;
    private String rackCode;  // parent rack

    // Capacity
    private BigDecimal maxWeightKg;
    private BigDecimal maxVolumeM3;
    private BigDecimal occupiedQty;
    private BigDecimal availableWeightKg; // maxWeightKg - occupiedQty (weight-based)
    private BigDecimal availableVolumeM3; // maxVolumeM3 - occupied volume

    private OccupancyStatus occupancyStatus;

    private Boolean isPickingFace;
}