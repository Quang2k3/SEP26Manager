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

    // ── Outbound QC / Dispatch — dùng so_id column (thêm V20) ────

    /**
     * BR-DISPATCH-03 / BR-QC-04:
     * Lấy tất cả OPEN incidents cho một Sales Order.
     * Dùng cột so_id được thêm ở V20__outbound_failed_cases.sql.
     */
    @Query("SELECT i FROM IncidentEntity i WHERE i.status = 'OPEN' AND i.soId = :soId")
    List<IncidentEntity> findOpenIncidentsBySoId(@Param("soId") Long soId);

    /**
     * Count OPEN incidents for a SO — quick guard check for dispatch.
     */
    @Query("SELECT COUNT(i) FROM IncidentEntity i WHERE i.status = 'OPEN' AND i.soId = :soId")
    long countOpenIncidentsBySoId(@Param("soId") Long soId);

    /**
     * Lấy tất cả incidents (bất kể status) cho một SO — dùng để hiển thị
     * lịch sử incident trong OutboundDetailModal.
     */
    @Query("SELECT i FROM IncidentEntity i WHERE i.soId = :soId ORDER BY i.createdAt DESC")
    List<IncidentEntity> findAllBySoIdOrderByCreatedAtDesc(@Param("soId") Long soId);
}