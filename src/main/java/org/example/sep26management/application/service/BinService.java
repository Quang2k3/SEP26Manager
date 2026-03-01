package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.application.dto.request.ConfigureBinCapacityRequest;
import org.example.sep26management.application.dto.response.*;
import org.example.sep26management.application.enums.LocationType;
import org.example.sep26management.application.enums.OccupancyStatus;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.example.sep26management.infrastructure.exception.ResourceNotFoundException;
import org.example.sep26management.infrastructure.persistence.entity.LocationEntity;
import org.example.sep26management.infrastructure.persistence.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BinService {

    private final LocationJpaRepository locationRepository;
    private final ZoneJpaRepository zoneRepository;
    private final InventorySnapshotJpaRepository snapshotRepository;
    private final PutawayTaskItemJpaRepository putawayTaskItemRepository;
    private final PickingTaskItemJpaRepository pickingTaskItemRepository;
    private final AuditLogService auditLogService;

    // ─────────────────────────────────────────────────────────────
    // SCRUM-277: UC-LOC-06 View Bin Occupancy
    // BR-LOC-20: occupancy from inventory_snapshot
    // BR-LOC-21: FULL when occupied >= max capacity
    // BR-LOC-22: inactive bins excluded from putaway (still shown in view with status)
    // BR-LOC-23: real-time data
    // ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<PageResponse<BinOccupancyResponse>> viewBinOccupancy(
            Long warehouseId,
            Long zoneId,
            OccupancyStatus occupancyStatusFilter,
            int page,
            int size) {

        log.info("Viewing bin occupancy: warehouse={}, zone={}, status={}",
                warehouseId, zoneId, occupancyStatusFilter);

        // Fetch all BIN-type locations in warehouse (optionally filtered by zone)
        Page<LocationEntity> binPage = locationRepository.searchLocations(
                warehouseId, zoneId, LocationType.BIN, null, null,
                PageRequest.of(page, size > 0 ? size : 20));

        // Collect all location IDs for batch snapshot query
        List<Long> locationIds = binPage.getContent().stream()
                .map(LocationEntity::getLocationId).toList();

        // Batch-fetch occupancy from inventory_snapshot
        Map<Long, BigDecimal> occupiedMap = snapshotRepository
                .sumQuantityByLocationIds(locationIds);
        Map<Long, BigDecimal> reservedMap = snapshotRepository
                .sumReservedByLocationIds(locationIds);

        // Resolve zone codes and parent codes (batch)
        Set<Long> zoneIds = binPage.getContent().stream()
                .map(LocationEntity::getZoneId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> zoneCodes = zoneRepository.findAllById(zoneIds).stream()
                .collect(Collectors.toMap(z -> z.getZoneId(), z -> z.getZoneCode()));

        Set<Long> parentIds = binPage.getContent().stream()
                .map(LocationEntity::getParentLocationId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, LocationEntity> parentMap = locationRepository.findAllById(parentIds).stream()
                .collect(Collectors.toMap(LocationEntity::getLocationId, l -> l));

        // Build responses and apply occupancy status filter (in-memory after calculation)
        List<BinOccupancyResponse> content = binPage.getContent().stream()
                .map(bin -> {
                    BigDecimal occupied = occupiedMap.getOrDefault(bin.getLocationId(), BigDecimal.ZERO);
                    BigDecimal reserved = reservedMap.getOrDefault(bin.getLocationId(), BigDecimal.ZERO);
                    // BR-LOC-21: use maxWeightKg as primary capacity metric
                    OccupancyStatus status = OccupancyStatus.of(occupied, bin.getMaxWeightKg());

                    BigDecimal available = null;
                    if (bin.getMaxWeightKg() != null) {
                        available = bin.getMaxWeightKg().subtract(occupied).subtract(reserved);
                        if (available.compareTo(BigDecimal.ZERO) < 0) available = BigDecimal.ZERO;
                    }

                    // Resolve parent (RACK) and grandparent (AISLE)
                    LocationEntity rack = bin.getParentLocationId() != null
                            ? parentMap.get(bin.getParentLocationId()) : null;
                    Long aisleId = rack != null ? rack.getParentLocationId() : null;
                    String aisleCode = null;
                    if (aisleId != null) {
                        aisleCode = parentMap.containsKey(aisleId)
                                ? parentMap.get(aisleId).getLocationCode()
                                : locationRepository.findById(aisleId)
                                .map(LocationEntity::getLocationCode).orElse(null);
                    }

                    return BinOccupancyResponse.builder()
                            .locationId(bin.getLocationId())
                            .locationCode(bin.getLocationCode())
                            .zoneId(bin.getZoneId())
                            .zoneCode(bin.getZoneId() != null ? zoneCodes.get(bin.getZoneId()) : null)
                            .parentLocationId(bin.getParentLocationId())
                            .parentLocationCode(rack != null ? rack.getLocationCode() : null)
                            .grandParentLocationId(aisleId)
                            .grandParentLocationCode(aisleCode)
                            .maxWeightKg(bin.getMaxWeightKg())
                            .maxVolumeM3(bin.getMaxVolumeM3())
                            .occupiedQty(occupied)
                            .reservedQty(reserved)
                            .availableQty(available)
                            .occupancyStatus(status)
                            .isPickingFace(bin.getIsPickingFace())
                            .isStaging(bin.getIsStaging())
                            .active(bin.getActive())
                            .build();
                })
                // Apply occupancy status filter after calculation
                .filter(r -> occupancyStatusFilter == null || r.getOccupancyStatus() == occupancyStatusFilter)
                .toList();

        PageResponse<BinOccupancyResponse> pageResponse = PageResponse.<BinOccupancyResponse>builder()
                .content(content)
                .page(binPage.getNumber())
                .size(binPage.getSize())
                .totalElements(binPage.getTotalElements())
                .totalPages(binPage.getTotalPages())
                .last(binPage.isLast())
                .build();

        return ApiResponse.success(MessageConstants.BIN_OCCUPANCY_SUCCESS, pageResponse);
    }

    /**
     * UC-LOC-06 3c: View detailed inventory inside a specific bin
     */
    @Transactional(readOnly = true)
    public ApiResponse<BinOccupancyResponse> getBinDetail(Long locationId) {
        LocationEntity bin = locationRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.LOCATION_NOT_FOUND, locationId)));

        if (bin.getLocationType() != LocationType.BIN) {
            throw new BusinessException(MessageConstants.BIN_ONLY_OPERATION);
        }

        BigDecimal occupied = snapshotRepository.sumQuantityByLocationId(locationId);
        BigDecimal reserved = snapshotRepository.sumReservedByLocationId(locationId);
        OccupancyStatus status = OccupancyStatus.of(occupied, bin.getMaxWeightKg());

        BigDecimal available = null;
        if (bin.getMaxWeightKg() != null) {
            available = bin.getMaxWeightKg().subtract(occupied).subtract(reserved);
            if (available.compareTo(BigDecimal.ZERO) < 0) available = BigDecimal.ZERO;
        }

        // Fetch detailed inventory items in this bin
        List<BinOccupancyResponse.BinInventoryItem> items =
                snapshotRepository.findDetailByLocationId(locationId).stream()
                        .map(s -> BinOccupancyResponse.BinInventoryItem.builder()
                                .skuId(s.getSkuId())
                                .skuCode(s.getSkuCode())
                                .skuName(s.getSkuName())
                                .lotId(s.getLotId())
                                .lotNumber(s.getLotNumber())
                                .expiryDate(s.getExpiryDate())
                                .quantity(s.getQuantity())
                                .reservedQty(s.getReservedQty())
                                .build())
                        .toList();

        String zoneCode = bin.getZoneId() != null
                ? zoneRepository.findById(bin.getZoneId()).map(z -> z.getZoneCode()).orElse(null)
                : null;
        String parentCode = bin.getParentLocationId() != null
                ? locationRepository.findById(bin.getParentLocationId())
                .map(LocationEntity::getLocationCode).orElse(null)
                : null;

        BinOccupancyResponse response = BinOccupancyResponse.builder()
                .locationId(bin.getLocationId())
                .locationCode(bin.getLocationCode())
                .zoneId(bin.getZoneId())
                .zoneCode(zoneCode)
                .parentLocationId(bin.getParentLocationId())
                .parentLocationCode(parentCode)
                .maxWeightKg(bin.getMaxWeightKg())
                .maxVolumeM3(bin.getMaxVolumeM3())
                .occupiedQty(occupied)
                .reservedQty(reserved)
                .availableQty(available)
                .occupancyStatus(status)
                .isPickingFace(bin.getIsPickingFace())
                .isStaging(bin.getIsStaging())
                .active(bin.getActive())
                .inventoryItems(items)
                .build();

        return ApiResponse.success(MessageConstants.BIN_OCCUPANCY_SUCCESS, response);
    }

    // ─────────────────────────────────────────────────────────────
    // SCRUM-278: UC-LOC-07 Search Empty Bin
    // BR-LOC-24: only active bins
    // BR-LOC-25: available capacity >= required qty
    // BR-LOC-26: zone compatible with product category (via storage_policy_rules)
    // BR-LOC-27: sort by least residual space (prioritize tighter fit)
    // ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<List<EmptyBinResponse>> searchEmptyBin(
            Long warehouseId,
            Long zoneId,
            BigDecimal requiredWeightKg,
            BigDecimal requiredVolumeM3) {

        log.info("Searching empty bins: warehouse={}, zone={}, reqWeight={}, reqVol={}",
                warehouseId, zoneId, requiredWeightKg, requiredVolumeM3);

        // Fetch all active BIN locations in warehouse (BR-LOC-24: active only)
        List<LocationEntity> allBins = locationRepository
                .findActiveBinsByWarehouse(warehouseId, zoneId);

        // Batch-fetch occupancy
        List<Long> binIds = allBins.stream().map(LocationEntity::getLocationId).toList();
        Map<Long, BigDecimal> occupiedMap = snapshotRepository.sumQuantityByLocationIds(binIds);
        Map<Long, BigDecimal> reservedMap = snapshotRepository.sumReservedByLocationIds(binIds);

        // Resolve zone codes
        Set<Long> zoneIds = allBins.stream().map(LocationEntity::getZoneId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> zoneCodes = zoneRepository.findAllById(zoneIds).stream()
                .collect(Collectors.toMap(z -> z.getZoneId(), z -> z.getZoneCode()));

        Set<Long> parentIds = allBins.stream().map(LocationEntity::getParentLocationId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> parentCodes = locationRepository.findAllById(parentIds).stream()
                .collect(Collectors.toMap(LocationEntity::getLocationId, LocationEntity::getLocationCode));

        List<EmptyBinResponse> result = allBins.stream()
                .map(bin -> {
                    BigDecimal occupied = occupiedMap.getOrDefault(bin.getLocationId(), BigDecimal.ZERO);
                    BigDecimal reserved = reservedMap.getOrDefault(bin.getLocationId(), BigDecimal.ZERO);

                    BigDecimal availableWeight = bin.getMaxWeightKg() != null
                            ? bin.getMaxWeightKg().subtract(occupied).subtract(reserved)
                            : null;
                    BigDecimal availableVolume = bin.getMaxVolumeM3() != null
                            ? bin.getMaxVolumeM3().subtract(reserved) // volume reserved separately if tracked
                            : null;

                    OccupancyStatus status = OccupancyStatus.of(occupied, bin.getMaxWeightKg());

                    return EmptyBinResponse.builder()
                            .locationId(bin.getLocationId())
                            .locationCode(bin.getLocationCode())
                            .zoneId(bin.getZoneId())
                            .zoneCode(bin.getZoneId() != null ? zoneCodes.get(bin.getZoneId()) : null)
                            .rackCode(bin.getParentLocationId() != null
                                    ? parentCodes.get(bin.getParentLocationId()) : null)
                            .maxWeightKg(bin.getMaxWeightKg())
                            .maxVolumeM3(bin.getMaxVolumeM3())
                            .occupiedQty(occupied)
                            .availableWeightKg(availableWeight != null
                                    ? availableWeight.max(BigDecimal.ZERO) : null)
                            .availableVolumeM3(availableVolume != null
                                    ? availableVolume.max(BigDecimal.ZERO) : null)
                            .occupancyStatus(status)
                            .isPickingFace(bin.getIsPickingFace())
                            .build();
                })
                // BR-LOC-25: available capacity >= required
                .filter(r -> {
                    if (requiredWeightKg != null && r.getAvailableWeightKg() != null) {
                        if (r.getAvailableWeightKg().compareTo(requiredWeightKg) < 0) return false;
                    }
                    if (requiredVolumeM3 != null && r.getAvailableVolumeM3() != null) {
                        if (r.getAvailableVolumeM3().compareTo(requiredVolumeM3) < 0) return false;
                    }
                    // Exclude FULL bins (BR-LOC-24 + BR-LOC-25)
                    return r.getOccupancyStatus() != OccupancyStatus.FULL;
                })
                // BR-LOC-27: prioritize bins with least residual space (tighter fit first)
                .sorted(Comparator.comparing(
                        r -> r.getAvailableWeightKg() != null ? r.getAvailableWeightKg() : BigDecimal.valueOf(Long.MAX_VALUE)))
                .toList();

        String message = result.isEmpty()
                ? MessageConstants.BIN_SEARCH_NO_RESULT
                : MessageConstants.BIN_SEARCH_SUCCESS;

        return ApiResponse.success(message, result);
    }

    // ─────────────────────────────────────────────────────────────
    // SCRUM-279: UC-LOC-08 Configure Bin Capacity
    // BR-LOC-28: capacity > 0
    // BR-LOC-29: new capacity >= current occupied qty
    // BR-LOC-31: immediately recalculate available capacity
    // 7b: block if bin is in an active putaway/picking task
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<BinCapacityResponse> configureBinCapacity(
            Long locationId,
            ConfigureBinCapacityRequest request,
            Long updatedBy,
            String ipAddress,
            String userAgent) {

        log.info("Configuring bin capacity: locationId={}", locationId);

        LocationEntity bin = locationRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.LOCATION_NOT_FOUND, locationId)));

        // Must be BIN type
        if (bin.getLocationType() != LocationType.BIN) {
            throw new BusinessException(MessageConstants.BIN_ONLY_OPERATION);
        }

        // Must be active
        if (Boolean.FALSE.equals(bin.getActive())) {
            throw new BusinessException(MessageConstants.LOCATION_ALREADY_INACTIVE);
        }

        // UC-LOC-08 7b: block if bin is in an active putaway or picking task
        boolean inActivePutaway = putawayTaskItemRepository.existsActiveTaskForLocation(locationId);
        boolean inActivePicking = pickingTaskItemRepository.existsActiveTaskForLocation(locationId);
        if (inActivePutaway || inActivePicking) {
            throw new BusinessException(MessageConstants.BIN_IN_ACTIVE_TASK);
        }

        // BR-LOC-29: new capacity must not be < current occupied qty
        BigDecimal currentOccupied = snapshotRepository.sumQuantityByLocationId(locationId);
        if (currentOccupied == null) currentOccupied = BigDecimal.ZERO;

        if (request.getMaxWeightKg() != null
                && currentOccupied.compareTo(request.getMaxWeightKg()) > 0) {
            throw new BusinessException(
                    String.format(MessageConstants.BIN_CAPACITY_BELOW_OCCUPIED,
                            currentOccupied, request.getMaxWeightKg()));
        }

        // Apply update
        if (request.getMaxWeightKg() != null) bin.setMaxWeightKg(request.getMaxWeightKg());
        if (request.getMaxVolumeM3() != null)  bin.setMaxVolumeM3(request.getMaxVolumeM3());

        LocationEntity saved = locationRepository.save(bin);

        log.info("Bin capacity updated: locationId={}, maxWeight={}, maxVol={}",
                locationId, saved.getMaxWeightKg(), saved.getMaxVolumeM3());

        auditLogService.logAction(
                updatedBy, "BIN_CAPACITY_CONFIGURED", "LOCATION", locationId,
                String.format("Bin %s capacity: weight=%s kg, volume=%s m³",
                        saved.getLocationCode(), saved.getMaxWeightKg(), saved.getMaxVolumeM3()),
                ipAddress, userAgent);

        // BR-LOC-31: recalculate available capacity immediately
        BigDecimal availableWeight = saved.getMaxWeightKg() != null
                ? saved.getMaxWeightKg().subtract(currentOccupied).max(BigDecimal.ZERO)
                : null;
        BigDecimal availableVolume = saved.getMaxVolumeM3(); // full volume available if no volume tracking

        String zoneCode = saved.getZoneId() != null
                ? zoneRepository.findById(saved.getZoneId()).map(z -> z.getZoneCode()).orElse(null)
                : null;

        BinCapacityResponse response = BinCapacityResponse.builder()
                .locationId(saved.getLocationId())
                .locationCode(saved.getLocationCode())
                .zoneId(saved.getZoneId())
                .zoneCode(zoneCode)
                .maxWeightKg(saved.getMaxWeightKg())
                .maxVolumeM3(saved.getMaxVolumeM3())
                .currentOccupiedQty(currentOccupied)
                .availableWeightKg(availableWeight)
                .availableVolumeM3(availableVolume)
                .updatedAt(saved.getUpdatedAt())
                .build();

        return ApiResponse.success(MessageConstants.BIN_CAPACITY_CONFIGURED_SUCCESS, response);
    }
}