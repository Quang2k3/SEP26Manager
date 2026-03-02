// ===== SalesOrderItemJpaRepository.java =====
package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.SalesOrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SalesOrderItemJpaRepository extends JpaRepository<SalesOrderItemEntity, Long> {
    List<SalesOrderItemEntity> findBySoId(Long soId);
    void deleteBySoId(Long soId);
}