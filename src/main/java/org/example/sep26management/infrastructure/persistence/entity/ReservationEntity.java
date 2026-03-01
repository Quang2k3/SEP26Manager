package org.example.sep26management.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Maps to table: reservations
 * BR-OUT-17: approved quantities moved from available to reserved
 */
@Entity
@Table(name = "reservations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(name = "lot_id")
    private Long lotId;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity;

    /** e.g. "sales_orders" or "transfers" */
    @Column(name = "reference_table", length = 100)
    private String referenceTable;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "OPEN";

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}