package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.QcInspectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QcInspectionJpaRepository extends JpaRepository<QcInspectionEntity, Long> {

    List<QcInspectionEntity> findByStatusOrderByCreatedAtDesc(String status);

    List<QcInspectionEntity> findByWarehouseIdAndStatusOrderByCreatedAtDesc(Long warehouseId, String status);

    List<QcInspectionEntity> findByLotIdOrderByCreatedAtDesc(Long lotId);

    List<QcInspectionEntity> findAllByOrderByCreatedAtDesc();
}
