package org.example.sep26management.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    // ── QC Scan columns ──────────────────────────────────────────
    /**
     * Số lượng unit đã scan PASS.
     * Track từng unit riêng để tránh ghi đè khi 1 SKU có nhiều unit (VD: 1 PASS + 1 FAIL).
     */
    @Column(name = "qc_pass_qty", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal qcPassQty = BigDecimal.ZERO;

    /**
     * Số lượng unit đã scan FAIL.
     */
    @Column(name = "qc_fail_qty", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal qcFailQty = BigDecimal.ZERO;

    /**
     * PASS | FAIL | NULL (null = not yet scanned).
     * Worst-case: nếu qcFailQty > 0 → FAIL, ngược lại → PASS.
     * Set tự động sau mỗi lần scan.
     */
    @Column(name = "qc_result", length = 10)
    private String qcResult;

    /** Reason text — populated when qc_result = FAIL (BR-QC-01) */
    @Column(name = "qc_note", columnDefinition = "TEXT")
    private String qcNote;

    /** Timestamp of QC scan; NULL means item has not been QC-scanned yet */
    @Column(name = "qc_scanned_at")
    private LocalDateTime qcScannedAt;

    /**
     * URL ảnh chụp hàng hỏng khi qc_result = FAIL.
     * Upload từ mobile scanner, lưu Cloudinary/S3, paste URL vào đây.
     * Dùng làm bằng chứng cho Incident DAMAGE.
     */
    @Column(name = "qc_attachment_url", columnDefinition = "TEXT")
    private String qcAttachmentUrl;
}