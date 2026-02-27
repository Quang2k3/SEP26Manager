package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.DecimalMin;
import lombok.*;

import java.math.BigDecimal;

/**
 * UC-LOC-03: Update Location
 * BR-LOC-08: location_code is IMMUTABLE — NOT included in this request
 * BR-LOC-09: new capacity must be >= current occupied qty
 * BR-LOC-10: hierarchy integrity preserved
 * BR-LOC-11: locations with inventory cannot change parent zone
 *
 * Updatable fields: maxWeightKg, maxVolumeM3, isPickingFace, isStaging
 * NOT updatable: locationCode, zoneId, locationType, parentLocationId (if has inventory)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateLocationRequest {

    /** BR-LOC-09: new capacity must be >= current occupied qty */
    @DecimalMin(value = "0.0", inclusive = false, message = "Max weight must be positive")
    private BigDecimal maxWeightKg;

    @DecimalMin(value = "0.0", inclusive = false, message = "Max volume must be positive")
    private BigDecimal maxVolumeM3;

    private Boolean isPickingFace;

    private Boolean isStaging;
}