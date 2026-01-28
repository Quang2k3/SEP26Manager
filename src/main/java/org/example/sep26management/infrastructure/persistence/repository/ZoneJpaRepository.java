package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.domain.enums.ZoneType;
import org.example.sep26management.infrastructure.persistence.entity.ZoneEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZoneJpaRepository extends JpaRepository<ZoneEntity, Long> {

    Optional<ZoneEntity> findByZoneCode(String zoneCode);

    boolean existsByZoneCode(String zoneCode);

    List<ZoneEntity> findByWarehouseCode(String warehouseCode);

    List<ZoneEntity> findByZoneType(ZoneType zoneType);

    List<ZoneEntity> findByIsActive(Boolean isActive);
}
