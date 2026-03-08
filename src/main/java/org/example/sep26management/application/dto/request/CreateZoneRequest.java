package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * UC-LOC-01: Create Zone
 * BR-LOC-01: zone_code phải unique trong warehouse
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateZoneRequest {
    @Schema(description = "Mã Khu Vực", example = "ZONE-ELEC")
    @NotBlank(message = "Zone Code is required")
    @Size(max = 50, message = "Zone Code must not exceed 50 characters")
    private String zoneCode;

    @Schema(description = "Tên Khu Vực", example = "Khu Điện tử")
    @Size(max = 200, message = "Zone Name must not exceed 200 characters")
    private String zoneName;
}