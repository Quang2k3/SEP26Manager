package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.example.sep26management.application.enums.LocationType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * UC-LOC-05: View Location List - each record shows:
 * locationCode, locationType, parentLocationId, status
 * BR-LOC-16: parent-child relationships clearly indicated
 * BR-LOC-17: inactive locations visible but clearly marked
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LocationResponse {

    private Long locationId;
    private Long warehouseId;
    private Long zoneId;
    private String zoneCode;     // denormalized for display

    private String locationCode;
    private LocationType locationType;

    // Parent info — BR-LOC-16: show hierarchy
    private Long parentLocationId;
    private String parentLocationCode; // denormalized for display

    private BigDecimal maxWeightKg;
    private BigDecimal maxVolumeM3;
    private Boolean isPickingFace;
    private Boolean isStaging;

    /** BR-LOC-17: always present — never hidden */
    private Boolean active;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}