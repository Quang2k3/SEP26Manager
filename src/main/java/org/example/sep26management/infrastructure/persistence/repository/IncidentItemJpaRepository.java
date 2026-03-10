package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.IncidentItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentItemJpaRepository extends JpaRepository<IncidentItemEntity, Long> {
    List<IncidentItemEntity> findByIncidentIncidentId(Long incidentId);
}
