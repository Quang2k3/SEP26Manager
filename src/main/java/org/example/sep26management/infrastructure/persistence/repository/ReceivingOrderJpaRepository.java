package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.ReceivingOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReceivingOrderJpaRepository extends JpaRepository<ReceivingOrderEntity, Long> {

    List<ReceivingOrderEntity> findByWarehouseIdAndStatusOrderByCreatedAtDesc(Long warehouseId, String status);

    List<ReceivingOrderEntity> findByStatusOrderByCreatedAtDesc(String status);

    List<ReceivingOrderEntity> findAllByOrderByCreatedAtDesc();

    @Query("SELECT MAX(r.receivingCode) FROM ReceivingOrderEntity r WHERE r.warehouseId = :warehouseId AND r.receivingCode LIKE 'GRN%'")
    Optional<String> findMaxReceivingCode(@Param("warehouseId") Long warehouseId);
}
