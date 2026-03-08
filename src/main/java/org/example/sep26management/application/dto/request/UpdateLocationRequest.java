package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.DecimalMin;
import lombok.*;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * UC-LOC-03: Update Location
 * BR-LOC-08: location_code is IMMUTABLE — NOT included in this request
 * BR-LOC-09: new capacity must be >= current occupied qty
 * BR-LOC-10: hierarchy integrity preserved
 * BR-LOC-11: locations with inventory cannot change parent zone
 *
 * Updatable fields: maxWeightKg, maxVolumeM3, isPickingFace, isStaging
 * NOT updatable: locationCode, zoneId, locationType, parentLocationId (if has
 * inventory)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateLocationRequest {

    /** BR-LOC-09: new capacity must be >= current occupied qty */
    @Schema(description = "UPDATE Cân Nặng Max", example = "150.0")
    @DecimalMin(value = "0.0", inclusive = false, message = "Max weight must be positive")
    private BigDecimal maxWeightKg;

    @Schema(description = "UPDATE Thể Tích Max", example = "3.0")
    @DecimalMin(value = "0.0", inclusive = false, message = "Max volume must be positive")
    private BigDecimal maxVolumeM3;

    @Schema(description = "UPDATE PickFace", example = "true")
    private Boolean isPickingFace;

    @Schema(description = "UPDATE Staging", example = "false")
    private Boolean isStaging;
}