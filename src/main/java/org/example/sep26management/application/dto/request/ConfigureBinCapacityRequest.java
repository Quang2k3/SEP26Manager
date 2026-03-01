package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.DecimalMin;
import lombok.*;

import java.math.BigDecimal;

/**
 * UC-LOC-08: Configure Bin Capacity
 * BR-LOC-28: values must be > 0
 * BR-LOC-29: new capacity must not be < current occupied qty
 * BR-LOC-30: units are kg (weight) and m³ (volume) — system standard
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfigureBinCapacityRequest {

    /** BR-LOC-28: must be > 0 */
    @DecimalMin(value = "0.0", inclusive = false, message = "Max weight must be greater than zero")
    private BigDecimal maxWeightKg;

    /** BR-LOC-28: must be > 0 */
    @DecimalMin(value = "0.0", inclusive = false, message = "Max volume must be greater than zero")
    private BigDecimal maxVolumeM3;
}