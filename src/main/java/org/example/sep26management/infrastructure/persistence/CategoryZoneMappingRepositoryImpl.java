package org.example.sep26management.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.example.sep26management.domain.entity.CategoryZoneMapping;
import org.example.sep26management.infrastructure.persistence.entity.CategoryZoneMappingEntity;
import org.example.sep26management.infrastructure.persistence.repository.CategoryZoneMappingJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Bridge Repository between Domain and Infrastructure for CategoryZoneMapping
 */
@Repository
@RequiredArgsConstructor
public class CategoryZoneMappingRepositoryImpl {

    private final CategoryZoneMappingJpaRepository jpaRepository;

    /**
     * Save mapping
     */
    public CategoryZoneMapping save(CategoryZoneMapping mapping) {
        CategoryZoneMappingEntity entity = toEntity(mapping);
        CategoryZoneMappingEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    /**
     * Find by ID
     */
    public Optional<CategoryZoneMapping> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    /**
     * Find by category and zone
     */
    public Optional<CategoryZoneMapping> findByCategoryIdAndZoneId(Long categoryId, Long zoneId) {
        return jpaRepository.findByCategoryIdAndZoneId(categoryId, zoneId)
                .map(this::toDomain);
    }

    /**
     * Find mappings by category
     */
    public List<CategoryZoneMapping> findByCategoryId(Long categoryId) {
        return jpaRepository.findByCategoryIdAndIsActiveTrue(categoryId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * Find mappings by zone
     */
    public List<CategoryZoneMapping> findByZoneId(Long zoneId) {
        return jpaRepository.findByZoneIdAndIsActiveTrue(zoneId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * Find all active mappings
     */
    public List<CategoryZoneMapping> findAllActive() {
        return jpaRepository.findAll().stream()
                .filter(e -> Boolean.TRUE.equals(e.getIsActive()))
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * Check if mapping exists
     */
    public boolean existsByCategoryIdAndZoneId(Long categoryId, Long zoneId) {
        return jpaRepository.existsByCategoryIdAndZoneId(categoryId, zoneId);
    }

    /**
     * Delete mapping
     */
    public void delete(CategoryZoneMapping mapping) {
        CategoryZoneMappingEntity entity = toEntity(mapping);
        jpaRepository.save(entity);
    }

    // ========== Mapping Methods ==========

    /**
     * Convert Entity to Domain
     */
    private CategoryZoneMapping toDomain(CategoryZoneMappingEntity entity) {
        return CategoryZoneMapping.builder()
                .mappingId(entity.getMappingId())
                .categoryId(entity.getCategoryId())
                .zoneId(entity.getZoneId())
                .priority(entity.getPriority())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .createdBy(entity.getCreatedBy())
                .updatedAt(entity.getUpdatedAt())
                .updatedBy(entity.getUpdatedBy())
                .build();
    }

    /**
     * Convert Domain to Entity
     */
    private CategoryZoneMappingEntity toEntity(CategoryZoneMapping domain) {
        return CategoryZoneMappingEntity.builder()
                .mappingId(domain.getMappingId())
                .categoryId(domain.getCategoryId())
                .zoneId(domain.getZoneId())
                .priority(domain.getPriority())
                .isActive(domain.getIsActive())
                .createdAt(domain.getCreatedAt())
                .createdBy(domain.getCreatedBy())
                .updatedAt(domain.getUpdatedAt())
                .updatedBy(domain.getUpdatedBy())
                .build();
    }
}
