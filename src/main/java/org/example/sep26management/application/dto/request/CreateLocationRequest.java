package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.example.sep26management.application.enums.LocationType;

import java.math.BigDecimal;

/**
 * UC-LOC-02: Create Location
 * BR-LOC-04: hierarchy Zone → Aisle → Rack → Bin
 * BR-LOC-05: location_code unique within zone
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateLocationRequest {

    @NotNull(message = "Warehouse ID is required")
    private Long warehouseId;

    @NotNull(message = "Zone ID is required")
    private Long zoneId;

    @NotBlank(message = "Location Code is required")
    @Size(max = 100, message = "Location Code must not exceed 100 characters")
    private String locationCode;

    /** BR-LOC-04: AISLE | RACK | BIN */
    @NotNull(message = "Location Type is required")
    private LocationType locationType;

    /**
     * Parent location ID — required for RACK and BIN
     * AISLE: null (directly under zone)
     * RACK: must point to an AISLE
     * BIN: must point to a RACK
     */
    private Long parentLocationId;

    /** BR-LOC-07: optional capacity constraint for BIN */
    @DecimalMin(value = "0.0", inclusive = false, message = "Max weight must be positive")
    private BigDecimal maxWeightKg;

    @DecimalMin(value = "0.0", inclusive = false, message = "Max volume must be positive")
    private BigDecimal maxVolumeM3;

    private Boolean isPickingFace;

    private Boolean isStaging;
}