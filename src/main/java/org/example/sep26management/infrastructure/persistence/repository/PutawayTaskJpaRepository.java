package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.PutawayTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PutawayTaskJpaRepository extends JpaRepository<PutawayTaskEntity, Long> {

    List<PutawayTaskEntity> findByAssignedToAndStatusOrderByCreatedAtDesc(Long userId, String status);

    List<PutawayTaskEntity> findByWarehouseIdAndStatusOrderByCreatedAtDesc(Long warehouseId, String status);

    List<PutawayTaskEntity> findAllByOrderByCreatedAtDesc();

    Optional<PutawayTaskEntity> findByReceivingId(Long receivingId);
}
