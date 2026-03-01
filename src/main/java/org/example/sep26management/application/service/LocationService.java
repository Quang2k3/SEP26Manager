package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.application.dto.request.CreateLocationRequest;
import org.example.sep26management.application.dto.request.UpdateLocationRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.LocationResponse;
import org.example.sep26management.application.dto.response.PageResponse;
import org.example.sep26management.application.enums.LocationType;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.example.sep26management.infrastructure.exception.ResourceNotFoundException;
import org.example.sep26management.infrastructure.persistence.entity.LocationEntity;
import org.example.sep26management.infrastructure.persistence.repository.LocationJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.WarehouseJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.ZoneJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocationService {

    private final LocationJpaRepository locationRepository;
    private final ZoneJpaRepository zoneRepository;
    private final WarehouseJpaRepository warehouseRepository;
    private final AuditLogService auditLogService;

    // ─────────────────────────────────────────────────────────────
    // UC-LOC-02: Create Location
    // BR-LOC-04: hierarchy Zone → Aisle → Rack → Bin
    // BR-LOC-05: location_code unique within zone
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<LocationResponse> createLocation(
            CreateLocationRequest request,
            Long createdBy,
            String ipAddress,
            String userAgent) {

        log.info("Creating location: code={}, type={}, zone={}, warehouse={}",
                request.getLocationCode(), request.getLocationType(),
                request.getZoneId(), request.getWarehouseId());

        // Validate warehouse exists
        warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.WAREHOUSE_NOT_FOUND, request.getWarehouseId())));

        // Validate zone exists and is active
        var zone = zoneRepository.findById(request.getZoneId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.ZONE_NOT_FOUND, request.getZoneId())));

        if (Boolean.FALSE.equals(zone.getActive())) {
            throw new BusinessException(MessageConstants.LOCATION_ZONE_INACTIVE);
        }

        // BR-LOC-05: unique location_code within zone
        if (locationRepository.existsByZoneIdAndLocationCode(
                request.getZoneId(), request.getLocationCode().toUpperCase())) {
            throw new BusinessException(
                    String.format(MessageConstants.LOCATION_CODE_DUPLICATE, request.getLocationCode()));
        }

        // BR-LOC-04: validate hierarchy
        validateHierarchy(request.getLocationType(), request.getParentLocationId(), request.getZoneId());

        LocationEntity location = LocationEntity.builder()
                .warehouseId(request.getWarehouseId())
                .zoneId(request.getZoneId())
                .locationCode(request.getLocationCode().toUpperCase().trim())
                .locationType(request.getLocationType())
                .parentLocationId(request.getParentLocationId())
                .maxWeightKg(request.getMaxWeightKg())
                .maxVolumeM3(request.getMaxVolumeM3())
                .isPickingFace(request.getIsPickingFace() != null ? request.getIsPickingFace() : false)
                .isStaging(request.getIsStaging() != null ? request.getIsStaging() : false)
                .active(true)
                .build();

        LocationEntity saved = locationRepository.save(location);

        log.info("Location created: locationId={}, code={}", saved.getLocationId(), saved.getLocationCode());

        auditLogService.logAction(
                createdBy, "LOCATION_CREATED", "LOCATION", saved.getLocationId(),
                String.format("Location %s (%s) created in zone %d",
                        saved.getLocationCode(), saved.getLocationType(), saved.getZoneId()),
                ipAddress, userAgent);

        return ApiResponse.success(MessageConstants.LOCATION_CREATED_SUCCESS,
                toResponse(saved, zone.getZoneCode(), null));
    }

// ─────────────────────────────────────────────────────────────
    // UC-LOC-03: Update Location
    // BR-LOC-08: location_code immutable
    // BR-LOC-09: new capacity >= current occupied qty
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<LocationResponse> updateLocation(
            Long locationId,
            UpdateLocationRequest request,
            Long updatedBy,
            String ipAddress,
            String userAgent) {

        log.info("Updating location: locationId={}", locationId);

        LocationEntity location = locationRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.LOCATION_NOT_FOUND, locationId)));

        if (Boolean.FALSE.equals(location.getActive())) {
            throw new BusinessException(MessageConstants.LOCATION_ALREADY_INACTIVE);
        }

        // BR-LOC-09: new capacity must be >= current occupied qty
        if (request.getMaxWeightKg() != null) {
            var currentQty = locationRepository.getCurrentOccupiedQty(locationId);
            if (currentQty != null && request.getMaxWeightKg().compareTo(
                    currentQty) < 0) {
                throw new BusinessException(MessageConstants.LOCATION_CAPACITY_BELOW_CURRENT);
            }
        }

        // Apply updates (location_code, zone, type, parent NOT in request = protected)
        if (request.getMaxWeightKg() != null) location.setMaxWeightKg(request.getMaxWeightKg());
        if (request.getMaxVolumeM3() != null)  location.setMaxVolumeM3(request.getMaxVolumeM3());
        if (request.getIsPickingFace() != null) location.setIsPickingFace(request.getIsPickingFace());
        if (request.getIsStaging() != null)     location.setIsStaging(request.getIsStaging());

        LocationEntity updated = locationRepository.save(location);

        log.info("Location updated: locationId={}", updated.getLocationId());

        auditLogService.logAction(
                updatedBy, "LOCATION_UPDATED", "LOCATION", updated.getLocationId(),
                String.format("Location %s updated", updated.getLocationCode()),
                ipAddress, userAgent);

        // Resolve zone code and parent code for response
        String zoneCode = zoneRepository.findById(updated.getZoneId())
                .map(z -> z.getZoneCode()).orElse(null);
        String parentCode = resolveParentCode(updated.getParentLocationId());

        return ApiResponse.success(MessageConstants.LOCATION_UPDATED_SUCCESS,
                toResponse(updated, zoneCode, parentCode));
    }

    // ─────────────────────────────────────────────────────────────
    // UC-LOC-04: Deactivate Location
    // BR-LOC-12: location must be empty (no inventory)
    // BR-LOC-13: no active child locations
    // BR-LOC-14: data preserved for history
    // BR-LOC-15: excluded from putaway/pick/empty-bin after deactivation
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<Void> deactivateLocation(
            Long locationId,
            Long deactivatedBy,
            String ipAddress,
            String userAgent) {

        log.info("Deactivating location: locationId={}", locationId);

        LocationEntity location = locationRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.LOCATION_NOT_FOUND, locationId)));

        if (Boolean.FALSE.equals(location.getActive())) {
            throw new BusinessException(MessageConstants.LOCATION_ALREADY_INACTIVE);
        }

        // BR-LOC-12: must be empty — no inventory in snapshot
        if (locationRepository.hasInventory(locationId)) {
            throw new BusinessException(MessageConstants.LOCATION_HAS_INVENTORY);
        }

        // BR-LOC-13: no active child locations
        if (locationRepository.existsByParentLocationIdAndActiveTrue(locationId)) {
            throw new BusinessException(MessageConstants.LOCATION_HAS_ACTIVE_CHILDREN);
        }

        location.setActive(false);
        locationRepository.save(location);

        log.info("Location deactivated: locationId={}, code={}", locationId, location.getLocationCode());

        auditLogService.logAction(
                deactivatedBy, "LOCATION_DEACTIVATED", "LOCATION", locationId,
                String.format("Location %s deactivated", location.getLocationCode()),
                ipAddress, userAgent);

        return ApiResponse.success(MessageConstants.LOCATION_DEACTIVATED_SUCCESS);
    }

    // ─────────────────────────────────────────────────────────────
    // UC-LOC-05: View Location List
    // Filter: keyword, locationType, active, zoneId
    // BR-LOC-16: parent-child relationships visible
    // BR-LOC-17: inactive locations visible but clearly marked
    // BR-LOC-18: read-only
    // ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<PageResponse<LocationResponse>> listLocations(
            Long warehouseId,
            Long zoneId,
            LocationType locationType,
            Boolean active,
            String keyword,
            int page,
            int size) {

        log.info("Listing locations: warehouse={}, zone={}, type={}, active={}, keyword={}",
                warehouseId, zoneId, locationType, active, keyword);

        Pageable pageable = PageRequest.of(page, size > 0 ? size : 20);
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;

        Page<LocationEntity> locationPage = locationRepository.searchLocations(
                warehouseId, zoneId, locationType, active, kw, pageable);

        // Batch-resolve zone codes and parent codes for response enrichment
        Set<Long> zoneIds = locationPage.getContent().stream()
                .map(LocationEntity::getZoneId).filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, String> zoneCodes = zoneRepository.findAllById(zoneIds).stream()
                .collect(Collectors.toMap(z -> z.getZoneId(), z -> z.getZoneCode()));

        Set<Long> parentIds = locationPage.getContent().stream()
                .map(LocationEntity::getParentLocationId).filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, String> parentCodes = locationRepository.findAllById(parentIds).stream()
                .collect(Collectors.toMap(LocationEntity::getLocationId, LocationEntity::getLocationCode));

        List<LocationResponse> content = locationPage.getContent().stream()
                .map(l -> toResponse(l,
                        l.getZoneId() != null ? zoneCodes.get(l.getZoneId()) : null,
                        l.getParentLocationId() != null ? parentCodes.get(l.getParentLocationId()) : null))
                .toList();

        PageResponse<LocationResponse> pageResponse = PageResponse.<LocationResponse>builder()
                .content(content)
                .page(locationPage.getNumber())
                .size(locationPage.getSize())
                .totalElements(locationPage.getTotalElements())
                .totalPages(locationPage.getTotalPages())
                .last(locationPage.isLast())
                .build();

        return ApiResponse.success(MessageConstants.LOCATION_LIST_SUCCESS, pageResponse);
    }

    // Get single location detail
    @Transactional(readOnly = true)
    public ApiResponse<LocationResponse> getLocationDetail(Long locationId) {
        LocationEntity location = locationRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.LOCATION_NOT_FOUND, locationId)));

        String zoneCode = location.getZoneId() != null
                ? zoneRepository.findById(location.getZoneId()).map(z -> z.getZoneCode()).orElse(null)
                : null;
        String parentCode = resolveParentCode(location.getParentLocationId());

        return ApiResponse.success(MessageConstants.LOCATION_LIST_SUCCESS,
                toResponse(location, zoneCode, parentCode));
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * BR-LOC-04: validate hierarchy
     * AISLE → no parent required (directly under zone)
     * RACK  → parent must be AISLE
     * BIN   → parent must be RACK
     */
    private void validateHierarchy(LocationType type, Long parentLocationId, Long zoneId) {
        LocationType expectedParent = type.expectedParentType();

        if (expectedParent == null) {
            // AISLE: no parent needed
            if (parentLocationId != null) {
                throw new BusinessException(MessageConstants.LOCATION_INVALID_HIERARCHY);
            }
            return;
        }

        // RACK or BIN: must have a parent
        if (parentLocationId == null) {
            throw new BusinessException(
                    String.format(MessageConstants.LOCATION_PARENT_REQUIRED, type.name(), expectedParent.name()));
        }

        LocationEntity parent = locationRepository.findById(parentLocationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.LOCATION_NOT_FOUND, parentLocationId)));

        // Validate parent type matches expected
        if (parent.getLocationType() != expectedParent) {
            throw new BusinessException(
                    String.format(MessageConstants.LOCATION_INVALID_PARENT_TYPE,
                            type.name(), expectedParent.name(), parent.getLocationType().name()));
        }

        // Validate parent is in the same zone
        if (!parent.getZoneId().equals(zoneId)) {
            throw new BusinessException(MessageConstants.LOCATION_PARENT_DIFFERENT_ZONE);
        }

        // Parent must be active
        if (Boolean.FALSE.equals(parent.getActive())) {
            throw new BusinessException(MessageConstants.LOCATION_PARENT_INACTIVE);
        }
    }

    private String resolveParentCode(Long parentLocationId) {
        if (parentLocationId == null) return null;
        return locationRepository.findById(parentLocationId)
                .map(LocationEntity::getLocationCode)
                .orElse(null);
    }

    private LocationResponse toResponse(LocationEntity l, String zoneCode, String parentCode) {
        return LocationResponse.builder()
                .locationId(l.getLocationId())
                .warehouseId(l.getWarehouseId())
                .zoneId(l.getZoneId())
                .zoneCode(zoneCode)
                .locationCode(l.getLocationCode())
                .locationType(l.getLocationType())
                .parentLocationId(l.getParentLocationId())
                .parentLocationCode(parentCode)
                .maxWeightKg(l.getMaxWeightKg())
                .maxVolumeM3(l.getMaxVolumeM3())
                .isPickingFace(l.getIsPickingFace())
                .isStaging(l.getIsStaging())
                .active(l.getActive())
                .createdAt(l.getCreatedAt())
                .updatedAt(l.getUpdatedAt())
                .build();
    }
}