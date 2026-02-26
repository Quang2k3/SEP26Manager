package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.SkuEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkuJpaRepository extends JpaRepository<SkuEntity, Long> {

    List<SkuEntity> findByCategory_CategoryId(Long categoryId);

    Optional<SkuEntity> findBySkuCode(String skuCode);

    Optional<SkuEntity> findByBarcode(String barcode);

    boolean existsBySkuCode(String skuCode);

    boolean existsByBarcode(String barcode);

    @Query("SELECT s FROM SkuEntity s LEFT JOIN FETCH s.category WHERE s.barcode = :barcode AND s.active = true AND s.deletedAt IS NULL")
    Optional<SkuEntity> findActiveByBarcodeWithCategory(@Param("barcode") String barcode);

    @Query("SELECT s FROM SkuEntity s LEFT JOIN FETCH s.category WHERE s.skuCode = :skuCode AND s.active = true AND s.deletedAt IS NULL")
    Optional<SkuEntity> findActiveBySkuCodeWithCategory(@Param("skuCode") String skuCode);

    @Query("SELECT s FROM SkuEntity s LEFT JOIN FETCH s.category WHERE s.skuId = :skuId")
    Optional<SkuEntity> findByIdWithCategory(@Param("skuId") Long skuId);
    /**
     * UC-B06: Search SKU
     * BR-SKU-06: partial matching (ILIKE), searches across skuCode AND skuName simultaneously
     * BR-GEN-01: when keyword is blank, returns all active SKUs (paginated, default 20)
     */
    @Query("""
            SELECT s FROM SkuEntity s
            LEFT JOIN FETCH s.category
            WHERE s.deletedAt IS NULL
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(s.skuCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(s.skuName) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY s.createdAt DESC
            """)
    Page<SkuEntity> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    /**
     * Count variant for search (used internally when needed)
     */
    @Query("""
            SELECT COUNT(s) FROM SkuEntity s
            WHERE s.deletedAt IS NULL
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(s.skuCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(s.skuName) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    long countByKeyword(@Param("keyword") String keyword);
}