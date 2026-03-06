package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.IncidentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentJpaRepository extends JpaRepository<IncidentEntity, Long> {

    List<IncidentEntity> findByWarehouseIdAndStatusOrderByCreatedAtDesc(Long warehouseId, String status);

    List<IncidentEntity> findByStatusOrderByCreatedAtDesc(String status);

    List<IncidentEntity> findByReceivingIdOrderByCreatedAtDesc(Long receivingId);

    List<IncidentEntity> findAllByOrderByCreatedAtDesc();
}
