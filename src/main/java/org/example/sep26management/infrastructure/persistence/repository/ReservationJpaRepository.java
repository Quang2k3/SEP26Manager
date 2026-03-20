package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.ReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ReservationJpaRepository extends JpaRepository<ReservationEntity, Long> {

    /** BR-OUT-17: tổng reserved qty của 1 SKU trong warehouse */
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

    /** Idempotency guard trong AllocateStockService */
    List<ReservationEntity> findByReferenceTableAndReferenceIdAndStatus(
            String referenceTable, Long referenceId, String status);

    /**
     * Dùng khi close reservation của Internal Transfer tại confirmPicked.
     * Tìm OPEN reservation theo warehouse + sku + location (không cần reference_id).
     */
    @Query("""
            SELECT r FROM ReservationEntity r
            WHERE r.warehouseId = :warehouseId
              AND r.skuId       = :skuId
              AND r.locationId  = :locationId
              AND r.status      = 'OPEN'
            """)
    List<ReservationEntity> findOpenByWarehouseSkuLocation(
            @Param("warehouseId") Long warehouseId,
            @Param("skuId") Long skuId,
            @Param("locationId") Long locationId);
}