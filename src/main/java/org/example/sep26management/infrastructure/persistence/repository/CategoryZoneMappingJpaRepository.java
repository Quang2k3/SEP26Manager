package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.CategoryZoneMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA Repository for Category-Zone Mappings
 * Uses Spring Data JPA derived query methods for clean, maintainable code
 */
@Repository
public interface CategoryZoneMappingJpaRepository extends JpaRepository<CategoryZoneMappingEntity, Long> {

    /**
     * Find mapping by category and zone
     */
    Optional<CategoryZoneMappingEntity> findByCategoryIdAndZoneId(Long categoryId, Long zoneId);

    /**
     * Find all active mappings for a category
     * Returns mappings ordered by priority (handled in service layer if needed)
     */
    List<CategoryZoneMappingEntity> findByCategoryIdAndIsActiveTrue(Long categoryId);

    /**
     * Find all active mappings for a zone
     * Returns mappings ordered by priority (handled in service layer if needed)
     */
    List<CategoryZoneMappingEntity> findByZoneIdAndIsActiveTrue(Long zoneId);

    /**
     * Check if mapping exists between category and zone
     */
    boolean existsByCategoryIdAndZoneId(Long categoryId, Long zoneId);

    /**
     * Delete mapping by category and zone (soft delete handled in service)
     */
    void deleteByCategoryIdAndZoneId(Long categoryId, Long zoneId);
}
