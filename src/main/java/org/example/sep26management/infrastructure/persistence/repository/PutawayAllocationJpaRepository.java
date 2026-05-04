package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.PutawayAllocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface PutawayAllocationJpaRepository extends JpaRepository<PutawayAllocationEntity, Long> {

    List<PutawayAllocationEntity> findByPutawayTaskIdAndStatus(Long putawayTaskId, String status);

    List<PutawayAllocationEntity> findByPutawayTaskId(Long putawayTaskId);

    /** Tổng qty đã allocate (RESERVED) cho 1 SKU trong 1 task */
    @Query("SELECT COALESCE(SUM(a.allocatedQty), 0) FROM PutawayAllocationEntity a " +
           "WHERE a.putawayTaskId = :taskId AND a.skuId = :skuId AND a.status = 'RESERVED'")
    BigDecimal sumReservedQtyByTaskAndSku(@Param("taskId") Long taskId, @Param("skuId") Long skuId);

    /** Tổng qty đã allocate (RESERVED) cho 1 bin */
    @Query("SELECT COALESCE(SUM(a.allocatedQty), 0) FROM PutawayAllocationEntity a " +
           "WHERE a.locationId = :locationId AND a.status = 'RESERVED'")
    BigDecimal sumReservedQtyByLocation(@Param("locationId") Long locationId);

    /** Tổng kg RESERVED putaway tại bin = SUM(allocatedQty × weightPerCartonKg), SKU có cấu hình kg */
    @Query("""
            SELECT COALESCE(SUM(a.allocatedQty * sk.weightPerCartonKg), 0)
            FROM PutawayAllocationEntity a
            JOIN SkuEntity sk ON sk.skuId = a.skuId
            WHERE a.locationId = :locationId AND a.status = 'RESERVED'
              AND sk.weightPerCartonKg IS NOT NULL
            """)
    BigDecimal sumReservedWeightKgByLocation(@Param("locationId") Long locationId);

    /** Batch — tổng kg RESERVED putaway per location */
    @Query("""
            SELECT a.locationId, COALESCE(SUM(a.allocatedQty * sk.weightPerCartonKg), 0)
            FROM PutawayAllocationEntity a
            JOIN SkuEntity sk ON sk.skuId = a.skuId
            WHERE a.locationId IN :locationIds AND a.status = 'RESERVED'
              AND sk.weightPerCartonKg IS NOT NULL
            GROUP BY a.locationId
            """)
    List<Object[]> sumReservedWeightKgGroupedByLocationIds(@Param("locationIds") List<Long> locationIds);

    default java.util.Map<Long, BigDecimal> sumReservedWeightKgByLocationIds(List<Long> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) return java.util.Collections.emptyMap();
        return sumReservedWeightKgGroupedByLocationIds(locationIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (BigDecimal) row[1]));
    }

    /** Batch: tổng qty RESERVED per location (cho BinService occupancy) */
    @Query("SELECT a.locationId, COALESCE(SUM(a.allocatedQty), 0) FROM PutawayAllocationEntity a " +
           "WHERE a.locationId IN :locationIds AND a.status = 'RESERVED' GROUP BY a.locationId")
    List<Object[]> sumReservedGroupedByLocationIds(@Param("locationIds") List<Long> locationIds);

    default java.util.Map<Long, BigDecimal> sumReservedByLocationIds(List<Long> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) return java.util.Collections.emptyMap();
        return sumReservedGroupedByLocationIds(locationIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (BigDecimal) row[1]));
    }
}
