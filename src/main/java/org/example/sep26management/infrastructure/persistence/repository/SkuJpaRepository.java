package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.SkuEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SkuJpaRepository extends JpaRepository<SkuEntity, Long> {

    List<SkuEntity> findByCategoryId(Long categoryId);
}