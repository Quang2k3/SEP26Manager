package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.InventorySnapshotEntity;
import org.example.sep26management.infrastructure.persistence.entity.InventorySnapshotId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public interface InventorySnapshotJpaRepository
        extends JpaRepository<InventorySnapshotEntity, InventorySnapshotId> {

    /** Single location — total occupied qty */
    @Query("SELECT COALESCE(SUM(s.quantity), 0) FROM InventorySnapshotEntity s WHERE s.locationId = :locationId")
    BigDecimal sumQuantityByLocationId(@Param("locationId") Long locationId);

    /** Single location — total reserved qty */
    @Query("SELECT COALESCE(SUM(s.reservedQty), 0) FROM InventorySnapshotEntity s WHERE s.locationId = :locationId")
    BigDecimal sumReservedByLocationId(@Param("locationId") Long locationId);

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
     * UC-LOC-06 3c: detailed inventory items in a bin
     * Joins with skus and inventory_lots for full detail
     */
    @Query("""
            SELECT s.locationId, s.skuId, sk.skuCode, sk.skuName,
                   s.lotId, l.lotNumber, l.expiryDate,
                   s.quantity, s.reservedQty
            FROM InventorySnapshotEntity s
            JOIN SkuEntity sk ON sk.skuId = s.skuId
            LEFT JOIN InventoryLotEntity l ON l.lotId = s.lotId
            WHERE s.locationId = :locationId
              AND s.quantity > 0
            ORDER BY l.expiryDate ASC NULLS LAST
            """)
    List<BinInventoryProjection> findDetailByLocationId(@Param("locationId") Long locationId);

    // ─── Default methods to convert raw Object[] to Map ───

    default Map<Long, BigDecimal> sumQuantityByLocationIds(List<Long> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) return java.util.Collections.emptyMap();
        return sumQuantityGroupedByLocationIds(locationIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (BigDecimal) row[1]));
    }

    default Map<Long, BigDecimal> sumReservedByLocationIds(List<Long> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) return java.util.Collections.emptyMap();
        return sumReservedGroupedByLocationIds(locationIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (BigDecimal) row[1]));
    }

    /** Projection interface for bin detail query */
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

    /** Total quantity for a SKU across all locations in warehouse */
    @Query("""
            SELECT COALESCE(SUM(s.quantity), 0)
            FROM InventorySnapshotEntity s
            WHERE s.warehouseId = :warehouseId AND s.skuId = :skuId
            """)
    BigDecimal sumQuantityByWarehouseAndSku(
            @Param("warehouseId") Long warehouseId,
            @Param("skuId") Long skuId);

    /** Total reserved for a SKU across all locations in warehouse */
    @Query("""
            SELECT COALESCE(SUM(s.reservedQty), 0)
            FROM InventorySnapshotEntity s
            WHERE s.warehouseId = :warehouseId AND s.skuId = :skuId
            """)
    BigDecimal sumReservedByWarehouseAndSku(
            @Param("warehouseId") Long warehouseId,
            @Param("skuId") Long skuId);

    /** Increment reserved_qty for a SKU — called on approval (BR-OUT-17) */
    @Modifying
    @Query(value = """
            UPDATE inventory_snapshot
            SET reserved_qty = reserved_qty + :qty, last_updated = NOW()
            WHERE warehouse_id = :warehouseId AND sku_id = :skuId
            """, nativeQuery = true)
    void incrementReservedByWarehouseAndSku(
            @Param("warehouseId") Long warehouseId,
            @Param("skuId") Long skuId,
            @Param("qty") java.math.BigDecimal qty);
}