package org.example.sep26management.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * UC-B07: Configure SKU Threshold
 * Maps to table: sku_thresholds
 *
 * BR-SKU-07:
 *   - min_qty and max_qty must be positive integers
 *   - min_qty < max_qty strictly
 *   - If left blank → 0 or null (no threshold applied)
 */
@Entity
@Table(name = "sku_thresholds",
        uniqueConstraints = @UniqueConstraint(columnNames = {"warehouse_id", "sku_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkuThresholdEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "threshold_id")
    private Long thresholdId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    /** BR-SKU-07: positive integer, < maxQty */
    @Column(name = "min_qty", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal minQty = BigDecimal.ZERO;

    /** BR-SKU-07: positive integer, > minQty */
    @Column(name = "max_qty", precision = 12, scale = 2)
    private BigDecimal maxQty;

    @Column(name = "reorder_point", precision = 12, scale = 2)
    private BigDecimal reorderPoint;

    @Column(name = "reorder_qty", precision = 12, scale = 2)
    private BigDecimal reorderQty;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

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