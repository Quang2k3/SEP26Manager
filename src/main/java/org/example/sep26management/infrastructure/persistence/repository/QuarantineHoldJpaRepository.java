package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.QuarantineHoldEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuarantineHoldJpaRepository extends JpaRepository<QuarantineHoldEntity, Long> {

    List<QuarantineHoldEntity> findByLotId(Long lotId);

    List<QuarantineHoldEntity> findByWarehouseIdAndReleaseAtIsNull(Long warehouseId);
}
