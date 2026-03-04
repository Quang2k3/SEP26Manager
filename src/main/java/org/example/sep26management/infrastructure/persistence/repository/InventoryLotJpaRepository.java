package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.InventoryLotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryLotJpaRepository extends JpaRepository<InventoryLotEntity, Long> {
    List<InventoryLotEntity> findBySkuId(Long skuId);
}