package org.example.sep26management.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "receiving_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceivingOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "receiving_id")
    private Long receivingId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "receiving_code", nullable = false, length = 100)
    private String receivingCode;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType;

    @Column(name = "source_warehouse_id")
    private Long sourceWarehouseId;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "source_reference_code", length = 100)
    private String sourceReferenceCode;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "putaway_created_at")
    private LocalDateTime putawayCreatedAt;

    @Column(name = "putaway_done_by")
    private Long putawayDoneBy;

    @Column(name = "putaway_done_at")
    private LocalDateTime putawayDoneAt;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @OneToMany(mappedBy = "receivingOrder", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ReceivingItemEntity> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null)
            status = "DRAFT";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
