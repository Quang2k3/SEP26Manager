package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.PutawayTaskItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PutawayTaskItemJpaRepository extends JpaRepository<PutawayTaskItemEntity, Long> {

    List<PutawayTaskItemEntity> findByPutawayTaskPutawayTaskId(Long putawayTaskId);
}
