package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.ReceivingOrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReceivingOrderJpaRepository extends JpaRepository<ReceivingOrderEntity, Long> {

    Page<ReceivingOrderEntity> findByWarehouseIdAndStatusOrderByCreatedAtDesc(Long warehouseId, String status,
                                                                              Pageable pageable);

    Page<ReceivingOrderEntity> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<ReceivingOrderEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT MAX(r.receivingCode) FROM ReceivingOrderEntity r WHERE r.warehouseId = :warehouseId AND r.receivingCode LIKE 'GRN%'")
    Optional<String> findMaxReceivingCode(@Param("warehouseId") Long warehouseId);

    /**
     * Kiểm tra số chứng từ / PO đã tồn tại chưa (bỏ qua DRAFT và CANCELLED).
     * Dùng để cảnh báo duplicate PO khi tạo phiếu nhận hàng mới.
     */
    @Query("SELECT r FROM ReceivingOrderEntity r " +
            "WHERE LOWER(r.sourceReferenceCode) = LOWER(:sourceReferenceCode) " +
            "AND r.status NOT IN ('DRAFT', 'CANCELLED')")
    java.util.List<ReceivingOrderEntity> findActiveBySourceReferenceCode(
            @Param("sourceReferenceCode") String sourceReferenceCode);
}