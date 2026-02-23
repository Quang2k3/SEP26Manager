package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.LogMessages;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.application.dto.request.AssignCategoryToSkuRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.SkuResponse;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.example.sep26management.infrastructure.exception.ResourceNotFoundException;
import org.example.sep26management.infrastructure.persistence.entity.CategoryEntity;
import org.example.sep26management.infrastructure.persistence.entity.SkuEntity;
import org.example.sep26management.infrastructure.persistence.repository.CategoryJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.SkuJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SkuService {

    private final SkuJpaRepository skuRepository;
    private final CategoryJpaRepository categoryRepository;
    private final AuditLogService auditLogService;

    /**
     * UC: Assign Category to SKU
     * BR: Category must be active
     */
    public ApiResponse<SkuResponse> assignCategoryToSku(
            Long skuId,
            AssignCategoryToSkuRequest request,
            Long updatedBy,
            String ipAddress,
            String userAgent) {

        log.info(LogMessages.SKU_ASSIGNING_CATEGORY, skuId, request.getCategoryId());

        // Find SKU
        SkuEntity sku = skuRepository.findById(skuId)
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

        // Check same category
        if (request.getCategoryId().equals(sku.getCategoryId())) {
            throw new BusinessException(MessageConstants.SKU_SAME_CATEGORY);
        }

        Long oldCategoryId = sku.getCategoryId();

        // Assign
        sku.setCategoryId(request.getCategoryId());
        SkuEntity updatedSku = skuRepository.save(sku);

        log.info(LogMessages.SKU_CATEGORY_ASSIGNED, skuId, request.getCategoryId());

        auditLogService.logAction(
                updatedBy, "SKU_CATEGORY_ASSIGNED", "SKU", skuId,
                "SKU " + sku.getSkuCode() + " category: " + oldCategoryId + " -> " + request.getCategoryId(),
                ipAddress, userAgent);

        return ApiResponse.success(MessageConstants.SKU_CATEGORY_ASSIGNED_SUCCESS,
                buildSkuResponse(updatedSku, category));
    }

    private SkuResponse buildSkuResponse(SkuEntity sku, CategoryEntity category) {
        return SkuResponse.builder()
                .skuId(sku.getSkuId())
                .skuCode(sku.getSkuCode())
                .skuName(sku.getSkuName())
                .description(sku.getDescription())
                .brand(sku.getBrand())
                .unit(sku.getUnit())
                .categoryId(sku.getCategoryId())
                .categoryCode(category != null ? category.getCategoryCode() : null)
                .categoryName(category != null ? category.getCategoryName() : null)
                .active(sku.getActive())
                .createdAt(sku.getCreatedAt())
                .updatedAt(sku.getUpdatedAt())
                .build();
    }
}