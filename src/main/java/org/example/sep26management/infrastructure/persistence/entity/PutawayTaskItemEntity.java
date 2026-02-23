package org.example.sep26management.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "putaway_task_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PutawayTaskItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "putaway_task_item_id")
    private Long putawayTaskItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "putaway_task_id", nullable = false)
    private PutawayTaskEntity putawayTask;

    @Column(name = "receiving_item_id", nullable = false)
    private Long receivingItemId;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(name = "lot_id")
    private Long lotId;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity;

    @Column(name = "putaway_qty", nullable = false, precision = 12, scale = 2)
    private BigDecimal putawayQty;

    @Column(name = "suggested_location_id")
    private Long suggestedLocationId;

    @Column(name = "actual_location_id")
    private Long actualLocationId;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @PrePersist
    protected void onCreate() {
        if (putawayQty == null)
            putawayQty = BigDecimal.ZERO;
    }
}
