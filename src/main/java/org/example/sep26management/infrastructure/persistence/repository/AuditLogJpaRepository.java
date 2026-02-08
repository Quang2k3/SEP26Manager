package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogJpaRepository extends JpaRepository<AuditLogEntity, Long> {

    List<AuditLogEntity> findByActionByOrderByActionAtDesc(Long actionBy);

    List<AuditLogEntity> findByActionOrderByActionAtDesc(String action);

    List<AuditLogEntity> findByEntityNameAndEntityIdOrderByActionAtDesc(
            String entityName,
            Long entityId
    );

    List<AuditLogEntity> findByActionAtBetweenOrderByActionAtDesc(
            LocalDateTime start,
            LocalDateTime end
    );
}