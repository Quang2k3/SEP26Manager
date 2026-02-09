package org.example.sep26management.application.dto.response;

import lombok.*;
import org.example.sep26management.domain.enums.ZoneType;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZoneResponse {

    private Long zoneId;
    private String zoneCode;
    private String zoneName;
    private String warehouseCode;
    private ZoneType zoneType;
    private String zoneTypeDisplay; // Display name for UI
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
