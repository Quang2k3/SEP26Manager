package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.PickingTaskItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PickingTaskItemJpaRepository extends JpaRepository<PickingTaskItemEntity, Long> {

    /**
     * UC-LOC-08 7b: check if bin is locked by an active picking task
     */
    @Query("""
            SELECT COUNT(i) > 0
            FROM PickingTaskItemEntity i
            JOIN PickingTaskEntity t ON t.pickingTaskId = i.pickingTaskId
            WHERE i.fromLocationId = :locationId
              AND t.status IN ('OPEN', 'IN_PROGRESS')
            """)
    boolean existsActiveTaskForLocation(@Param("locationId") Long locationId);

    // ── QC Scan queries ──────────────────────────────────────────

    /** All items belonging to a picking task */
    List<PickingTaskItemEntity> findByPickingTaskId(Long pickingTaskId);

    /** Items where qc_scanned_at IS NULL — used for pendingCount */
    @Query("SELECT i FROM PickingTaskItemEntity i WHERE i.pickingTaskId = :taskId AND i.qcScannedAt IS NULL")
    List<PickingTaskItemEntity> findUnscannedByTaskId(@Param("taskId") Long taskId);

    /** Items with qc_result = PASS — used for dispatch note and inventory deduction */
    @Query("SELECT i FROM PickingTaskItemEntity i WHERE i.pickingTaskId = :taskId AND i.qcResult = 'PASS'")
    List<PickingTaskItemEntity> findPassedItemsByTaskId(@Param("taskId") Long taskId);

    /** Count items not yet QC-scanned for a task */
    @Query("SELECT COUNT(i) FROM PickingTaskItemEntity i WHERE i.pickingTaskId = :taskId AND i.qcScannedAt IS NULL")
    long countUnscannedByTaskId(@Param("taskId") Long taskId);

    /** Count items by qc_result value for a task */
    @Query("SELECT COUNT(i) FROM PickingTaskItemEntity i WHERE i.pickingTaskId = :taskId AND i.qcResult = :result")
    long countByTaskIdAndQcResult(@Param("taskId") Long taskId, @Param("result") String result);

    // ── Dispatch queries ─────────────────────────────────────────

    /**
     * BR-DISPATCH-02: Check if any items in any task for a SO have not been QC-scanned.
     * Returns true if all items across all ACTIVE tasks for this SO have been scanned.
     */
    @Query("""
            SELECT COUNT(i) = 0
            FROM PickingTaskItemEntity i
            JOIN PickingTaskEntity t ON t.pickingTaskId = i.pickingTaskId
            WHERE t.soId = :soId
              AND t.status NOT IN ('CANCELLED', 'COMPLETED')
              AND i.qcScannedAt IS NULL
            """)
    boolean allItemsScannedForSo(@Param("soId") Long soId);

    /**
     * Fetch all PASS items for a SO across all active picking tasks.
     * Used for inventory deduction on dispatch.
     */
    @Query("""
            SELECT i
            FROM PickingTaskItemEntity i
            JOIN PickingTaskEntity t ON t.pickingTaskId = i.pickingTaskId
            WHERE t.soId = :soId
              AND t.status NOT IN ('CANCELLED')
              AND i.qcResult = 'PASS'
            """)
    List<PickingTaskItemEntity> findPassedItemsBySoId(@Param("soId") Long soId);

    /**
     * Fetch all items for a SO across all active picking tasks.
     * Used to determine the active task for a given SO.
     */
    @Query("""
            SELECT i
            FROM PickingTaskItemEntity i
            JOIN PickingTaskEntity t ON t.pickingTaskId = i.pickingTaskId
            WHERE t.soId = :soId
              AND t.status NOT IN ('CANCELLED', 'COMPLETED')
            """)
    List<PickingTaskItemEntity> findAllActiveItemsBySoId(@Param("soId") Long soId);
}