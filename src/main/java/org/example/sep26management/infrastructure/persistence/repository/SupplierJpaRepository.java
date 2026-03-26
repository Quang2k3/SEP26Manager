package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.SupplierEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupplierJpaRepository extends JpaRepository<SupplierEntity, Long> {

    Optional<SupplierEntity> findBySupplierCode(String supplierCode);

    boolean existsBySupplierCodeAndSupplierIdNot(String supplierCode, Long supplierId);

    boolean existsBySupplierCode(String supplierCode);

    @Query("SELECT s FROM SupplierEntity s WHERE " +
            "(:keyword IS NULL OR LOWER(s.supplierName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "   OR LOWER(s.supplierCode) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:activeOnly IS NULL OR s.active = :activeOnly)")
    Page<SupplierEntity> searchSuppliers(
            @Param("keyword") String keyword,
            @Param("activeOnly") Boolean activeOnly,
            Pageable pageable);
}