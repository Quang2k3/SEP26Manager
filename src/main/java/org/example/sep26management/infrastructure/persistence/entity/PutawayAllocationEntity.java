package org.example.sep26management.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "putaway_allocations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PutawayAllocationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "allocation_id")
    private Long allocationId;

    @Column(name = "putaway_task_id", nullable = false)
    private Long putawayTaskId;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(name = "lot_id")
    private Long lotId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "allocated_qty", nullable = false, precision = 12, scale = 2)
    private BigDecimal allocatedQty;

    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "RESERVED";

    @Column(name = "allocated_by", nullable = false)
    private Long allocatedBy;

    @Column(name = "allocated_at", nullable = false)
    @Builder.Default
    private LocalDateTime allocatedAt = LocalDateTime.now();
}
