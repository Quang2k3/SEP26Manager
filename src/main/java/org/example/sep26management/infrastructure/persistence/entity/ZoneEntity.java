package org.example.sep26management.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.sep26management.domain.enums.ZoneType;

import java.time.LocalDateTime;

@Entity
@Table(name = "zones", uniqueConstraints = {
        @UniqueConstraint(columnNames = "zone_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZoneEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "zone_id")
    private Long zoneId;

    @Column(name = "zone_code", unique = true, nullable = false, length = 50)
    private String zoneCode;

    @Column(name = "zone_name", nullable = false, length = 200)
    private String zoneName;

    @Column(name = "warehouse_code", nullable = false, length = 50)
    private String warehouseCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone_type", nullable = false, length = 50)
    private ZoneType zoneType;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
