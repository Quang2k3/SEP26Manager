package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.ZoneEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZoneJpaRepository extends JpaRepository<ZoneEntity, Long> {

    List<ZoneEntity> findByWarehouseId(Long warehouseId);

    List<ZoneEntity> findByWarehouseIdAndActiveTrue(Long warehouseId);

    /**
     * Convention-based: find zone by warehouse + zone_code
     * zone_code = "Z-" + category_code
     */
    Optional<ZoneEntity> findByWarehouseIdAndZoneCode(Long warehouseId, String zoneCode);
}