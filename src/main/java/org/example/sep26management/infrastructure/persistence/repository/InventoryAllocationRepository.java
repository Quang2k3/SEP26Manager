package org.example.sep26management.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
     * FIX: thêm AND loc.isDefect = false (đã có) VÀ filter thêm zone_code
     *      để loại bỏ các zone có tên chứa DEFECT/DEFEQ/DAMAGE dù bin chưa được
     *      đánh dấu is_defect=true bởi getOrCreateDefectBin.
     * SKU phải có lot — dùng khi SKU được quản lý theo lô.
     */
    @Query("""
            SELECT s.locationId AS locationId,
                   s.lotId AS lotId,
                   l.expiryDate AS expiryDate,
                   s.quantity AS quantity,
                   s.reservedQty AS reservedQty,
                   (s.quantity - s.reservedQty) AS availableQty,
                   loc.locationCode AS locationCode,
                   z.zoneCode AS zoneCode
            FROM InventorySnapshotEntity s
            LEFT JOIN InventoryLotEntity l ON l.lotId = s.lotId
            JOIN LocationEntity loc     ON loc.locationId = s.locationId
            JOIN ZoneEntity z           ON z.zoneId       = loc.zoneId
            WHERE s.warehouseId = :warehouseId
              AND s.skuId       = :skuId
              AND (s.quantity - s.reservedQty) > 0
              AND loc.active      = true
              AND loc.isStaging   = false
              AND loc.isDefect    = false
              AND loc.locationType = org.example.sep26management.application.enums.LocationType.BIN
              AND UPPER(z.zoneCode) NOT LIKE '%DEFECT%'
              AND UPPER(z.zoneCode) NOT LIKE '%DEFEQ%'
              AND UPPER(z.zoneCode) NOT LIKE '%DAMAGE%'
            ORDER BY l.expiryDate ASC NULLS LAST, loc.locationCode ASC
            """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<FEFOAllocationProjection> findAvailableStockFEFO(
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