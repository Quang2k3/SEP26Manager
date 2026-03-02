package org.example.sep26management.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Maps to table: inventory_lots
 * Tracks lot-level information for inventory (manufacture date, expiry, QC status)
 */
@Entity
@Table(name = "inventory_lots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryLotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lot_id")
    private Long lotId;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(name = "lot_number", nullable = false, length = 100)
    private String lotNumber;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "qc_status", nullable = false, length = 50)
    @Builder.Default
    private String qcStatus = "PENDING";

    @Column(name = "quarantine_status", nullable = false, length = 50)
    @Builder.Default
    private String quarantineStatus = "NONE";

    @Column(name = "receiving_item_id")
    private Long receivingItemId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}