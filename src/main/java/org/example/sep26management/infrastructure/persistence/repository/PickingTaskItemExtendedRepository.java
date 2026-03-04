package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.PickingTaskItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PickingTaskItemExtendedRepository extends JpaRepository<PickingTaskItemEntity, Long> {
    List<PickingTaskItemEntity> findByPickingTaskId(Long pickingTaskId);
}