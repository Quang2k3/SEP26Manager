package org.example.sep26management.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Maps to table: sales_order_items
 */
@Entity
@Table(name = "sales_order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesOrderItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "so_item_id")
    private Long soItemId;

    @Column(name = "so_id", nullable = false)
    private Long soId;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    /** BR-OUT-04: quantity > 0, cannot exceed available */
    @Column(name = "ordered_qty", nullable = false, precision = 12, scale = 2)
    private BigDecimal orderedQty;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}