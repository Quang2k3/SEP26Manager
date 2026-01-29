package org.example.sep26management.domain.entity;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Category-Zone Mapping Domain Entity
 * Maps product categories to storage zones (e.g., HOME_CARE → ZHC)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryZoneMapping {
    private Long mappingId;

    private Long categoryId;
    private Long zoneId;

    // Priority for multi-zone categories (lower = higher priority)
    private Integer priority;

    private Boolean isActive;

    // Audit
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;

    // Domain Methods
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
