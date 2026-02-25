package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.WarehouseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WarehouseJpaRepository extends JpaRepository<WarehouseEntity, Long> {

    Optional<WarehouseEntity> findByWarehouseCode(String warehouseCode);
}
