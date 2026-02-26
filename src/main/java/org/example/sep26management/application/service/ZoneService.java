package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.application.dto.request.CreateZoneRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.ZoneResponse;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.example.sep26management.infrastructure.exception.ResourceNotFoundException;
import org.example.sep26management.infrastructure.persistence.entity.ZoneEntity;
import org.example.sep26management.infrastructure.persistence.repository.WarehouseJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.ZoneJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ZoneService {

    private final ZoneJpaRepository zoneRepository;
    private final WarehouseJpaRepository warehouseRepository;
    private final AuditLogService auditLogService;

    // ─────────────────────────────────────────────────────────────
    // UC-LOC-01: Create Zone
    // BR-LOC-01: unique zone_code per warehouse
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<ZoneResponse> createZone(
            CreateZoneRequest request,
            Long createdBy,
            String ipAddress,
            String userAgent) {

        log.info("Creating zone: code={}, warehouse={}", request.getZoneCode(), request.getWarehouseId());

        // Validate warehouse exists
        warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.WAREHOUSE_NOT_FOUND, request.getWarehouseId())));

        // BR-LOC-01: Duplicate zone code check within warehouse
        String zoneCode = request.getZoneCode().toUpperCase().trim();
        if (zoneRepository.existsByWarehouseIdAndZoneCode(request.getWarehouseId(), zoneCode)) {
            throw new BusinessException(
                    String.format(MessageConstants.ZONE_CODE_DUPLICATE, zoneCode));
        }

        ZoneEntity zone = ZoneEntity.builder()
                .warehouseId(request.getWarehouseId())
                .zoneCode(zoneCode)
                .zoneName(request.getZoneName() != null ? request.getZoneName().trim() : null)
                .active(true)
                .build();

        ZoneEntity saved = zoneRepository.save(zone);

        log.info("Zone created: zoneId={}, code={}", saved.getZoneId(), saved.getZoneCode());

        auditLogService.logAction(
                createdBy, "ZONE_CREATED", "ZONE", saved.getZoneId(),
                String.format("Zone %s created in warehouse %d", saved.getZoneCode(), saved.getWarehouseId()),
                ipAddress, userAgent);

        return ApiResponse.success(MessageConstants.ZONE_CREATED_SUCCESS, toResponse(saved));
    }

    // List zones by warehouse
    @Transactional(readOnly = true)
    public ApiResponse<List<ZoneResponse>> listZones(Long warehouseId, Boolean activeOnly) {
        List<ZoneEntity> zones = Boolean.TRUE.equals(activeOnly)
                ? zoneRepository.findByWarehouseIdAndActiveTrue(warehouseId)
                : zoneRepository.findByWarehouseId(warehouseId);

        List<ZoneResponse> responses = zones.stream().map(this::toResponse).toList();
        return ApiResponse.success("Zones retrieved", responses);
    }

    private ZoneResponse toResponse(ZoneEntity zone) {
        return ZoneResponse.builder()
                .zoneId(zone.getZoneId())
                .warehouseId(zone.getWarehouseId())
                .zoneCode(zone.getZoneCode())
                .zoneName(zone.getZoneName())
                .active(zone.getActive())
                .createdAt(zone.getCreatedAt())
                .updatedAt(zone.getUpdatedAt())
                .build();
    }
}