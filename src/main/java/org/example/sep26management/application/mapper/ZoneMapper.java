package org.example.sep26management.application.mapper;

import org.example.sep26management.application.dto.response.ZoneResponse;
import org.example.sep26management.domain.entity.Zone;
import org.springframework.stereotype.Component;

@Component
public class ZoneMapper {

    public ZoneResponse toResponse(Zone zone) {
        if (zone == null) {
            return null;
        }

        return ZoneResponse.builder()
                .zoneId(zone.getZoneId())
                .zoneCode(zone.getZoneCode())
                .zoneName(zone.getZoneName())
                .warehouseCode(zone.getWarehouseCode())
                .zoneType(zone.getZoneType())
                .zoneTypeDisplay(zone.getZoneType() != null ? zone.getZoneType().getDisplayName() : null)
                .isActive(zone.getIsActive())
                .createdAt(zone.getCreatedAt())
                .updatedAt(zone.getUpdatedAt())
                .build();
    }
}
