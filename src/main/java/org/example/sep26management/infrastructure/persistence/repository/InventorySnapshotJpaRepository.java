package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.InventorySnapshotEntity;
import org.example.sep26management.infrastructure.persistence.entity.InventorySnapshotId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public interface InventorySnapshotJpaRepository
        extends JpaRepository<InventorySnapshotEntity, InventorySnapshotId> {

        // ── Single-location queries (used by BinService.getBinDetail) ────────────

        /** Single location — total occupied qty */
        @Query("SELECT COALESCE(SUM(s.quantity), 0) FROM InventorySnapshotEntity s WHERE s.locationId = :locationId")
        BigDecimal sumQuantityByLocationId(@Param("locationId") Long locationId);

        /** Single location — total reserved qty */
        @Query("SELECT COALESCE(SUM(s.reservedQty), 0) FROM InventorySnapshotEntity s WHERE s.locationId = :locationId")
        BigDecimal sumReservedByLocationId(@Param("locationId") Long locationId);

        // ── Batch-location queries (used by BinService.viewBinOccupancy) ─────────

        /** Batch — occupied qty per location */
        @Query("""
            SELECT s.locationId, COALESCE(SUM(s.quantity), 0)
            FROM InventorySnapshotEntity s
            WHERE s.locationId IN :locationIds
            GROUP BY s.locationId
            """)
        List<Object[]> sumQuantityGroupedByLocationIds(@Param("locationIds") List<Long> locationIds);

        /** Batch — reserved qty per location */
        @Query("""
            SELECT s.locationId, COALESCE(SUM(s.reservedQty), 0)
            FROM InventorySnapshotEntity s
            WHERE s.locationId IN :locationIds
            GROUP BY s.locationId
            """)
        List<Object[]> sumReservedGroupedByLocationIds(@Param("locationIds") List<Long> locationIds);

        /**
         * UC-LOC-06 3c: detailed inventory items in a bin.
         * Joins with skus and inventory_lots for full detail.
         * Used by BinService.getBinDetail → BinInventoryProjection.
         */
        @Query("""
            SELECT s.locationId AS locationId,
                   s.skuId      AS skuId,
                   sk.skuCode   AS skuCode,
                   sk.skuName   AS skuName,
                   s.lotId      AS lotId,
                   l.lotNumber  AS lotNumber,
                   l.expiryDate AS expiryDate,
                   s.quantity   AS quantity,
                   s.reservedQty AS reservedQty
            FROM InventorySnapshotEntity s
            JOIN SkuEntity sk ON sk.skuId = s.skuId
            LEFT JOIN InventoryLotEntity l ON l.lotId = s.lotId
            WHERE s.locationId = :locationId
              AND s.quantity > 0
            ORDER BY l.expiryDate ASC NULLS LAST
            """)
        List<BinInventoryProjection> findDetailByLocationId(@Param("locationId") Long locationId);

        // ── Default helpers to convert Object[] rows → Map ───────────────────────

        default Map<Long, BigDecimal> sumQuantityByLocationIds(List<Long> locationIds) {
                if (locationIds == null || locationIds.isEmpty()) return Collections.emptyMap();
                return sumQuantityGroupedByLocationIds(locationIds).stream()
                        .collect(Collectors.toMap(
                                row -> (Long) row[0],
                                row -> (BigDecimal) row[1]));
        }

        default Map<Long, BigDecimal> sumReservedByLocationIds(List<Long> locationIds) {
                if (locationIds == null || locationIds.isEmpty()) return Collections.emptyMap();
                return sumReservedGroupedByLocationIds(locationIds).stream()
                        .collect(Collectors.toMap(
                                row -> (Long) row[0],
                                row -> (BigDecimal) row[1]));
        }

        // ── Projection interface (MUST stay here — BinService depends on it) ─────

        /** Projection interface for findDetailByLocationId — used by BinService */
        interface BinInventoryProjection {
                Long getLocationId();
                Long getSkuId();
                String getSkuCode();
                String getSkuName();
                Long getLotId();
                String getLotNumber();
                LocalDate getExpiryDate();
                BigDecimal getQuantity();
                BigDecimal getReservedQty();
        }

        // ── Warehouse-level aggregate queries ────────────────────────────────────

        /**
         * Tính tổng tồn kho khả dụng theo warehouse + sku.
         * FIX: chỉ tính hàng ở location ACTIVE + là BIN thực (không staging, không AISLE/RACK).
         * Trước đây query không join locations → tính cả hàng ở location inactive hoặc staging
         * → tồn kho hiển thị cao hơn thực tế.
         */
        @Query("""
            SELECT COALESCE(SUM(s.quantity), 0)
            FROM InventorySnapshotEntity s
            JOIN LocationEntity loc ON loc.locationId = s.locationId
            WHERE s.warehouseId = :warehouseId
              AND s.skuId = :skuId
              AND loc.active = true
              AND loc.isStaging = false
              AND loc.locationType = org.example.sep26management.application.enums.LocationType.BIN
            """)
        BigDecimal sumQuantityByWarehouseAndSku(
                @Param("warehouseId") Long warehouseId,
                @Param("skuId") Long skuId);

        /**
         * Tính tổng reserved_qty theo warehouse + sku.
         * FIX: chỉ tính reserved ở location ACTIVE + BIN thực.
         * Nếu location bị deactivate thì reserved ở đó không được tính vào công thức
         * available = total - reserved → tránh available âm.
         */
        @Query("""
            SELECT COALESCE(SUM(s.reservedQty), 0)
            FROM InventorySnapshotEntity s
            JOIN LocationEntity loc ON loc.locationId = s.locationId
            WHERE s.warehouseId = :warehouseId
              AND s.skuId = :skuId
              AND loc.active = true
              AND loc.isStaging = false
              AND loc.locationType = org.example.sep26management.application.enums.LocationType.BIN
            """)
        BigDecimal sumReservedByWarehouseAndSku(
                @Param("warehouseId") Long warehouseId,
                @Param("skuId") Long skuId);

        // ── Increment reserved (BR-OUT-17 / BR-WXE-20) ───────────────────────────

        @Modifying
        @Query(value = """
            UPDATE inventory_snapshot
            SET reserved_qty = reserved_qty + :qty, last_updated = NOW()
            WHERE warehouse_id = :warehouseId AND sku_id = :skuId
            """, nativeQuery = true)
        void incrementReservedByWarehouseAndSku(
                @Param("warehouseId") Long warehouseId,
                @Param("skuId") Long skuId,
                @Param("qty") BigDecimal qty);

        @Modifying
        @Query(value = """
            UPDATE inventory_snapshot
            SET reserved_qty = reserved_qty + :qty, last_updated = NOW()
            WHERE location_id = :locationId
              AND sku_id = :skuId
              AND (CASE WHEN :lotId IS NULL THEN lot_id IS NULL ELSE lot_id = :lotId END)
            """, nativeQuery = true)
        void incrementReservedByLocationAndSku(
                @Param("locationId") Long locationId,
                @Param("skuId") Long skuId,
                @Param("lotId") Long lotId,
                @Param("qty") BigDecimal qty);

        // ── Upsert + decrement quantity ───────────────────────────────────────────

        @Modifying
        @Transactional
        @Query(value = """
            INSERT INTO inventory_snapshot (warehouse_id, sku_id, lot_id, location_id, quantity, reserved_qty, last_updated)
            VALUES (:warehouseId, :skuId, :lotId, :locationId, :qty, 0, NOW())
            ON CONFLICT (warehouse_id, sku_id, lot_id_safe, location_id)
            DO UPDATE SET quantity = inventory_snapshot.quantity + EXCLUDED.quantity, last_updated = NOW()
            """, nativeQuery = true)
        void upsertInventory(
                @Param("warehouseId") Long warehouseId,
                @Param("skuId") Long skuId,
                @Param("lotId") Long lotId,
                @Param("locationId") Long locationId,
                @Param("qty") BigDecimal qty);

        // [FIX] Tìm locationId thực từ snapshot — dùng khi fromLocationId trong picking item sai/null
        @Query(value = """
            SELECT s.location_id
            FROM inventory_snapshot s
            JOIN locations l ON l.location_id = s.location_id
            WHERE s.warehouse_id = :warehouseId
              AND s.sku_id       = :skuId
              AND (CASE WHEN :lotId IS NULL THEN s.lot_id IS NULL ELSE s.lot_id = :lotId END)
              AND s.quantity > 0
              AND l.active = true
              AND l.is_staging = false
            ORDER BY (s.quantity - COALESCE(s.reserved_qty, 0)) DESC
            LIMIT 1
            """, nativeQuery = true)
        Long findLocationIdByWarehouseSkuLot(
                @Param("warehouseId") Long warehouseId,
                @Param("skuId") Long skuId,
                @Param("lotId") Long lotId);

        @Modifying
        @Transactional
        @Query(value = """
            UPDATE inventory_snapshot
            SET quantity = quantity - :qty, last_updated = NOW()
            WHERE warehouse_id = :warehouseId
              AND sku_id = :skuId
              AND (CASE WHEN :lotId IS NULL THEN lot_id IS NULL ELSE lot_id = :lotId END)
              AND location_id = :locationId
            """, nativeQuery = true)
        void decrementQuantity(
                @Param("warehouseId") Long warehouseId,
                @Param("skuId") Long skuId,
                @Param("lotId") Long lotId,
                @Param("locationId") Long locationId,
                @Param("qty") BigDecimal qty);

        // ── NEW: BR-DISPATCH-01 — Dispatch deduction ─────────────────────────────

        /**
         * BR-DISPATCH-01: Decrease reserved_qty after dispatch confirmation.
         * Called per picking_task_item (locationId + skuId + lotId).
         */
        @Modifying
        @Transactional
        @Query(value = """
            UPDATE inventory_snapshot
            SET reserved_qty  = GREATEST(0, reserved_qty - :qty),
                last_updated  = NOW()
            WHERE location_id = :locationId
              AND sku_id       = :skuId
              AND (CASE WHEN :lotId IS NULL THEN lot_id IS NULL ELSE lot_id = :lotId END)
            """, nativeQuery = true)
        void decrementReservedByLocationSkuLot(
                @Param("locationId") Long locationId,
                @Param("skuId") Long skuId,
                @Param("lotId") Long lotId,
                @Param("qty") BigDecimal qty);
}