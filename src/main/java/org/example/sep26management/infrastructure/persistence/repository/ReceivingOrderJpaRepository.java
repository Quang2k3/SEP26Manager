package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.ReceivingOrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

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

    /**
     * Atomic QC claim — chỉ set nếu chưa có QC nào claim.
     * WHERE assigned_qc_id IS NULL đảm bảo chỉ 1 QC thành công (affected rows = 1).
     * QC thứ 2 gọi cùng lúc → affected = 0 → BE trả lỗi "đơn đã được QC khác nhận".
     * Caller phải dùng trong @Transactional.
     */
    @Modifying
    @Query("""
            UPDATE ReceivingOrderEntity r
            SET r.assignedQcId = :qcUserId,
                r.updatedAt    = CURRENT_TIMESTAMP
            WHERE r.receivingId   = :receivingId
              AND r.assignedQcId IS NULL
              AND r.status IN ('PENDING_COUNT', 'PENDING_INCIDENT', 'QC_RESCAN')
            """)
    int claimQcAssignment(
            @Param("receivingId") Long receivingId,
            @Param("qcUserId")    Long qcUserId);

    /**
     * Release QC claim — gọi sau khi qcSubmitSession hoàn thành hoặc QC release.
     */
    @Modifying
    @Query("""
            UPDATE ReceivingOrderEntity r
            SET r.assignedQcId = NULL,
                r.updatedAt    = CURRENT_TIMESTAMP
            WHERE r.receivingId   = :receivingId
              AND r.assignedQcId  = :qcUserId
            """)
    int releaseQcAssignment(
            @Param("receivingId") Long receivingId,
            @Param("qcUserId")    Long qcUserId);
}