package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.SalesOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboundQueryRepository extends JpaRepository<SalesOrderEntity, Long> {

    List<SalesOrderEntity> findByWarehouseIdOrderByCreatedAtDesc(Long warehouseId);

    List<SalesOrderEntity> findByWarehouseIdAndStatusOrderByCreatedAtDesc(Long warehouseId, String status);

    List<SalesOrderEntity> findByWarehouseIdAndSoCodeContainingIgnoreCaseOrderByCreatedAtDesc(
            Long warehouseId, String keyword);

    List<SalesOrderEntity> findByWarehouseIdAndStatusAndSoCodeContainingIgnoreCaseOrderByCreatedAtDesc(
            Long warehouseId, String status, String keyword);

    long countByWarehouseIdAndStatus(Long warehouseId, String status);
}