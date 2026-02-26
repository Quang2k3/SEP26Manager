package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.SkuThresholdEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SkuThresholdJpaRepository extends JpaRepository<SkuThresholdEntity, Long> {

    Optional<SkuThresholdEntity> findByWarehouseIdAndSkuId(Long warehouseId, Long skuId);

    boolean existsByWarehouseIdAndSkuId(Long warehouseId, Long skuId);
}