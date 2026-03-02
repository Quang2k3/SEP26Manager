package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * UC-LOC-08: Configure Bin Capacity response
 * BR-LOC-31: returns recalculated available capacity immediately
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BinCapacityResponse {

    private Long locationId;
    private String locationCode;
    private Long zoneId;
    private String zoneCode;

    private BigDecimal maxWeightKg;
    private BigDecimal maxVolumeM3;

    // Recalculated after save — BR-LOC-31
    private BigDecimal currentOccupiedQty;
    private BigDecimal availableWeightKg;
    private BigDecimal availableVolumeM3;

    private LocalDateTime updatedAt;
}