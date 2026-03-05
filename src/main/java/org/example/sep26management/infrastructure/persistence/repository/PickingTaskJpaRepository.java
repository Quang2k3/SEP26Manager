package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.PickingTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
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
}