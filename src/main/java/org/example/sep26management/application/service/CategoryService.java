package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.LogMessages;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.application.dto.request.CreateCategoryRequest;
import org.example.sep26management.application.dto.request.UpdateCategoryRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.CategoryResponse;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.example.sep26management.infrastructure.exception.ResourceNotFoundException;
import org.example.sep26management.infrastructure.persistence.entity.CategoryEntity;
import org.example.sep26management.infrastructure.persistence.repository.CategoryJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CategoryService {

    private final CategoryJpaRepository categoryRepository;
    private final AuditLogService auditLogService;

    /**
     * Create a new product category
     */
    public ApiResponse<CategoryResponse> createCategory(
            CreateCategoryRequest request,
            Long createdBy,
            String ipAddress,
            String userAgent) {

        log.info(LogMessages.CATEGORY_CREATING, request.getCategoryCode());

        // Validate category code uniqueness
        if (categoryRepository.existsByCategoryCode(request.getCategoryCode())) {
            log.warn(LogMessages.CATEGORY_CODE_DUPLICATE, request.getCategoryCode());
            throw new BusinessException(
                    String.format(MessageConstants.CATEGORY_CODE_EXISTS, request.getCategoryCode()));
        }

        // Validate parent category if provided
        String parentCategoryName = null;
        if (request.getParentCategoryId() != null) {
            CategoryEntity parentCategory = categoryRepository.findById(request.getParentCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            String.format(MessageConstants.CATEGORY_PARENT_NOT_FOUND, request.getParentCategoryId())));

            if (!parentCategory.getActive()) {
                throw new BusinessException(MessageConstants.CATEGORY_PARENT_INACTIVE);
            }
            parentCategoryName = parentCategory.getCategoryName();
        }

        // Build entity
        CategoryEntity categoryEntity = CategoryEntity.builder()
                .categoryCode(request.getCategoryCode())
                .categoryName(request.getCategoryName())
                .parentCategoryId(request.getParentCategoryId())
                .description(request.getDescription())
                .active(true)
                .build();

        // Save to database
        CategoryEntity savedCategory = categoryRepository.save(categoryEntity);

        log.info(LogMessages.CATEGORY_CREATED, savedCategory.getCategoryId());

        // Audit log
        auditLogService.logAction(
                createdBy,
                "CATEGORY_CREATED",
                "CATEGORY",
                savedCategory.getCategoryId(),
                "Category created: " + savedCategory.getCategoryCode() + " - " + savedCategory.getCategoryName(),
                ipAddress,
                userAgent);

        CategoryResponse response = buildCategoryResponse(savedCategory, parentCategoryName);
        return ApiResponse.success(MessageConstants.CATEGORY_CREATED_SUCCESS, response);
    }

    /**
     * Update an existing product category
     */
    public ApiResponse<CategoryResponse> updateCategory(
            Long categoryId,
            UpdateCategoryRequest request,
            Long updatedBy,
            String ipAddress,
            String userAgent) {

        log.info(LogMessages.CATEGORY_UPDATING, categoryId);

        // Find existing category
        CategoryEntity category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.CATEGORY_NOT_FOUND, categoryId)));

        // Store old values for audit
        String oldName = category.getCategoryName();
        Long oldParentId = category.getParentCategoryId();
        Boolean oldActive = category.getActive();

        // Validate parent category if provided
        String parentCategoryName = null;
        if (request.getParentCategoryId() != null) {
            // Cannot set itself as parent
            if (request.getParentCategoryId().equals(categoryId)) {
                throw new BusinessException(MessageConstants.CATEGORY_SELF_PARENT);
            }

            CategoryEntity parentCategory = categoryRepository.findById(request.getParentCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            String.format(MessageConstants.CATEGORY_PARENT_NOT_FOUND, request.getParentCategoryId())));

            if (!parentCategory.getActive()) {
                throw new BusinessException(MessageConstants.CATEGORY_PARENT_INACTIVE);
            }

            // Check circular reference
            if (isCircularReference(categoryId, request.getParentCategoryId())) {
                throw new BusinessException(MessageConstants.CATEGORY_CIRCULAR_REFERENCE);
            }

            parentCategoryName = parentCategory.getCategoryName();
        }

        // Update fields
        category.setCategoryName(request.getCategoryName());
        category.setParentCategoryId(request.getParentCategoryId());
        category.setDescription(request.getDescription());
        if (request.getActive() != null) {
            category.setActive(request.getActive());
        }

        // Save
        CategoryEntity updatedCategory = categoryRepository.save(category);

        log.info(LogMessages.CATEGORY_UPDATED, updatedCategory.getCategoryId());

        // Audit log
        StringBuilder auditDetails = new StringBuilder("Category updated: " + updatedCategory.getCategoryCode());
        if (!oldName.equals(updatedCategory.getCategoryName())) {
            auditDetails.append(String.format(" | Name: '%s' -> '%s'", oldName, updatedCategory.getCategoryName()));
        }
        if ((oldParentId == null && updatedCategory.getParentCategoryId() != null)
                || (oldParentId != null && !oldParentId.equals(updatedCategory.getParentCategoryId()))) {
            auditDetails.append(String.format(" | ParentId: %s -> %s", oldParentId, updatedCategory.getParentCategoryId()));
        }
        if (!oldActive.equals(updatedCategory.getActive())) {
            auditDetails.append(String.format(" | Active: %s -> %s", oldActive, updatedCategory.getActive()));
        }

        auditLogService.logAction(
                updatedBy,
                "CATEGORY_UPDATED",
                "CATEGORY",
                updatedCategory.getCategoryId(),
                auditDetails.toString(),
                ipAddress,
                userAgent);

        CategoryResponse response = buildCategoryResponse(updatedCategory, parentCategoryName);
        return ApiResponse.success(MessageConstants.CATEGORY_UPDATED_SUCCESS, response);
    }

    /**
     * Get category by ID
     */
    @Transactional(readOnly = true)
    public ApiResponse<CategoryResponse> getCategoryById(Long categoryId) {
        log.info(LogMessages.CATEGORY_FETCHING, categoryId);

        CategoryEntity category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.CATEGORY_NOT_FOUND, categoryId)));

        String parentCategoryName = null;
        if (category.getParentCategoryId() != null) {
            parentCategoryName = categoryRepository.findById(category.getParentCategoryId())
                    .map(CategoryEntity::getCategoryName)
                    .orElse(null);
        }

        CategoryResponse response = buildCategoryResponse(category, parentCategoryName);
        return ApiResponse.success(MessageConstants.CATEGORY_RETRIEVED_SUCCESS, response);
    }

    /**
     * Get all categories
     */
    @Transactional(readOnly = true)
    public ApiResponse<List<CategoryResponse>> getAllCategories() {
        log.info(LogMessages.CATEGORY_LIST_FETCHING);

        List<CategoryEntity> categories = categoryRepository.findAll();

        List<CategoryResponse> responses = categories.stream()
                .map(cat -> {
                    String parentName = null;
                    if (cat.getParentCategoryId() != null) {
                        parentName = categoryRepository.findById(cat.getParentCategoryId())
                                .map(CategoryEntity::getCategoryName)
                                .orElse(null);
                    }
                    return buildCategoryResponse(cat, parentName);
                })
                .collect(Collectors.toList());

        return ApiResponse.success(MessageConstants.CATEGORY_LIST_SUCCESS, responses);
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    private boolean isCircularReference(Long categoryId, Long parentCategoryId) {
        Long currentParentId = parentCategoryId;
        while (currentParentId != null) {
            if (currentParentId.equals(categoryId)) {
                return true;
            }
            Long finalCurrentParentId = currentParentId;
            currentParentId = categoryRepository.findById(finalCurrentParentId)
                    .map(CategoryEntity::getParentCategoryId)
                    .orElse(null);
        }
        return false;
    }

    private CategoryResponse buildCategoryResponse(CategoryEntity entity, String parentCategoryName) {
        return CategoryResponse.builder()
                .categoryId(entity.getCategoryId())
                .categoryCode(entity.getCategoryCode())
                .categoryName(entity.getCategoryName())
                .parentCategoryId(entity.getParentCategoryId())
                .parentCategoryName(parentCategoryName)
                .description(entity.getDescription())
                .active(entity.getActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}