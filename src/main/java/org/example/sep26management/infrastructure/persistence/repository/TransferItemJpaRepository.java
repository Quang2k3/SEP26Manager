package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.TransferItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransferItemJpaRepository extends JpaRepository<TransferItemEntity, Long> {
    List<TransferItemEntity> findByTransferId(Long transferId);
    void deleteByTransferId(Long transferId);
}








