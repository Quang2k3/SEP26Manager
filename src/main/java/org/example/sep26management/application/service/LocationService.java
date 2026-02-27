package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.application.dto.request.CreateLocationRequest;
//import org.example.sep26management.application.dto.request.UpdateLocationRequest;
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