package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.ReceivingItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReceivingItemJpaRepository extends JpaRepository<ReceivingItemEntity, Long> {

    List<ReceivingItemEntity> findByReceivingOrderReceivingId(Long receivingId);

    Optional<ReceivingItemEntity> findByReceivingOrderReceivingIdAndSkuId(Long receivingId, Long skuId);
}
