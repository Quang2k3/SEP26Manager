package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.IncidentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentJpaRepository extends JpaRepository<IncidentEntity, Long> {

    Page<IncidentEntity> findByWarehouseIdAndStatusOrderByCreatedAtDesc(Long warehouseId, String status,
            Pageable pageable);

    Page<IncidentEntity> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    List<IncidentEntity> findByReceivingIdOrderByCreatedAtDesc(Long receivingId);

    Page<IncidentEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
