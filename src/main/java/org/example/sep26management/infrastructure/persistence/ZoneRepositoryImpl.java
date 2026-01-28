package org.example.sep26management.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.example.sep26management.domain.entity.Zone;
import org.example.sep26management.infrastructure.persistence.entity.ZoneEntity;
import org.example.sep26management.infrastructure.persistence.repository.ZoneJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ZoneRepositoryImpl {

    private final ZoneJpaRepository jpaRepository;

    public Zone save(Zone zone) {
        ZoneEntity entity = toEntity(zone);
        ZoneEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    public Optional<Zone> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    public Optional<Zone> findByZoneCode(String zoneCode) {
        return jpaRepository.findByZoneCode(zoneCode).map(this::toDomain);
    }

    public List<Zone> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    public List<Zone> findByWarehouseCode(String warehouseCode) {
        return jpaRepository.findByWarehouseCode(warehouseCode).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    public boolean existsByZoneCode(String zoneCode) {
        return jpaRepository.existsByZoneCode(zoneCode);
    }

    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    // Mapper methods
    private Zone toDomain(ZoneEntity entity) {
        return Zone.builder()
                .zoneId(entity.getZoneId())
                .zoneCode(entity.getZoneCode())
                .zoneName(entity.getZoneName())
                .warehouseCode(entity.getWarehouseCode())
                .zoneType(entity.getZoneType())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .createdBy(entity.getCreatedBy())
                .updatedAt(entity.getUpdatedAt())
                .updatedBy(entity.getUpdatedBy())
                .build();
    }

    private ZoneEntity toEntity(Zone domain) {
        return ZoneEntity.builder()
                .zoneId(domain.getZoneId())
                .zoneCode(domain.getZoneCode())
                .zoneName(domain.getZoneName())
                .warehouseCode(domain.getWarehouseCode())
                .zoneType(domain.getZoneType())
                .isActive(domain.getIsActive())
                .createdAt(domain.getCreatedAt())
                .createdBy(domain.getCreatedBy())
                .updatedAt(domain.getUpdatedAt())
                .updatedBy(domain.getUpdatedBy())
                .build();
    }
}
