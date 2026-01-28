package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.request.CreateZoneRequest;
import org.example.sep26management.application.dto.request.UpdateZoneRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.ZoneResponse;
import org.example.sep26management.application.mapper.ZoneMapper;
import org.example.sep26management.domain.entity.Zone;
import org.example.sep26management.infrastructure.persistence.ZoneRepositoryImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ZoneService {

    private final ZoneRepositoryImpl zoneRepository;
    private final ZoneMapper zoneMapper;

    /**
     * Create a new zone
     */
    public ApiResponse<ZoneResponse> createZone(CreateZoneRequest request, Long currentUserId) {
        log.info("Creating zone with code: {}", request.getZoneCode());

        // Validate unique zone code
        if (zoneRepository.existsByZoneCode(request.getZoneCode())) {
            return ApiResponse.error("Zone code already exists: " + request.getZoneCode());
        }

        // Build zone entity
        Zone zone = Zone.builder()
                .zoneCode(request.getZoneCode())
                .zoneName(request.getZoneName())
                .warehouseCode(request.getWarehouseCode())
                .zoneType(request.getZoneType())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .createdBy(currentUserId)
                .updatedBy(currentUserId)
                .build();

        // Save zone
        Zone savedZone = zoneRepository.save(zone);

        log.info("Zone created successfully with ID: {}", savedZone.getZoneId());

        return ApiResponse.success(
                "Zone created successfully",
                zoneMapper.toResponse(savedZone));
    }

    /**
     * Get zone by ID
     */
    @Transactional(readOnly = true)
    public ApiResponse<ZoneResponse> getZoneById(Long id) {
        log.info("Fetching zone with ID: {}", id);

        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Zone not found with ID: " + id));

        return ApiResponse.success(
                "Zone retrieved successfully",
                zoneMapper.toResponse(zone));
    }

    /**
     * Get all zones
     */
    @Transactional(readOnly = true)
    public ApiResponse<List<ZoneResponse>> getAllZones() {
        log.info("Fetching all zones");

        List<Zone> zones = zoneRepository.findAll();
        List<ZoneResponse> responses = zones.stream()
                .map(zoneMapper::toResponse)
                .collect(Collectors.toList());

        return ApiResponse.success(
                "Zones retrieved successfully",
                responses);
    }

    /**
     * Update zone
     */
    public ApiResponse<ZoneResponse> updateZone(Long id, UpdateZoneRequest request, Long currentUserId) {
        log.info("Updating zone with ID: {}", id);

        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Zone not found with ID: " + id));

        // Update fields if provided
        if (request.getZoneName() != null) {
            zone.setZoneName(request.getZoneName());
        }
        if (request.getWarehouseCode() != null) {
            zone.setWarehouseCode(request.getWarehouseCode());
        }
        if (request.getZoneType() != null) {
            zone.setZoneType(request.getZoneType());
        }
        if (request.getIsActive() != null) {
            zone.setIsActive(request.getIsActive());
        }

        zone.setUpdatedBy(currentUserId);
        zone.setUpdatedAt(LocalDateTime.now());

        Zone updatedZone = zoneRepository.save(zone);

        log.info("Zone updated successfully: {}", updatedZone.getZoneId());

        return ApiResponse.success(
                "Zone updated successfully",
                zoneMapper.toResponse(updatedZone));
    }

    /**
     * Delete zone (soft delete by setting isActive = false)
     */
    public ApiResponse<String> deleteZone(Long id, Long currentUserId) {
        log.info("Deleting zone with ID: {}", id);

        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Zone not found with ID: " + id));

        zone.deactivate();
        zone.setUpdatedBy(currentUserId);
        zone.setUpdatedAt(LocalDateTime.now());

        zoneRepository.save(zone);

        log.info("Zone deleted (deactivated) successfully: {}", id);

        return ApiResponse.success("Zone deleted successfully");
    }

    /**
     * Get zones by warehouse code
     */
    @Transactional(readOnly = true)
    public ApiResponse<List<ZoneResponse>> getZonesByWarehouse(String warehouseCode) {
        log.info("Fetching zones for warehouse: {}", warehouseCode);

        List<Zone> zones = zoneRepository.findByWarehouseCode(warehouseCode);
        List<ZoneResponse> responses = zones.stream()
                .map(zoneMapper::toResponse)
                .collect(Collectors.toList());

        return ApiResponse.success(
                "Zones retrieved successfully",
                responses);
    }
}
