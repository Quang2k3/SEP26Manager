package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.SkuEntity;
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
}