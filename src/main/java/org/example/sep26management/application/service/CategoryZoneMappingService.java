package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.request.CreateCategoryZoneMappingRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.CategoryZoneMappingResponse;
import org.example.sep26management.domain.entity.CategoryZoneMapping;
import org.example.sep26management.infrastructure.persistence.CategoryZoneMappingRepositoryImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing Category-Zone mappings
 * MANAGER only
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CategoryZoneMappingService {

    private final CategoryZoneMappingRepositoryImpl mappingRepository;

    /**
     * Create category-zone mapping
     */
    public ApiResponse<CategoryZoneMappingResponse> createMapping(
            CreateCategoryZoneMappingRequest request,
            Long currentUserId) {
        log.info("Creating mapping: Category {} → Zone {}", request.getCategoryId(), request.getZoneId());

        // Check if mapping already exists
        if (mappingRepository.existsByCategoryIdAndZoneId(request.getCategoryId(), request.getZoneId())) {
            throw new RuntimeException("Mapping already exists for this category and zone");
        }

        CategoryZoneMapping mapping = CategoryZoneMapping.builder()
                .categoryId(request.getCategoryId())
                .zoneId(request.getZoneId())
                .priority(request.getPriority() != null ? request.getPriority() : 1)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .createdBy(currentUserId)
                .updatedBy(currentUserId)
                .build();

        CategoryZoneMapping savedMapping = mappingRepository.save(mapping);

        log.info("Mapping created successfully: ID {}", savedMapping.getMappingId());

        CategoryZoneMappingResponse response = toResponse(savedMapping);
        return ApiResponse.success("Mapping created successfully", response);
    }

    /**
     * Get mappings for a category
     */
    @Transactional(readOnly = true)
    public ApiResponse<List<CategoryZoneMappingResponse>> getMappingsByCategory(Long categoryId) {
        log.info("Fetching mappings for category: {}", categoryId);

        List<CategoryZoneMapping> mappings = mappingRepository.findByCategoryId(categoryId);
        List<CategoryZoneMappingResponse> responses = mappings.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ApiResponse.success("Mappings retrieved successfully", responses);
    }

    /**
     * Get mappings for a zone
     */
    @Transactional(readOnly = true)
    public ApiResponse<List<CategoryZoneMappingResponse>> getMappingsByZone(Long zoneId) {
        log.info("Fetching mappings for zone: {}", zoneId);

        List<CategoryZoneMapping> mappings = mappingRepository.findByZoneId(zoneId);
        List<CategoryZoneMappingResponse> responses = mappings.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ApiResponse.success("Mappings retrieved successfully", responses);
    }

    /**
     * Delete mapping
     */
    public ApiResponse<String> deleteMapping(Long categoryId, Long zoneId, Long currentUserId) {
        log.info("Deleting mapping: Category {} → Zone {}", categoryId, zoneId);

        CategoryZoneMapping mapping = mappingRepository
                .findByCategoryIdAndZoneId(categoryId, zoneId)
                .orElseThrow(() -> new RuntimeException("Mapping not found"));

        mapping.setIsActive(false);
        mapping.setUpdatedBy(currentUserId);
        mappingRepository.save(mapping);

        log.info("Mapping deactivated successfully");

        return ApiResponse.success("Mapping deleted successfully");
    }

    /**
     * Get all active mappings
     */
    @Transactional(readOnly = true)
    public ApiResponse<List<CategoryZoneMappingResponse>> getAllMappings() {
        log.info("Fetching all active mappings");

        List<CategoryZoneMapping> mappings = mappingRepository.findAllActive();

        List<CategoryZoneMappingResponse> responses = mappings.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ApiResponse.success("Mappings retrieved successfully", responses);
    }

    // Helper method to convert domain to response
    private CategoryZoneMappingResponse toResponse(CategoryZoneMapping domain) {
        return CategoryZoneMappingResponse.builder()
                .mappingId(domain.getMappingId())
                .categoryId(domain.getCategoryId())
                .zoneId(domain.getZoneId())
                .priority(domain.getPriority())
                .isActive(domain.getIsActive())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                // TODO: Populate category and zone names from joined queries
                .build();
    }
}
