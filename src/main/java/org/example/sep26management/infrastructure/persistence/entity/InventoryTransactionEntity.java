package org.example.sep26management.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Maps to table: inventory_transactions
 * Single source of truth for all inventory movements
 * Used in UC-OUT-04: type = RESERVE when approving outbound
 */
@Entity
@Table(name = "inventory_transactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InventoryTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "txn_id")
    private Long txnId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(name = "lot_id")
    private Long lotId;

    @Column(name = "location_id", nullable = false)
    @Builder.Default
    private Long locationId = 0L; // 0 = warehouse-level txn (no specific location)

    @Column(name = "quantity", nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity;

    @Column(name = "txn_type", nullable = false, length = 50)
    private String txnType;

    @Column(name = "reference_table", length = 100)
    private String referenceTable;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "reason_code", length = 100)
    private String reasonCode;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}