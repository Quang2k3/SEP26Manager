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
    //
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<LocationResponse> createLocation(
            CreateLocationRequest request,
            Long warehouseId,
            Long createdBy,
            String ipAddress,
            String userAgent) {

        log.info("Creating location: code={}, type={}, zone={}, warehouse={}",
                request.getLocationCode(), request.getLocationType(),
                request.getZoneId(), warehouseId);

        // Validate warehouse exists
        warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.WAREHOUSE_NOT_FOUND, warehouseId)));

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

        // BR-LOC-NEW-01: 1 AISLE tối đa 3 RACK
        if (request.getLocationType() == LocationType.RACK) {
            long rackCount = locationRepository.countByParentLocationIdAndLocationType(
                    request.getParentLocationId(), LocationType.RACK);
            if (rackCount >= 3) {
                throw new BusinessException(MessageConstants.LOCATION_RACK_LIMIT_EXCEEDED);
            }
            // Tự động gán capacity RACK theo chuẩn (= tổng 9 BIN):
            // max_weight_kg = 3×512 + 3×448 + 3×400 = 4.080 kg
            // max_volume_m3 = 3×1.050 + 3×0.920 + 3×0.820 = 8.370 m³
            // Không cho phép nhập tay — cố định để tránh sai
            request.setMaxWeightKg(new java.math.BigDecimal("4080.000"));
            request.setMaxVolumeM3(new java.math.BigDecimal("8.370"));
        }

        // BR-LOC-NEW-02: 1 RACK tối đa 9 BIN (3 tầng × 3 cột)
        if (request.getLocationType() == LocationType.BIN) {
            long binCount = locationRepository.countByParentLocationIdAndLocationType(
                    request.getParentLocationId(), LocationType.BIN);
            if (binCount >= 9) {
                throw new BusinessException(MessageConstants.LOCATION_BIN_LIMIT_EXCEEDED);
            }
        }

        // BR-LOC-NEW-03: BIN phải có tầng (binFloor) và cột (binColumn)
        java.math.BigDecimal resolvedWeight = request.getMaxWeightKg();
        Integer resolvedFloor = null;
        Integer resolvedColumn = null;
        if (request.getLocationType() == LocationType.BIN) {
            // Validate floor
            if (request.getBinFloor() == null) {
                throw new BusinessException(MessageConstants.LOCATION_BIN_FLOOR_REQUIRED);
            }
            if (request.getBinFloor() < 1 || request.getBinFloor() > 3) {
                throw new BusinessException(MessageConstants.LOCATION_BIN_FLOOR_INVALID);
            }
            // Validate column
            if (request.getBinColumn() == null) {
                throw new BusinessException(MessageConstants.LOCATION_BIN_COLUMN_REQUIRED);
            }
            if (request.getBinColumn() < 1 || request.getBinColumn() > 3) {
                throw new BusinessException(MessageConstants.LOCATION_BIN_COLUMN_INVALID);
            }
            resolvedFloor  = request.getBinFloor();
            resolvedColumn = request.getBinColumn();

            // BR-LOC-NEW-04: Mỗi tầng tối đa 3 BIN (3 cột)
            long floorCount = locationRepository.countByParentLocationIdAndLocationTypeAndBinFloor(
                    request.getParentLocationId(), LocationType.BIN, resolvedFloor);
            if (floorCount >= 3) {
                throw new BusinessException(String.format(
                        MessageConstants.LOCATION_BIN_FLOOR_FULL, resolvedFloor));
            }

            // BR-LOC-NEW-05: Ô (floor × column) chưa có BIN nào
            long slotCount = locationRepository.countByParentLocationIdAndLocationTypeAndBinFloorAndBinColumn(
                    request.getParentLocationId(), LocationType.BIN, resolvedFloor, resolvedColumn);
            if (slotCount > 0) {
                throw new BusinessException(String.format(
                        MessageConstants.LOCATION_BIN_SLOT_DUPLICATE, resolvedFloor, resolvedColumn));
            }

            resolvedFloor = request.getBinFloor();
            // Tự động gán max_weight_kg và max_volume_m3 theo tầng nếu người dùng không nhập.
            // Cơ sở: 1 BIN chứa thùng chuẩn SKU001 (16kg, ~0.02625 m³), hệ số lấp đầy 80%.
            // Tầng 1 (dưới): 32 thùng → 512 kg, 1.050 m³
            // Tầng 2 (giữa): 28 thùng → 448 kg, 0.920 m³
            // Tầng 3 (trên):  25 thùng → 400 kg, 0.820 m³
            if (resolvedWeight == null) {
                resolvedWeight = switch (resolvedFloor) {
                    case 1  -> new java.math.BigDecimal("512.000");
                    case 2  -> new java.math.BigDecimal("448.000");
                    default -> new java.math.BigDecimal("400.000"); // tầng 3
                };
            }
            if (request.getMaxVolumeM3() == null) {
                request.setMaxVolumeM3(switch (resolvedFloor) {
                    case 1  -> new java.math.BigDecimal("1.050");
                    case 2  -> new java.math.BigDecimal("0.920");
                    default -> new java.math.BigDecimal("0.820"); // tầng 3
                });
            }
        }

        LocationEntity location = LocationEntity.builder()
                .warehouseId(warehouseId)
                .zoneId(request.getZoneId())
                .locationCode(request.getLocationCode().toUpperCase().trim())
                .locationType(request.getLocationType())
                .parentLocationId(request.getParentLocationId())
                .maxWeightKg(resolvedWeight)
                .maxVolumeM3(request.getMaxVolumeM3())
                .isPickingFace(request.getIsPickingFace() != null ? request.getIsPickingFace() : false)
                .isStaging(request.getIsStaging() != null ? request.getIsStaging() : false)
                .isDefect(request.getIsDefect() != null ? request.getIsDefect() : false)
                .binFloor(resolvedFloor)
                .binColumn(resolvedColumn)
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

        // BR-LOC-CAPACITY-LOCK: BIN và RACK không được sửa tải trọng/thể tích — cố định theo chuẩn
        // BIN: cố định theo tầng (T1=512kg, T2=448kg, T3=400kg)
        // RACK: cố định = tổng 9 BIN (4080kg, 8.370m³)
        if ((location.getLocationType() == LocationType.BIN
                || location.getLocationType() == LocationType.RACK)
                && (request.getMaxWeightKg() != null || request.getMaxVolumeM3() != null)) {
            throw new BusinessException(MessageConstants.LOCATION_BIN_CAPACITY_LOCKED);
        }

        // BR-LOC-09: new capacity must be >= current occupied qty (chỉ áp dụng AISLE/RACK)
        if (request.getMaxWeightKg() != null) {
            var currentQty = locationRepository.getCurrentOccupiedQty(locationId);
            if (currentQty != null && request.getMaxWeightKg().compareTo(currentQty) < 0) {
                throw new BusinessException(MessageConstants.LOCATION_CAPACITY_BELOW_CURRENT);
            }
        }

        // Apply updates (locationCode, zone, type, parent, weight, volume đều immutable cho BIN)
        if (request.getMaxWeightKg() != null)  location.setMaxWeightKg(request.getMaxWeightKg());
        if (request.getMaxVolumeM3() != null)   location.setMaxVolumeM3(request.getMaxVolumeM3());
        if (request.getIsPickingFace() != null) location.setIsPickingFace(request.getIsPickingFace());
        if (request.getIsStaging() != null)     location.setIsStaging(request.getIsStaging());

        LocationEntity updated = locationRepository.save(location);

        log.info("Location updated: locationId={}", updated.getLocationId());

        auditLogService.logAction(
                updatedBy, "LOCATION_UPDATED", "LOCATION", updated.getLocationId(),
                String.format("Location %s updated", updated.getLocationCode()),
                ipAddress, userAgent);

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

        return ApiResponse.success(MessageConstants.LOCATION_DEACTIVATED_SUCCESS, null);
    }

    // ─────────────────────────────────────────────────────────────
    // Reactivate Location
    // ─────────────────────────────────────────────────────────────
    @Transactional
    public ApiResponse<Void> reactivateLocation(
            Long locationId,
            Long reactivatedBy,
            String ipAddress,
            String userAgent) {

        log.info("Reactivating location: locationId={}", locationId);

        LocationEntity location = locationRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.LOCATION_NOT_FOUND, locationId)));

        if (Boolean.TRUE.equals(location.getActive())) {
            throw new BusinessException(MessageConstants.LOCATION_ALREADY_ACTIVE);
        }

        // Kiểm tra zone cha còn active không
        var zone = zoneRepository.findById(location.getZoneId()).orElse(null);
        if (zone != null && !Boolean.TRUE.equals(zone.getActive())) {
            throw new BusinessException("Không thể mở lại location vì zone '" + zone.getZoneCode() + "' đang bị vô hiệu hóa.");
        }

        location.setActive(true);
        locationRepository.save(location);

        log.info("Location reactivated: locationId={}, code={}", locationId, location.getLocationCode());

        auditLogService.logAction(
                reactivatedBy, "LOCATION_REACTIVATED", "LOCATION", locationId,
                String.format("Location %s reactivated", location.getLocationCode()),
                ipAddress, userAgent);

        return ApiResponse.success(MessageConstants.LOCATION_REACTIVATED_SUCCESS, null);
    }

    // ─────────────────────────────────────────────────────────────
    // UC-LOC-05: List Locations
    //
    // ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<PageResponse<LocationResponse>> listLocations(
            Long warehouseId,
            Long zoneId,
            LocationType locationType,
            Boolean active,
            Long parentLocationId,
            String keyword,
            int page,
            int size) {

        log.info("Listing locations: warehouse={}, zone={}, type={}, active={}, parent={}, keyword={}",
                warehouseId, zoneId, locationType, active, parentLocationId, keyword);

        Pageable pageable = PageRequest.of(page, size > 0 ? size : 20);
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;

        Page<LocationEntity> locationPage = locationRepository.searchLocations(
                warehouseId, zoneId, locationType, active, parentLocationId, kw, pageable);

        // Batch-resolve zone codes and parent codes
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

        if (parent.getLocationType() != expectedParent) {
            throw new BusinessException(
                    String.format(MessageConstants.LOCATION_INVALID_PARENT_TYPE,
                            type.name(), expectedParent.name(), parent.getLocationType().name()));
        }

        if (!parent.getZoneId().equals(zoneId)) {
            throw new BusinessException(MessageConstants.LOCATION_PARENT_DIFFERENT_ZONE);
        }

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
        // Tính số thùng tối đa ước tính (chuẩn thùng 16kg):
        //   BIN: max_weight_kg ÷ 16
        //   RACK: tổng 9 BIN = (3×512 + 3×448 + 3×400) ÷ 16 = 255 thùng
        Integer maxBoxCount = null;
        if (l.getMaxWeightKg() != null &&
                (l.getLocationType() == LocationType.BIN
                        || l.getLocationType() == LocationType.RACK)) {
            maxBoxCount = l.getMaxWeightKg()
                    .divide(new java.math.BigDecimal("16"), java.math.RoundingMode.FLOOR)
                    .intValue();
        }
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
                .isDefect(l.getIsDefect())
                .binFloor(l.getBinFloor())
                .binColumn(l.getBinColumn())
                .maxBoxCount(maxBoxCount)
                .active(l.getActive())
                .createdAt(l.getCreatedAt())
                .updatedAt(l.getUpdatedAt())
                .build();
    }
}