package org.example.sep26management.infrastructure.persistence.repository;

import io.lettuce.core.dynamic.annotation.Param;
import org.example.sep26management.infrastructure.persistence.entity.PutawayTaskItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PutawayTaskItemJpaRepository extends JpaRepository<PutawayTaskItemEntity, Long> {
    /**
     * UC-LOC-08 7b: check if bin is locked by an active putaway task
     * Active putaway statuses: OPEN, IN_PROGRESS
     */
    @Query("""
    SELECT COUNT(i) > 0
    FROM PutawayTaskItemEntity i
    JOIN i.putawayTask t
    WHERE i.suggestedLocationId = :locationId
      AND t.status IN ('OPEN', 'IN_PROGRESS')
""")
    boolean existsActiveTaskForLocation(@Param("locationId") Long locationId);
    List<PutawayTaskItemEntity> findByPutawayTaskPutawayTaskId(Long putawayTaskId);
}
