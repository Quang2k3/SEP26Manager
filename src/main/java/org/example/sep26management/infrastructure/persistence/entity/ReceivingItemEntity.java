package org.example.sep26management.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "receiving_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceivingItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "receiving_item_id")
    private Long receivingItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiving_id", nullable = false)
    private ReceivingOrderEntity receivingOrder;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(name = "expected_qty", precision = 12, scale = 2)
    private BigDecimal expectedQty;

    @Column(name = "received_qty", nullable = false, precision = 12, scale = 2)
    private BigDecimal receivedQty;

    @Column(name = "lot_number", length = 100)
    private String lotNumber;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "weight_kg", precision = 12, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "qc_required", nullable = false)
    private Boolean qcRequired;

    @PrePersist
    protected void onCreate() {
        if (qcRequired == null)
            qcRequired = false;
    }
}
