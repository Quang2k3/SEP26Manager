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
     * FIX-1: loại trừ staging locations (is_staging=true).
     * FIX-2: loại trừ defect locations (is_defect=true) — hàng lỗi không được pick ra.
     *        Đây là nguyên nhân gốc của bug DEFEQ: bin DEFEQ chưa được đánh dấu is_defect=true
     *        qua getOrCreateDefectBin (vì tên zone không phải "Z-DEFECT") nên query cũ vẫn
     *        trả về location trong DEFEQ zone khi nó là fallback.
     * Returns the location_id with the most available qty (for pick routing — FEFO + most qty first).
     */
    @Query(value = """
            SELECT s.location_id
            FROM inventory_snapshot s
            JOIN locations l ON l.location_id = s.location_id
            JOIN zones z     ON z.zone_id      = l.zone_id
            WHERE s.warehouse_id = :warehouseId
              AND s.sku_id       = :skuId
              AND (:lotId IS NULL OR s.lot_id = :lotId)
              AND (s.quantity - COALESCE(s.reserved_qty, 0)) > 0
              AND l.is_staging   = false
              AND l.is_defect    = false
              AND l.active       = true
              AND z.zone_code    NOT LIKE '%DEFECT%'
              AND z.zone_code    NOT LIKE '%DEFEQ%'
              AND z.zone_code    NOT LIKE '%DAMAGE%'
              AND z.zone_code    NOT LIKE '%HOLD%'
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