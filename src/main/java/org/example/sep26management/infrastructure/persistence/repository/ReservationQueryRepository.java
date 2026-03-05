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
     * Resolve which location holds stock for a reservation's sku+lot
     * Returns the location_id with the most available qty (for pick routing)
     */
    @Query("""
            SELECT s.locationId FROM InventorySnapshotEntity s
            WHERE s.warehouseId = :warehouseId
              AND s.skuId = :skuId
              AND (:lotId IS NULL OR s.lotId = :lotId)
              AND (s.quantity - s.reservedQty) > 0
            ORDER BY (s.quantity - s.reservedQty) DESC
            """)
    List<Long> findLocationForReservationList(
            @Param("warehouseId") Long warehouseId,
            @Param("skuId") Long skuId,
            @Param("lotId") Long lotId);

    default Long findLocationForReservation(Long warehouseId, Long skuId, Long lotId) {
        List<Long> results = findLocationForReservationList(warehouseId, skuId, lotId);
        return results.isEmpty() ? null : results.get(0);
    }
}
