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
import org.example.sep26management.application.dto.response.CategoryTreeResponse;
import org.example.sep26management.application.dto.response.MapCategoryToZoneResponse;
import org.example.sep26management.application.dto.request.MapCategoryToZoneRequest;
import org.example.sep26management.infrastructure.persistence.entity.ZoneEntity;
import org.example.sep26management.infrastructure.persistence.repository.ZoneJpaRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CategoryService {

    private final CategoryJpaRepository categoryRepository;
    private final AuditLogService auditLogService;
    private final ZoneJpaRepository zoneRepository;

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

    // ==========================================================
    // UC: Deactivate Category
    // ==========================================================

    /**
     * Deactivate a category.
     * BR-CAT-08: Inactive categories cannot be used in new mappings.
     * Must deactivate children first.
     */
    public ApiResponse<CategoryResponse> deactivateCategory(
            Long categoryId,
            Long deactivatedBy,
            String ipAddress,
            String userAgent) {

        log.info(LogMessages.CATEGORY_DEACTIVATING, categoryId);

        CategoryEntity category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.CATEGORY_NOT_FOUND, categoryId)));

        if (!category.getActive()) {
            throw new BusinessException(MessageConstants.CATEGORY_ALREADY_INACTIVE);
        }

        // Check active children — must deactivate children first
        List<CategoryEntity> activeChildren = categoryRepository.findAll().stream()
                .filter(c -> categoryId.equals(c.getParentCategoryId()) && c.getActive())
                .collect(Collectors.toList());

        if (!activeChildren.isEmpty()) {
            throw new BusinessException(
                    String.format(MessageConstants.CATEGORY_HAS_ACTIVE_CHILDREN, activeChildren.size()));
        }

        category.setActive(false);
        CategoryEntity saved = categoryRepository.save(category);

        log.info(LogMessages.CATEGORY_DEACTIVATED, categoryId);

        auditLogService.logAction(
                deactivatedBy, "CATEGORY_DEACTIVATED", "CATEGORY", categoryId,
                "Category deactivated: " + category.getCategoryCode(),
                ipAddress, userAgent);

        String parentName = null;
        if (saved.getParentCategoryId() != null) {
            parentName = categoryRepository.findById(saved.getParentCategoryId())
                    .map(CategoryEntity::getCategoryName).orElse(null);
        }

        return ApiResponse.success(MessageConstants.CATEGORY_DEACTIVATED_SUCCESS,
                buildCategoryResponse(saved, parentName));
    }

    // ==========================================================
    // UC: View Category Tree
    // ==========================================================

    /**
     * Get category tree with zone mapping info.
     * Shows convention-based zone: "Z-" + categoryCode
     */
    @Transactional(readOnly = true)
    public ApiResponse<List<CategoryTreeResponse>> getCategoryTree(Long warehouseId) {
        log.info(LogMessages.CATEGORY_TREE_FETCHING);

        List<CategoryEntity> allCategories = categoryRepository.findAll();

        // Build tree from root nodes (parentCategoryId == null)
        List<CategoryTreeResponse> tree = allCategories.stream()
                .filter(c -> c.getParentCategoryId() == null)
                .map(root -> buildTreeNode(root, allCategories, warehouseId))
                .collect(Collectors.toList());

        return ApiResponse.success(MessageConstants.CATEGORY_TREE_SUCCESS, tree);
    }

    private CategoryTreeResponse buildTreeNode(
            CategoryEntity node,
            List<CategoryEntity> allCategories,
            Long warehouseId) {

        List<CategoryTreeResponse> children = allCategories.stream()
                .filter(c -> node.getCategoryId().equals(c.getParentCategoryId()))
                .map(child -> buildTreeNode(child, allCategories, warehouseId))
                .collect(Collectors.toList());

        // Convention: zone_code = "Z-" + category_code
        String expectedZoneCode = "Z-" + node.getCategoryCode();
        boolean zoneMapped = false;

        if (warehouseId != null) {
            zoneMapped = zoneRepository
                    .findByWarehouseIdAndZoneCode(warehouseId, expectedZoneCode)
                    .filter(ZoneEntity::getActive)
                    .isPresent();
        }

        return CategoryTreeResponse.builder()
                .categoryId(node.getCategoryId())
                .categoryCode(node.getCategoryCode())
                .categoryName(node.getCategoryName())
                .description(node.getDescription())
                .active(node.getActive())
                .mappedZoneCode(expectedZoneCode)
                .zoneMapped(zoneMapped)
                .children(children)
                .build();
    }

    // ==========================================================
    // UC: Map Category to Zone (Convention-based)
    // ==========================================================

    /**
     * Map category to zone using convention: zone_code = "Z-" + category_code
     *
     * Flow:
     * 1. Lấy category_code từ category
     * 2. Tính zone_code = "Z-" + category_code  (e.g. "HC" → "Z-HC")
     * 3. Tìm zone trong warehouse theo zone_code
     * 4. Validate: zone phải tồn tại + active, category phải active
     * 5. Trả về kết quả mapping
     *
     * BR-CAT-07: Each category must have at least one primary storage zone
     * BR-CAT-08: Inactive categories or zones cannot be used in new mappings
     */
    @Transactional(readOnly = true)
    public ApiResponse<MapCategoryToZoneResponse> mapCategoryToZone(
            Long categoryId,
            MapCategoryToZoneRequest request,
            Long mappedBy,
            String ipAddress,
            String userAgent) {

        log.info(LogMessages.CZM_MAPPING, categoryId, request.getWarehouseId());

        // 1. Validate category
        CategoryEntity category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.CATEGORY_NOT_FOUND, categoryId)));

        // BR-CAT-08: category must be active
        if (!category.getActive()) {
            throw new BusinessException(MessageConstants.CZM_CATEGORY_INACTIVE);
        }

        // 2. Convention: zone_code = "Z-" + category_code
        String expectedZoneCode = "Z-" + category.getCategoryCode();

        // 3. Find zone in warehouse
        ZoneEntity zone = zoneRepository
                .findByWarehouseIdAndZoneCode(request.getWarehouseId(), expectedZoneCode)
                .orElseThrow(() -> new BusinessException(
                        String.format(MessageConstants.CZM_ZONE_NOT_FOUND_CONVENTION,
                                expectedZoneCode, request.getWarehouseId())));

        // BR-CAT-08: zone must be active
        if (!zone.getActive()) {
            throw new BusinessException(
                    String.format(MessageConstants.CZM_ZONE_INACTIVE, expectedZoneCode));
        }

        log.info(LogMessages.CZM_MAPPED, category.getCategoryCode(), expectedZoneCode);

        // Audit
        auditLogService.logAction(
                mappedBy, "CATEGORY_ZONE_MAPPED", "CATEGORY", categoryId,
                "Category " + category.getCategoryCode() + " mapped to zone " + expectedZoneCode
                        + " in warehouse " + request.getWarehouseId(),
                ipAddress, userAgent);

        // 4. Build response
        MapCategoryToZoneResponse response = MapCategoryToZoneResponse.builder()
                .categoryId(category.getCategoryId())
                .categoryCode(category.getCategoryCode())
                .categoryName(category.getCategoryName())
                .zoneId(zone.getZoneId())
                .zoneCode(zone.getZoneCode())
                .zoneName(zone.getZoneName())
                .warehouseId(zone.getWarehouseId())
                .conventionRule("Z-" + category.getCategoryCode())
                .zoneActive(zone.getActive())
                .build();

        return ApiResponse.success(MessageConstants.CZM_MAPPED_SUCCESS, response);
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