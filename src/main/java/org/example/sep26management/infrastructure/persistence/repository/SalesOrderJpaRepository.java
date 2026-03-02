// ===== SalesOrderJpaRepository.java =====
package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.SalesOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SalesOrderJpaRepository extends JpaRepository<SalesOrderEntity, Long> {

    Optional<SalesOrderEntity> findBySoCode(String soCode);

    boolean existsBySoCode(String soCode);

    /** Count today's sales orders for document code generation — BR-OUT-05 */
    @Query("""
        SELECT COUNT(s) FROM SalesOrderEntity s
        WHERE s.createdAt >= :startOfDay
          AND s.createdAt < :endOfDay
          AND s.warehouseId = :warehouseId
        """)
    long countTodayByWarehouse(
            @Param("warehouseId") Long warehouseId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay
    );

    List<SalesOrderEntity> findByWarehouseIdAndStatus(Long warehouseId, String status);
}