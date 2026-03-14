package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.PutawayTaskEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PutawayTaskJpaRepository extends JpaRepository<PutawayTaskEntity, Long> {

    Page<PutawayTaskEntity> findByAssignedToAndStatusOrderByCreatedAtDesc(Long userId, String status,
            Pageable pageable);

    Page<PutawayTaskEntity> findByWarehouseIdAndStatusOrderByCreatedAtDesc(Long warehouseId, String status,
            Pageable pageable);

    Page<PutawayTaskEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<PutawayTaskEntity> findByReceivingId(Long receivingId);

    Optional<PutawayTaskEntity> findByGrnId(Long grnId);
}
