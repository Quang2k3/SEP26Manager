package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.QcInspectionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QcInspectionJpaRepository extends JpaRepository<QcInspectionEntity, Long> {

    Page<QcInspectionEntity> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<QcInspectionEntity> findByWarehouseIdAndStatusOrderByCreatedAtDesc(Long warehouseId, String status,
            Pageable pageable);

    Page<QcInspectionEntity> findByLotIdOrderByCreatedAtDesc(Long lotId, Pageable pageable);

    Page<QcInspectionEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
