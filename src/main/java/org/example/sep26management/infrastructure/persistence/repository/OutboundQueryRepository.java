package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.SalesOrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * UC-OUT-06: View Outbound List queries
 * BR-OUT-24: default last 30 days
 * BR-OUT-25: 20 per page
 */
@Repository
public interface OutboundQueryRepository extends JpaRepository<SalesOrderEntity, Long> {

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

    @Query("SELECT COUNT(s) FROM SalesOrderEntity s WHERE (:wh IS NULL OR s.warehouseId = :wh) AND s.createdAt >= :from")
    long countTotal(@Param("wh") Long wh, @Param("from") LocalDateTime from);

    @Query("SELECT COUNT(s) FROM SalesOrderEntity s WHERE (:wh IS NULL OR s.warehouseId = :wh) AND s.status = :status AND s.createdAt >= :from")
    long countByStatus(@Param("wh") Long wh, @Param("status") String status, @Param("from") LocalDateTime from);

    /**
     * Fix: dùng range comparison thay vì DATE() function
     * Hibernate không so sánh được LocalDateTime với java.sql.Date (CURRENT_DATE)
     */
    @Query("""
        SELECT COUNT(s) FROM SalesOrderEntity s
        WHERE s.warehouseId = :wh
          AND s.status = 'CONFIRMED'
          AND s.updatedAt >= :startOfDay
          AND s.updatedAt < :endOfDay
    """)
    long countConfirmedToday(
            @Param("wh") Long wh,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay);

    @Query("""
        SELECT COUNT(s) FROM SalesOrderEntity s
        WHERE (:wh IS NULL OR s.warehouseId = :wh)
          AND s.status = :status
    """)
    long countByStatusAllTime(
            @Param("wh") Long wh,
            @Param("status") String status);
}