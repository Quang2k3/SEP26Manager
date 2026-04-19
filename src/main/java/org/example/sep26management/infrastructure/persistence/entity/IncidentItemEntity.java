package org.example.sep26management.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

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

    @Column(name = "expected_qty", precision = 12, scale = 2)
    private BigDecimal expectedQty;

    @Column(name = "actual_qty", precision = 12, scale = 2)
    private BigDecimal actualQty;

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

    // [FIX QC] URL ảnh bằng chứng hàng hỏng — chụp trên điện thoại khi scan FAIL
    @Column(name = "attachment_url", columnDefinition = "TEXT")
    private String attachmentUrl;

    // [FIX] Số lô và hạn sử dụng — hiển thị trong incident detail
    @Column(name = "lot_number", length = 100)
    private String lotNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    // [FIX VERDICT] Lưu phán quyết của Manager theo từng item: ACCEPT | RETURN_SCRAP
    @Column(name = "resolved_action", length = 50)
    private String resolvedAction;
}