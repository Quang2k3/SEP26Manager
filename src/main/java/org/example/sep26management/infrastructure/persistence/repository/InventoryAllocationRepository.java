package org.example.sep26management.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.example.sep26management.infrastructure.persistence.entity.InventorySnapshotEntity;
import org.example.sep26management.infrastructure.persistence.entity.InventorySnapshotId;

import java.math.BigDecimal;
import java.util.List;

/**
 * FEFO allocation queries for UC-WXE-05
 * BR-WXE-18: FEFO — First Expiry First Out
 * BR-WXE-19: only unallocated (available) stock
 */
@Repository
public interface InventoryAllocationRepository
        extends JpaRepository<InventorySnapshotEntity, InventorySnapshotId> {

    /**
     * BR-WXE-18/19: Get available stock for a SKU sorted by FEFO
     * Returns (snapshot, lot, location) ordered by expiry_date ASC NULLS LAST
     * Only active BIN locations
     */
    @Query("""
            SELECT s.locationId, s.lotId, l.expiryDate, s.quantity, s.reservedQty,
                   (s.quantity - s.reservedQty) AS availableQty,
                   loc.locationCode, z.zoneCode
            FROM InventorySnapshotEntity s
            JOIN InventoryLotEntity l ON l.lotId = s.lotId
            JOIN LocationEntity loc ON loc.locationId = s.locationId
            JOIN ZoneEntity z ON z.zoneId = loc.zoneId
            WHERE s.warehouseId = :warehouseId
              AND s.skuId = :skuId
              AND (s.quantity - s.reservedQty) > 0
              AND loc.active = true
              AND (loc.locationType = 'BIN' OR loc.isStaging = true)
            ORDER BY l.expiryDate ASC NULLS LAST, loc.locationCode ASC
            """)
    List<FEFOAllocationProjection> findAvailableStockFEFO(
            @Param("warehouseId") Long warehouseId,
            @Param("skuId") Long skuId);

    /**
     * Same query but without lot filter (for SKUs without lot tracking)
     */
    @Query("""
            SELECT s.locationId, s.lotId, null AS expiryDate, s.quantity, s.reservedQty,
                   (s.quantity - s.reservedQty) AS availableQty,
                   loc.locationCode, z.zoneCode
            FROM InventorySnapshotEntity s
            LEFT JOIN InventoryLotEntity l ON l.lotId = s.lotId
            JOIN LocationEntity loc ON loc.locationId = s.locationId
            JOIN ZoneEntity z ON z.zoneId = loc.zoneId
            WHERE s.warehouseId = :warehouseId
              AND s.skuId = :skuId
              AND (s.quantity - s.reservedQty) > 0
              AND loc.active = true
              AND (loc.locationType = 'BIN' OR loc.isStaging = true)
            ORDER BY l.expiryDate ASC NULLS LAST, loc.locationCode ASC
            """)
    List<FEFOAllocationProjection> findAvailableStockFEFONoLot(
            @Param("warehouseId") Long warehouseId,
            @Param("skuId") Long skuId);

    interface FEFOAllocationProjection {
        Long getLocationId();
        Long getLotId();
        java.time.LocalDate getExpiryDate();
        BigDecimal getQuantity();
        BigDecimal getReservedQty();
        BigDecimal getAvailableQty();
        String getLocationCode();
        String getZoneCode();
    }
}