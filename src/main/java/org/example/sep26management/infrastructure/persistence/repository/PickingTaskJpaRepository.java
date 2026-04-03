package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.PickingTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PickingTaskJpaRepository extends JpaRepository<PickingTaskEntity, Long> {

    List<PickingTaskEntity> findByWarehouseIdAndSoId(Long warehouseId, Long soId);

    /** BR-WXE-06: count today's pick tasks for code generation */
    @Query("""
    SELECT COUNT(p)
    FROM PickingTaskEntity p
    WHERE p.warehouseId = :warehouseId
      AND p.createdAt >= :start
      AND p.createdAt < :end
""")
    long countTodayByWarehouse(
            @Param("warehouseId") Long warehouseId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    // ── Giải pháp 3: Keeper picking claim ────────────────────────────────────

    /**
     * Atomic claim task cho Keeper — chỉ thành công nếu assigned_to IS NULL.
     * affected = 1 → claim OK | affected = 0 → Keeper khác đã nhận task.
     */
    @Modifying
    @Query("""
            UPDATE PickingTaskEntity t
            SET t.assignedTo = :keeperId,
                t.startedAt  = CURRENT_TIMESTAMP
            WHERE t.pickingTaskId = :taskId
              AND t.assignedTo IS NULL
              AND t.status IN ('OPEN', 'IN_PROGRESS')
            """)
    int claimKeeperAssignment(
            @Param("taskId")   Long taskId,
            @Param("keeperId") Long keeperId);

    /**
     * Release Keeper claim — dùng sau confirmPicked.
     */
    @Modifying
    @Query("""
            UPDATE PickingTaskEntity t
            SET t.assignedTo = NULL
            WHERE t.pickingTaskId = :taskId
              AND t.assignedTo    = :keeperId
            """)
    int releaseKeeperAssignment(
            @Param("taskId")   Long taskId,
            @Param("keeperId") Long keeperId);

    // ── Giải pháp 4: QC outbound claim ───────────────────────────────────────

    /**
     * Atomic claim task QC — chỉ thành công nếu assigned_qc_id IS NULL.
     * affected = 1 → claim OK | affected = 0 → QC khác đang scan.
     */
    @Modifying
    @Query("""
            UPDATE PickingTaskEntity t
            SET t.assignedQcId = :qcId
            WHERE t.pickingTaskId = :taskId
              AND t.assignedQcId IS NULL
              AND t.status IN ('PICKED', 'QC_IN_PROGRESS')
            """)
    int claimQcAssignment(
            @Param("taskId") Long taskId,
            @Param("qcId")   Long qcId);

    /**
     * Release QC claim — gọi sau finalizeQc.
     */
    @Modifying
    @Query("""
            UPDATE PickingTaskEntity t
            SET t.assignedQcId = NULL
            WHERE t.pickingTaskId = :taskId
              AND t.assignedQcId  = :qcId
            """)
    int releaseQcAssignment(
            @Param("taskId") Long taskId,
            @Param("qcId")   Long qcId);
}