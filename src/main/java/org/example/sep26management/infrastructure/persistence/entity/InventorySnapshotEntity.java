package org.example.sep26management.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Maps to table: inventory_snapshot
 * Single source of truth for current stock per (warehouse, sku, lot, location)
 */
@Entity
@Table(name = "inventory_snapshot")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(InventorySnapshotId.class)
public class InventorySnapshotEntity {

    @Id
    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Id
    @Column(name = "sku_id")
    private Long skuId;

    @Id
    @Column(name = "lot_id_safe", insertable = false, updatable = false)
    private Long lotIdSafe; // generated column

    @Id
    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "lot_id")
    private Long lotId;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "reserved_qty", nullable = false)
    private BigDecimal reservedQty;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;
}