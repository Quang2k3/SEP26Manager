package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.application.enums.LocationType;
import org.example.sep26management.infrastructure.persistence.entity.LocationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LocationJpaRepository extends JpaRepository<LocationEntity, Long> {

    /** BR-LOC-05: unique location_code within zone */
    boolean existsByZoneIdAndLocationCode(Long zoneId, String locationCode);

    Optional<LocationEntity> findByZoneIdAndLocationCode(Long zoneId, String locationCode);

    /** BR-LOC-13: check for active child locations before deactivation */
    boolean existsByParentLocationIdAndActiveTrue(Long parentLocationId);

    /** Count active children — used in deactivation guard */
    long countByParentLocationIdAndActiveTrue(Long parentLocationId);

    List<LocationEntity> findByParentLocationId(Long parentLocationId);

    List<LocationEntity> findByZoneId(Long zoneId);

    List<LocationEntity> findByWarehouseId(Long warehouseId);

    /**
     * UC-LOC-05: View Location List with search + filter
     * Supports keyword (code or name search via code), locationType filter, active filter
     * BR-LOC-16: show parent-child relationships
     * BR-LOC-17: inactive locations must remain visible
     */
    @Query("""
            SELECT l FROM LocationEntity l
            WHERE l.warehouseId = :warehouseId
              AND (:zoneId IS NULL OR l.zoneId = :zoneId)
              AND (:locationType IS NULL OR l.locationType = :locationType)
              AND (:active IS NULL OR l.active = :active)
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(l.locationCode) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY l.locationCode ASC
            """)
    Page<LocationEntity> searchLocations(
            @Param("warehouseId") Long warehouseId,
            @Param("zoneId") Long zoneId,
            @Param("locationType") LocationType locationType,
            @Param("active") Boolean active,
            @Param("keyword") String keyword,
            Pageable pageable);

    /**
     * BR-LOC-12: check if location has inventory before deactivation
     * Uses inventory_snapshot — sum qty > 0 means location has stock
     */
    @Query("""
            SELECT COALESCE(SUM(s.quantity), 0) > 0
            FROM InventorySnapshotEntity s
            WHERE s.locationId = :locationId
              AND s.quantity > 0
            """)
    boolean hasInventory(@Param("locationId") Long locationId);

    /**
     * BR-LOC-09: current occupied quantity for capacity check on update
     * Returns total quantity stored at this location
     */
    @Query("""
            SELECT COALESCE(SUM(s.quantity), 0)
            FROM InventorySnapshotEntity s
            WHERE s.locationId = :locationId
            """)
    java.math.BigDecimal getCurrentOccupiedQty(@Param("locationId") Long locationId);
}