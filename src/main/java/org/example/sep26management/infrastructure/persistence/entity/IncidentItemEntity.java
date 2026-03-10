package org.example.sep26management.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "incident_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "incident_item_id")
    private Long incidentItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", nullable = false)
    private IncidentEntity incident;

    @Column(name = "sku_id", nullable = false)
    private Long skuId;

    @Column(name = "damaged_qty", nullable = false, precision = 12, scale = 2)
    private BigDecimal damagedQty;

    @Column(name = "reason_code", length = 100)
    private String reasonCode;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "action_pass_qty", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal actionPassQty = BigDecimal.ZERO;

    @Column(name = "action_return_qty", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal actionReturnQty = BigDecimal.ZERO;

    @Column(name = "action_scrap_qty", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal actionScrapQty = BigDecimal.ZERO;
}
