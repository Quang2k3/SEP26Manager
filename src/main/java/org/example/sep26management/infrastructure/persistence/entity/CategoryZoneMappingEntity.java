package org.example.sep26management.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Category-Zone Mapping JPA Entity
 * Maps to category_zone_mapping table
 */
@Entity
@Table(name = "category_zone_mapping", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "category_id", "zone_id" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryZoneMappingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mapping_id")
    private Long mappingId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "zone_id", nullable = false)
    private Long zoneId;

    @Column(name = "priority")
    private Integer priority;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    // Audit fields
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isActive == null) {
            isActive = true;
        }
        if (priority == null) {
            priority = 1;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
