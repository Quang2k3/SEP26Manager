package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.application.dto.request.CreateZoneRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.PageResponse;
import org.example.sep26management.application.dto.response.ZoneResponse;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.example.sep26management.infrastructure.exception.ResourceNotFoundException;
import org.example.sep26management.infrastructure.persistence.entity.ZoneEntity;
import org.example.sep26management.infrastructure.persistence.repository.WarehouseJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.ZoneJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
                        Long warehouseId,
                        Long createdBy,
                        String ipAddress,
                        String userAgent) {

                log.info("Creating zone: code={}, warehouse={}", request.getZoneCode(), warehouseId);

                // Validate warehouse exists
                warehouseRepository.findById(warehouseId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                String.format(MessageConstants.WAREHOUSE_NOT_FOUND,
                                                                warehouseId)));

                // BR-LOC-01: Duplicate zone code check within warehouse
                String zoneCode = request.getZoneCode().toUpperCase().trim();
                if (zoneRepository.existsByWarehouseIdAndZoneCode(warehouseId, zoneCode)) {
                        throw new BusinessException(
                                        String.format(MessageConstants.ZONE_CODE_DUPLICATE, zoneCode));
                }

                ZoneEntity zone = ZoneEntity.builder()
                                .warehouseId(warehouseId)
                                .zoneCode(zoneCode)
                                .zoneName(request.getZoneName() != null ? request.getZoneName().trim() : null)
                                .active(true)
                                .build();

                ZoneEntity saved = zoneRepository.save(zone);

                log.info("Zone created: zoneId={}, code={}", saved.getZoneId(), saved.getZoneCode());

                auditLogService.logAction(
                                createdBy, "ZONE_CREATED", "ZONE", saved.getZoneId(),
                                String.format("Zone %s created in warehouse %d", saved.getZoneCode(),
                                                warehouseId),
                                ipAddress, userAgent);

                return ApiResponse.success(MessageConstants.ZONE_CREATED_SUCCESS, toResponse(saved));
        }

        // List zones by warehouse
        @Transactional(readOnly = true)
        public ApiResponse<PageResponse<ZoneResponse>> listZones(Long warehouseId, Boolean activeOnly, int page,
                        int size) {
                Pageable pageable = PageRequest.of(page, size);
                Page<ZoneEntity> zonesPage = Boolean.TRUE.equals(activeOnly)
                                ? zoneRepository.findByWarehouseIdAndActiveTrue(warehouseId, pageable)
                                : zoneRepository.findByWarehouseId(warehouseId, pageable);

                List<ZoneResponse> content = zonesPage.getContent().stream().map(this::toResponse).toList();
                PageResponse<ZoneResponse> pageResponse = PageResponse.<ZoneResponse>builder()
                                .content(content)
                                .page(zonesPage.getNumber())
                                .size(zonesPage.getSize())
                                .totalElements(zonesPage.getTotalElements())
                                .totalPages(zonesPage.getTotalPages())
                                .last(zonesPage.isLast())
                                .build();
                return ApiResponse.success("Zones retrieved", pageResponse);
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