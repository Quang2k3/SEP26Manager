package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.GrnEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GrnJpaRepository extends JpaRepository<GrnEntity, Long> {
    Page<GrnEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<GrnEntity> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<GrnEntity> findByStatus(String status, Pageable pageable);

    Page<GrnEntity> findByWarehouseIdOrderByCreatedAtDesc(Long warehouseId, Pageable pageable);

    Page<GrnEntity> findByWarehouseIdAndStatusOrderByCreatedAtDesc(Long warehouseId, String status, Pageable pageable);

    List<GrnEntity> findByReceivingIdOrderByCreatedAtDesc(Long receivingId);
}