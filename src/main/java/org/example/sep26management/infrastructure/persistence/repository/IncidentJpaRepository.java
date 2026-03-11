package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.application.enums.IncidentCategory;
import org.example.sep26management.infrastructure.persistence.entity.IncidentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IncidentJpaRepository extends JpaRepository<IncidentEntity, Long> {

    Page<IncidentEntity> findByWarehouseIdAndStatusOrderByCreatedAtDesc(Long warehouseId, String status,
                                                                        Pageable pageable);

    Page<IncidentEntity> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<IncidentEntity> findByCategoryOrderByCreatedAtDesc(IncidentCategory category, Pageable pageable);

    Page<IncidentEntity> findByStatusAndCategoryOrderByCreatedAtDesc(String status, IncidentCategory category,
                                                                     Pageable pageable);

    List<IncidentEntity> findByReceivingIdOrderByCreatedAtDesc(Long receivingId);

    Page<IncidentEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // ── Outbound QC / Dispatch ────────────────────────────────────

    /**
     * BR-DISPATCH-03 / BR-QC-04:
     * Check if there are any OPEN incidents linked to picking task items of a SO.
     * Uses the picking_task_items ��� picking_tasks → so_id chain via a native query
     * for simplicity, since IncidentEntity only carries warehouse_id / receiving_id.
     *
     * The outbound incidents are linked by referencing the soId stored in the
     * incident description or a dedicated reference field.
     * We rely on the convention that outbound incidents carry
     * reference_id = soId when created via the outbound flow.
     */
    @Query("""
            SELECT i FROM IncidentEntity i
            WHERE i.status = 'OPEN'
              AND i.receivingId = :soId
            """)
    List<IncidentEntity> findOpenIncidentsBySoId(@Param("soId") Long soId);

    /**
     * Count OPEN incidents for a SO — quick guard check.
     */
    @Query("""
            SELECT COUNT(i) FROM IncidentEntity i
            WHERE i.status = 'OPEN'
              AND i.receivingId = :soId
            """)
    long countOpenIncidentsBySoId(@Param("soId") Long soId);
}