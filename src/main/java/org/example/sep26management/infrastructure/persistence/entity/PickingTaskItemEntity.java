package org.example.sep26management.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "picking_task_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PickingTaskItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "picking_task_item_id")
    private Long pickingTaskItemId;

    @Column(name = "picking_task_id", nullable = false)
    private Long pickingTaskId;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(name = "lot_id")
    private Long lotId;

    @Column(name = "from_location_id", nullable = false)
    private Long fromLocationId;

    @Column(name = "required_qty", nullable = false, precision = 12, scale = 2)
    private BigDecimal requiredQty;

    @Column(name = "picked_qty", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal pickedQty = BigDecimal.ZERO;
}