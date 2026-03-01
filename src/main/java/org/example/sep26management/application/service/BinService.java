package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.MessageConstants;
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


}