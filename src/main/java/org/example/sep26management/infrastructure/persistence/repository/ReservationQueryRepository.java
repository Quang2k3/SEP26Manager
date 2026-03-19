package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.ReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReservationQueryRepository extends JpaRepository<ReservationEntity, Long> {

    /** UC-WXE-06: get all OPEN reservations for a document */
    List<ReservationEntity> findByReferenceTableAndReferenceIdAndStatus(
            String referenceTable, Long referenceId, String status);

    /**
     * Resolve which location holds stock for a reservation's sku+lot.
     * FIX: loại trừ staging locations (is_staging=true) — pick list chỉ lấy từ bin thực,
     * không lấy từ Z-OUT/staging mà hàng chưa được putaway.
     * Returns the location_id with the most available qty (for pick routing — FEFO + most qty first).
     */
    @Query(value = """
            SELECT s.location_id
            FROM inventory_snapshot s
            JOIN locations l ON l.location_id = s.location_id
            WHERE s.warehouse_id = :warehouseId
              AND s.sku_id       = :skuId
              AND (:lotId IS NULL OR s.lot_id = :lotId)
              AND (s.quantity - COALESCE(s.reserved_qty, 0)) > 0
              AND l.is_staging   = false
              AND l.active       = true
            ORDER BY (s.quantity - COALESCE(s.reserved_qty, 0)) DESC
            LIMIT 50
            """, nativeQuery = true)
    List<Long> findLocationForReservationList(
            @Param("warehouseId") Long warehouseId,
            @Param("skuId") Long skuId,
            @Param("lotId") Long lotId);

    default Long findLocationForReservation(Long warehouseId, Long skuId, Long lotId) {
        List<Long> results = findLocationForReservationList(warehouseId, skuId, lotId);
        return results.isEmpty() ? null : results.get(0);
    }
}