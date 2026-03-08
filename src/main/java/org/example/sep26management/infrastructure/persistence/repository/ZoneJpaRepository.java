package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.ZoneEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ZoneJpaRepository extends JpaRepository<ZoneEntity, Long> {

    Page<ZoneEntity> findByWarehouseId(Long warehouseId, Pageable pageable);

    Page<ZoneEntity> findByWarehouseIdAndActiveTrue(Long warehouseId, Pageable pageable);

    /**
     * Convention-based: find zone by warehouse + zone_code
     * zone_code = "Z-" + category_code
     */
    Optional<ZoneEntity> findByWarehouseIdAndZoneCode(Long warehouseId, String zoneCode);

    boolean existsByWarehouseIdAndZoneCode(Long warehouseId, String zoneCode);
}