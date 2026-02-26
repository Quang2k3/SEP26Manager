package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.request.ConfigureSkuThresholdRequest;
import org.example.sep26management.application.dto.request.SearchSkuRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.PageResponse;
import org.example.sep26management.application.dto.response.SkuResponse;
import org.example.sep26management.application.dto.response.SkuThresholdResponse;
import org.example.sep26management.infrastructure.mapper.SkuMapper;
import org.example.sep26management.infrastructure.persistence.entity.SkuThresholdEntity;
import org.example.sep26management.infrastructure.persistence.repository.CategoryJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.SkuJpaRepository;
import org.example.sep26management.application.constants.LogMessages;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.application.dto.request.AssignCategoryToSkuRequest;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.example.sep26management.infrastructure.exception.ResourceNotFoundException;
import org.example.sep26management.infrastructure.persistence.entity.CategoryEntity;
import org.example.sep26management.infrastructure.persistence.entity.SkuEntity;
import org.example.sep26management.infrastructure.persistence.repository.SkuThresholdJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.WarehouseJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SkuService {

    private final SkuJpaRepository skuJpaRepository;
    private final SkuMapper skuMapper;
    private final CategoryJpaRepository categoryRepository;
    private final AuditLogService auditLogService;
    private final SkuThresholdJpaRepository skuThresholdRepository;
    private final WarehouseJpaRepository warehouseRepository;


    /**
     * UC-268: View SKU Detail
     */
    @Transactional(readOnly = true)
    public ApiResponse<SkuResponse> getSkuDetail(Long skuId) {
        log.info("Fetching SKU detail for ID: {}", skuId);

        SkuEntity sku = skuJpaRepository.findByIdWithCategory(skuId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.SKU_NOT_FOUND, skuId)));

        SkuResponse response = skuMapper.toResponse(sku);

        return ApiResponse.success("SKU detail retrieved successfully", response);
    }

    // ─────────────────────────────────────────────────────────────
    // UC-B06: Search SKU
    // BR-SKU-06: partial, case-insensitive, skuCode + skuName
    // BR-GEN-01: blank keyword → latest 20 records
    // ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<PageResponse<SkuResponse>> searchSku(SearchSkuRequest request) {
        log.info("Searching SKU with keyword='{}', page={}, size={}",
                request.getKeyword(), request.getPage(), request.getSize());

        int size = request.getSize() > 0 ? request.getSize() : 20;
        Pageable pageable = PageRequest.of(request.getPage(), size);

        // blank keyword → return all (BR-GEN-01 default view)
        String keyword = (request.getKeyword() != null && !request.getKeyword().isBlank())
                ? request.getKeyword().trim()
                : null;

        Page<SkuEntity> page = skuJpaRepository.searchByKeyword(keyword, pageable);

        List<SkuResponse> content = page.getContent()
                .stream()
                .map(skuMapper::toResponse)
                .toList();

        PageResponse<SkuResponse> pageResponse = PageResponse.<SkuResponse>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();

        // MS-SKU-06: empty state message khi không có kết quả
        String message = content.isEmpty()
                ? MessageConstants.SKU_SEARCH_NO_RESULT
                : MessageConstants.SKU_SEARCH_SUCCESS;

        return ApiResponse.success(message, pageResponse);
    }

    // ─────────────────────────────────────────────────────────────
    // UC-B07: Configure SKU Threshold
    // BR-SKU-07: min < max, both positive integers
    // Upsert: tạo mới nếu chưa có, update nếu đã có
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<SkuThresholdResponse> configureThreshold(
            Long skuId,
            ConfigureSkuThresholdRequest request,
            Long updatedBy,
            String ipAddress,
            String userAgent) {

        log.info("Configuring threshold for skuId={}, warehouseId={}", skuId, request.getWarehouseId());

        // Validate SKU exists and is active
        SkuEntity sku = skuJpaRepository.findByIdWithCategory(skuId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.SKU_NOT_FOUND, skuId)));

        if (Boolean.FALSE.equals(sku.getActive())) {
            throw new BusinessException(MessageConstants.SKU_INACTIVE);
        }

        // Validate warehouse exists
        warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.WAREHOUSE_NOT_FOUND, request.getWarehouseId())));

        // BR-SKU-07: Min < Max validation
        if (request.getMinQty() != null && request.getMaxQty() != null) {
            if (request.getMinQty().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(MessageConstants.THRESHOLD_MUST_BE_POSITIVE);
            }
            if (request.getMaxQty().compareTo(BigDecimal.ONE) < 0) {
                throw new BusinessException(MessageConstants.THRESHOLD_MUST_BE_POSITIVE);
            }
            if (request.getMinQty().compareTo(request.getMaxQty()) >= 0) {
                throw new BusinessException(MessageConstants.THRESHOLD_MIN_MUST_BE_LESS_THAN_MAX);
            }
        }

        // Upsert threshold
        SkuThresholdEntity threshold = skuThresholdRepository
                .findByWarehouseIdAndSkuId(request.getWarehouseId(), skuId)
                .orElse(SkuThresholdEntity.builder()
                        .warehouseId(request.getWarehouseId())
                        .skuId(skuId)
                        .createdBy(updatedBy)
                        .build());

        threshold.setMinQty(request.getMinQty() != null ? request.getMinQty() : BigDecimal.ZERO);
        threshold.setMaxQty(request.getMaxQty());
        threshold.setReorderPoint(request.getReorderPoint());
        threshold.setReorderQty(request.getReorderQty());
        threshold.setNote(request.getNote());
        threshold.setUpdatedBy(updatedBy);

        SkuThresholdEntity saved = skuThresholdRepository.save(threshold);

        log.info("Threshold configured: skuId={}, min={}, max={}", skuId,
                saved.getMinQty(), saved.getMaxQty());

        auditLogService.logAction(
                updatedBy, "SKU_THRESHOLD_CONFIGURED", "SKU_THRESHOLD", saved.getThresholdId(),
                String.format("SKU %s threshold: min=%s, max=%s", sku.getSkuCode(),
                        saved.getMinQty(), saved.getMaxQty()),
                ipAddress, userAgent);

        SkuThresholdResponse response = SkuThresholdResponse.builder()
                .thresholdId(saved.getThresholdId())
                .skuId(skuId)
                .skuCode(sku.getSkuCode())
                .skuName(sku.getSkuName())
                .warehouseId(saved.getWarehouseId())
                .minQty(saved.getMinQty())
                .maxQty(saved.getMaxQty())
                .reorderPoint(saved.getReorderPoint())
                .reorderQty(saved.getReorderQty())
                .active(saved.getActive())
                .note(saved.getNote())
                .updatedAt(saved.getUpdatedAt())
                .build();

        return ApiResponse.success(MessageConstants.THRESHOLD_UPDATED_SUCCESS, response);
    }

    // Get current threshold for a SKU
    @Transactional(readOnly = true)
    public ApiResponse<SkuThresholdResponse> getThreshold(Long skuId, Long warehouseId) {
        SkuEntity sku = skuJpaRepository.findByIdWithCategory(skuId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.SKU_NOT_FOUND, skuId)));

        SkuThresholdEntity threshold = skuThresholdRepository
                .findByWarehouseIdAndSkuId(warehouseId, skuId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        MessageConstants.THRESHOLD_NOT_FOUND));

        SkuThresholdResponse response = SkuThresholdResponse.builder()
                .thresholdId(threshold.getThresholdId())
                .skuId(skuId)
                .skuCode(sku.getSkuCode())
                .skuName(sku.getSkuName())
                .warehouseId(threshold.getWarehouseId())
                .minQty(threshold.getMinQty())
                .maxQty(threshold.getMaxQty())
                .reorderPoint(threshold.getReorderPoint())
                .reorderQty(threshold.getReorderQty())
                .active(threshold.getActive())
                .note(threshold.getNote())
                .updatedAt(threshold.getUpdatedAt())
                .build();

        return ApiResponse.success("Threshold retrieved", response);
    }

    /**
     * UC: Assign Category to SKU
     * BR: Category must be active
     */
    @Transactional
    public ApiResponse<SkuResponse> assignCategoryToSku(
            Long skuId,
            AssignCategoryToSkuRequest request,
            Long updatedBy,
            String ipAddress,
            String userAgent) {

        log.info(LogMessages.SKU_ASSIGNING_CATEGORY, skuId, request.getCategoryId());

        // Find SKU
        SkuEntity sku = skuJpaRepository.findByIdWithCategory(skuId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.SKU_NOT_FOUND, skuId)));

        // Find Category
        CategoryEntity category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.CATEGORY_NOT_FOUND, request.getCategoryId())));

        // BR-CAT-08: Inactive category cannot be assigned
        if (!category.getActive()) {
            throw new BusinessException(MessageConstants.SKU_CATEGORY_INACTIVE);
        }

        // Check same category (null-safe)
        Long currentCategoryId = (sku.getCategory() != null) ? sku.getCategory().getCategoryId() : null;
        if (request.getCategoryId().equals(currentCategoryId)) {
            throw new BusinessException(MessageConstants.SKU_SAME_CATEGORY);
        }

        // Assign
        sku.setCategory(category);
        SkuEntity updatedSku = skuJpaRepository.save(sku);

        log.info(LogMessages.SKU_CATEGORY_ASSIGNED, skuId, request.getCategoryId());

        auditLogService.logAction(
                updatedBy, "SKU_CATEGORY_ASSIGNED", "SKU", skuId,
                "SKU " + sku.getSkuCode() + " category: " + currentCategoryId + " -> " + request.getCategoryId(),
                ipAddress, userAgent);

        return ApiResponse.success(MessageConstants.SKU_CATEGORY_ASSIGNED_SUCCESS,
                skuMapper.toResponse(updatedSku));
    }

    /**
     * Lookup SKU by barcode.
     */
    @Transactional(readOnly = true)
    public ApiResponse<SkuResponse> findByBarcode(String barcode) {
        log.info("Looking up SKU by barcode: {}", barcode);

        return skuJpaRepository.findActiveByBarcodeWithCategory(barcode)
                .map(sku -> {
                    log.info("SKU found for barcode {}: skuCode={}", barcode, sku.getSkuCode());
                    return ApiResponse.success("SKU found", skuMapper.toResponse(sku));
                })
                .orElseGet(() -> {
                    log.warn("No active SKU found for barcode: {}", barcode);
                    return ApiResponse.error("SKU not found for barcode: " + barcode);
                });
    }

    /**
     * Lookup SKU by SKU code.
     */
    @Transactional(readOnly = true)
    public ApiResponse<SkuResponse> findBySkuCode(String skuCode) {
        log.info("Looking up SKU by skuCode: {}", skuCode);

        return skuJpaRepository.findActiveBySkuCodeWithCategory(skuCode)
                .map(sku -> {
                    log.info("SKU found: skuCode={}", sku.getSkuCode());
                    return ApiResponse.success("SKU found", skuMapper.toResponse(sku));
                })
                .orElseGet(() -> {
                    log.warn("No active SKU found for skuCode: {}", skuCode);
                    return ApiResponse.error("SKU not found: " + skuCode);
                });
    }
}