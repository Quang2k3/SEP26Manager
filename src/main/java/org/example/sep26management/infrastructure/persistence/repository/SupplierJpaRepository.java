package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.SupplierEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupplierJpaRepository extends JpaRepository<SupplierEntity, Long> {

    Optional<SupplierEntity> findBySupplierCode(String supplierCode);
}
