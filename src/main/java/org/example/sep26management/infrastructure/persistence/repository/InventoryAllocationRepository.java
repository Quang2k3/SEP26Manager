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
 *
 * Luồng xuất kho KHÔNG dùng Z-OUT/staging.
 * Allocate → Pick → Dispatch đều làm việc trực tiếp với BIN lưu trữ.
 * Chỉ query location có locationType = BIN và isStaging = false.
 */
@Repository
public interface InventoryAllocationRepository
        extends JpaRepository<InventorySnapshotEntity, InventorySnapshotId> {

    /**
     * BR-WXE-18/19: Lấy stock khả dụng của một SKU theo FEFO (hạn gần nhất trước).
     * Chỉ xét BIN thực (locationType=BIN, isStaging=false, active=true).
     * SKU phải có lot — dùng khi SKU được quản lý theo lô.
     */
    @Query("""
            SELECT s.locationId, s.lotId, l.expiryDate, s.quantity, s.reservedQty,
                   (s.quantity - s.reservedQty) AS availableQty,
                   loc.locationCode, z.zoneCode
            FROM InventorySnapshotEntity s
            JOIN InventoryLotEntity l   ON l.lotId       = s.lotId
            JOIN LocationEntity loc     ON loc.locationId = s.locationId
            JOIN ZoneEntity z           ON z.zoneId       = loc.zoneId
            WHERE s.warehouseId = :warehouseId
              AND s.skuId       = :skuId
              AND (s.quantity - s.reservedQty) > 0
              AND loc.active      = true
              AND loc.isStaging   = false
              AND loc.locationType = org.example.sep26management.application.enums.LocationType.BIN
            ORDER BY l.expiryDate ASC NULLS LAST, loc.locationCode ASC
            """)
    List<FEFOAllocationProjection> findAvailableStockFEFO(
            @Param("warehouseId") Long warehouseId,
            @Param("skuId") Long skuId);

    /**
     * Fallback khi SKU không có lot (LEFT JOIN lot).
     * Chỉ xét BIN thực (locationType=BIN, isStaging=false, active=true).
     */
    @Query("""
            SELECT s.locationId, s.lotId, null AS expiryDate, s.quantity, s.reservedQty,
                   (s.quantity - s.reservedQty) AS availableQty,
                   loc.locationCode, z.zoneCode
            FROM InventorySnapshotEntity s
            LEFT JOIN InventoryLotEntity l ON l.lotId      = s.lotId
            JOIN LocationEntity loc        ON loc.locationId = s.locationId
            JOIN ZoneEntity z              ON z.zoneId       = loc.zoneId
            WHERE s.warehouseId = :warehouseId
              AND s.skuId       = :skuId
              AND (s.quantity - s.reservedQty) > 0
              AND loc.active      = true
              AND loc.isStaging   = false
              AND loc.locationType = org.example.sep26management.application.enums.LocationType.BIN
            ORDER BY loc.locationCode ASC
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