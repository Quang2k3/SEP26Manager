package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.SkuResponse;
import org.example.sep26management.infrastructure.mapper.SkuMapper;
import org.example.sep26management.infrastructure.persistence.repository.CategoryJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.SkuJpaRepository;
import org.example.sep26management.application.constants.LogMessages;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.application.dto.request.AssignCategoryToSkuRequest;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.example.sep26management.infrastructure.exception.ResourceNotFoundException;
import org.example.sep26management.infrastructure.persistence.entity.CategoryEntity;
import org.example.sep26management.infrastructure.persistence.entity.SkuEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SkuService {

    private final SkuJpaRepository skuJpaRepository;
    private final SkuMapper skuMapper;
    private final CategoryJpaRepository categoryRepository;
    private final AuditLogService auditLogService;

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