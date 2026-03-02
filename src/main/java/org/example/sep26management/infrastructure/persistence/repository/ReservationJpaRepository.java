package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.ReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface ReservationJpaRepository extends JpaRepository<ReservationEntity, Long> {

    /** BR-OUT-17: total reserved qty for a SKU in warehouse */
    @Query("""
            SELECT COALESCE(SUM(r.quantity), 0)
            FROM ReservationEntity r
            WHERE r.warehouseId = :warehouseId
              AND r.skuId = :skuId
              AND r.status = 'OPEN'
            """)
    BigDecimal sumReservedBySkuAndWarehouse(
            @Param("warehouseId") Long warehouseId,
            @Param("skuId") Long skuId);
}