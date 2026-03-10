package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.GrnItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GrnItemJpaRepository extends JpaRepository<GrnItemEntity, Long> {
    List<GrnItemEntity> findByGrnGrnId(Long grnId);
}
