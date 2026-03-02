package org.example.sep26management.infrastructure.mapper;

import org.example.sep26management.application.dto.response.SkuResponse;
import org.example.sep26management.infrastructure.persistence.entity.CategoryEntity;
import org.example.sep26management.infrastructure.persistence.entity.SkuEntity;
import org.springframework.stereotype.Component;

/**
 * SkuMapper — maps SkuEntity to SkuResponse DTO.
 * <p>
 * Extracted from the private toResponse() method in SkuService.
 * Follows the same convention as UserEntityMapper and UserMapper
 * in this package.
 */
@Component
public class SkuMapper {

    /**
     * Map SkuEntity (with category eagerly loaded) → SkuResponse
     */
    public SkuResponse toResponse(SkuEntity entity) {
        if (entity == null)
            return null;

        CategoryEntity cat = entity.getCategory();
        return SkuResponse.builder()
                .skuId(entity.getSkuId())
                .skuCode(entity.getSkuCode())
                .skuName(entity.getSkuName())
                .description(entity.getDescription())
                .brand(entity.getBrand())
                .barcode(entity.getBarcode())
                .unit(entity.getUnit())
                .packageType(entity.getPackageType())
                .volumeMl(entity.getVolumeMl())
                .weightG(entity.getWeightG())
                .originCountry(entity.getOriginCountry())
                .scent(entity.getScent())
                .imageUrl(entity.getImageUrl())
                .shelfLifeDays(entity.getShelfLifeDays())
                .storageTempMin(entity.getStorageTempMin())
                .storageTempMax(entity.getStorageTempMax())
                .active(entity.getActive())
                .categoryId(cat != null ? cat.getCategoryId() : null)
                .categoryCode(cat != null ? cat.getCategoryCode() : null)
                .categoryName(cat != null ? cat.getCategoryName() : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
