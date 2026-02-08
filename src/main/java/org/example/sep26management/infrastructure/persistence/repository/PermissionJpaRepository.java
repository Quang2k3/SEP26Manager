package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.PermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PermissionJpaRepository extends JpaRepository<PermissionEntity, Long> {

    Optional<PermissionEntity> findByPermissionCode(String permissionCode);

    boolean existsByPermissionCode(String permissionCode);
}
