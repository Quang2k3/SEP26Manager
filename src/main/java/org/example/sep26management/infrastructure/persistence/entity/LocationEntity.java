package org.example.sep26management.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.sep26management.application.enums.LocationType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Maps to table: locations
 *
 * BR-LOC-04: Zone → Aisle → Rack → Bin hierarchy
 * BR-LOC-05: location_code unique within zone
 * BR-LOC-06: only BIN can store inventory
 * BR-LOC-07: max_weight_kg / max_volume_m3 must not be exceeded during putaway
 * BR-LOC-08: location_code is immutable after creation
 */
@Entity
@Table(name = "locations",
        uniqueConstraints = @UniqueConstraint(name = "uk_location_zone_code",
                columnNames = {"zone_id", "location_code"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "zone_id")
    private Long zoneId;

    /** BR-LOC-08: immutable after creation — set once, never updated */
    @Column(name = "location_code", nullable = false, length = 100)
    private String locationCode;

    /** BR-LOC-04: AISLE | RACK | BIN */
    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false, length = 50)
    private LocationType locationType;

    /** Parent location: null for AISLE, AISLE_id for RACK, RACK_id for BIN */
    @Column(name = "parent_location_id")
    private Long parentLocationId;

    /** BR-LOC-07: max weight capacity in kg (optional, BIN relevant) */
    @Column(name = "max_weight_kg", precision = 12, scale = 2)
    private BigDecimal maxWeightKg;

    /** BR-LOC-07: max volume capacity in m³ (optional, BIN relevant) */
    @Column(name = "max_volume_m3", precision = 12, scale = 3)
    private BigDecimal maxVolumeM3;

    /** Operational flag: can this location be picked from */
    @Column(name = "is_picking_face", nullable = false)
    @Builder.Default
    private Boolean isPickingFace = false;

    /** Operational flag: is this a staging area */
    @Column(name = "is_staging", nullable = false)
    @Builder.Default
    private Boolean isStaging = false;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}