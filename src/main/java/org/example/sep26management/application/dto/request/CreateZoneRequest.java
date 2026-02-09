package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.example.sep26management.domain.enums.ZoneType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateZoneRequest {

    @NotBlank(message = "Zone code is required")
    private String zoneCode;

    @NotBlank(message = "Zone name is required")
    private String zoneName;

    @NotBlank(message = "Warehouse code is required")
    private String warehouseCode;

    @NotNull(message = "Zone type is required")
    private ZoneType zoneType;

    private Boolean isActive; // Optional, defaults to true
}
