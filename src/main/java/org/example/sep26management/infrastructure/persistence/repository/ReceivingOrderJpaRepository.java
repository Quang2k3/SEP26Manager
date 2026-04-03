package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.ReceivingOrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    // ── Owner-scoped queries — Keeper chỉ thấy đơn do mình tạo ─────────────

    /** Tất cả đơn của 1 Keeper, sắp xếp mới nhất trước. */
    Page<ReceivingOrderEntity> findByCreatedByOrderByCreatedAtDesc(
            Long createdBy, Pageable pageable);

    /** Đơn của 1 Keeper theo status. */
    Page<ReceivingOrderEntity> findByStatusAndCreatedByOrderByCreatedAtDesc(
            String status, Long createdBy, Pageable pageable);


    /**
     * SELECT FOR UPDATE — chống double-submit/double-status-change.
     * Dùng trong mọi phương thức thay đổi status ReceivingOrder.
     * Khóa row cho đến khi transaction commit → T2 thấy status mới → fail validateStatus.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ReceivingOrderEntity r WHERE r.receivingId = :id")
    java.util.Optional<ReceivingOrderEntity> findByIdForUpdate(@Param("id") Long id);

    // ── QC claim lock ────────────────────────────────────────────────────────

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