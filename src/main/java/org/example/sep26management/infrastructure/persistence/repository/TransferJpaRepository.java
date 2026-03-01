package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.TransferEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransferJpaRepository extends JpaRepository<TransferEntity, Long> {

    boolean existsByTransferCode(String transferCode);

    /** Count today's transfers for document code — BR-OUT-05 */
    @Query("""
        SELECT COUNT(t) FROM TransferEntity t
        WHERE t.createdAt >= :startOfDay
          AND t.createdAt < :endOfDay
          AND t.fromWarehouseId = :warehouseId
        """)
    long countTodayByWarehouse(
            @Param("warehouseId") Long warehouseId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay
    );

    List<TransferEntity> findByFromWarehouseIdAndStatus(Long warehouseId, String status);
}