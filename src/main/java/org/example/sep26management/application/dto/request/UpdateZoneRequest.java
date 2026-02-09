package org.example.sep26management.application.dto.request;

import lombok.*;
import org.example.sep26management.domain.enums.ZoneType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateZoneRequest {

    private String zoneName;
    private String warehouseCode;
    private ZoneType zoneType;
    private Boolean isActive;
}
