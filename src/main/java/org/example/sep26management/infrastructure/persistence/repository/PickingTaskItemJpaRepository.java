package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.PickingTaskItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PickingTaskItemJpaRepository extends JpaRepository<PickingTaskItemEntity, Long> {

    /**
     * UC-LOC-08 7b: check if bin is locked by an active picking task
     * Active picking statuses: OPEN, IN_PROGRESS
     */
    @Query("""
            SELECT COUNT(i) > 0
            FROM PickingTaskItemEntity i
            JOIN PickingTaskEntity t ON t.pickingTaskId = i.pickingTaskId
            WHERE i.fromLocationId = :locationId
              AND t.status IN ('OPEN', 'IN_PROGRESS')
            """)
    boolean existsActiveTaskForLocation(@Param("locationId") Long locationId);
}