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
}
