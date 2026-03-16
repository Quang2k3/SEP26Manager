package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.SalesOrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface OutboundQueryRepository extends JpaRepository<SalesOrderEntity, Long> {

    // ── List query — null-safe warehouseId (MANAGER có thể không có warehouse) ──
    @Query("""
            SELECT s FROM SalesOrderEntity s
            WHERE (:warehouseId IS NULL OR s.warehouseId = :warehouseId)
              AND (:status IS NULL OR s.status = :status)
              AND (:createdBy IS NULL OR s.createdBy = :createdBy)
              AND (:fromDate IS NULL OR s.createdAt >= :fromDate)
              AND (:toDate IS NULL OR s.createdAt <= :toDate)
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(s.soCode) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY s.createdAt DESC
            """)
    Page<SalesOrderEntity> searchSalesOrders(
            @Param("warehouseId") Long warehouseId,
            @Param("status") String status,
            @Param("createdBy") Long createdBy,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("keyword") String keyword,
            Pageable pageable);

    // ── Summary count queries — null-safe warehouseId ──────────────────────────
    @Query("SELECT COUNT(s) FROM SalesOrderEntity s WHERE (:wh IS NULL OR s.warehouseId = :wh) AND s.status = :status")
    long countByStatusAllTime(@Param("wh") Long wh, @Param("status") String status);
}
