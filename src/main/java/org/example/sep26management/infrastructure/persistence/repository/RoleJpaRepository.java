package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleJpaRepository extends JpaRepository<RoleEntity, Long> {

    Optional<RoleEntity> findByRoleCode(String roleCode);

    @Query("SELECT r FROM RoleEntity r LEFT JOIN FETCH r.permissions WHERE r.roleId = :roleId")
    Optional<RoleEntity> findByIdWithPermissions(@Param("roleId") Long roleId);

    boolean existsByRoleCode(String roleCode);
}
