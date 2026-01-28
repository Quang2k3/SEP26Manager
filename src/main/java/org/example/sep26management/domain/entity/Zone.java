package org.example.sep26management.domain.entity;

import lombok.*;
import org.example.sep26management.domain.enums.ZoneType;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Zone {
    private Long zoneId;

    // Zone Information
    private String zoneCode; // e.g., "Z-INB", "Z-HC", "Z-OUT"
    private String zoneName; // Display name
    private String warehouseCode; // e.g., "WH01", "WH02"
    private ZoneType zoneType; // INBOUND, STORAGE, OUTBOUND, HOLD, DEFECT

    // Status
    private Boolean isActive;

    // Audit fields
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;

    // Domain methods
    public boolean isActive() {
        return Boolean.TRUE.equals(this.isActive);
    }

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
