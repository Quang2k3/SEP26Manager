package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.InventoryLotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryLotJpaRepository extends JpaRepository<InventoryLotEntity, Long> {

    List<InventoryLotEntity> findBySkuId(Long skuId);

    Optional<InventoryLotEntity> findBySkuIdAndLotNumber(
            Long skuId,
            String lotNumber);
}