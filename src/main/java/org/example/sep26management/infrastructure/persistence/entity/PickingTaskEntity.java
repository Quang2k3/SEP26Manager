package org.example.sep26management.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "picking_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PickingTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "picking_task_id")
    private Long pickingTaskId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "so_id")
    private Long soId;

    @Column(name = "shipment_id")
    private Long shipmentId;

    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "OPEN";

    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 3;

    /**
     * Keeper được giao task picking này.
     * Set khi Keeper bấm "Tạo QR Picking" — atomic claim WHERE assigned_to IS NULL.
     * NULL = chưa có Keeper nào nhận task.
     */
    @Column(name = "assigned_to")
    private Long assignedTo;

    /**
     * QC claim lock — userId của QC đang scan task này.
     * Set khi QC bấm "Tạo QR QC" — atomic claim WHERE assigned_qc_id IS NULL.
     * NULL = chưa có QC nào claim.
     */
    @Column(name = "assigned_qc_id")
    private Long assignedQcId;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}