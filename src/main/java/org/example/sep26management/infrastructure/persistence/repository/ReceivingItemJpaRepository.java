package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.ReceivingItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Repository cho ReceivingItemEntity (bảng receiving_items).
 *
 * Quan hệ: ReceivingItemEntity.receivingOrder → ReceivingOrderEntity
 *           field: receivingOrder.receivingId  (JPQL path)
 *           column: receiving_id               (DB)
 *
 * Thread-safety:
 *   - incrementReceivedQty / decrementReceivedQty dùng UPDATE trực tiếp
 *     → atomic ở DB level, không cần đọc trước → không có lost-update
 *   - findForUpdateByReceivingIdAndSkuId dùng SELECT FOR UPDATE
 *     → dùng khi cần đọc rồi sửa nhiều field cùng lúc (pessimistic lock)
 *   - @Modifying(clearAutomatically, flushAutomatically) đảm bảo
 *     Hibernate flush trước và clear cache sau bulk UPDATE
 */
@Repository
public interface ReceivingItemJpaRepository extends JpaRepository<ReceivingItemEntity, Long> {

    // ─── Basic queries ─────────────────────────────────────────────────────────

    /** Tất cả items của 1 receiving order. */
    List<ReceivingItemEntity> findByReceivingOrderReceivingId(Long receivingId);

    /** Tìm items theo receivingId + skuId (có thể trả về nhiều dòng vì khác LOT). */
    List<ReceivingItemEntity> findByReceivingOrderReceivingIdAndSkuId(
            Long receivingId, Long skuId);

    // ─── Pessimistic lock ──────────────────────────────────────────────────────

    /**
     * SELECT ... FOR UPDATE — dùng khi cần sửa nhiều field trên 1 row đồng thời.
     * Lock row cho đến khi transaction commit/rollback.
     * Caller BẮT BUỘC phải có @Transactional, nếu không lock vô hiệu.
     *
     * Ví dụ dùng: cập nhật condition + reasonCode + attachmentUrl cùng lúc.
     * Không dùng cho receivedQty — hãy dùng incrementReceivedQty (atomic UPDATE).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT i
            FROM ReceivingItemEntity i
            WHERE i.receivingOrder.receivingId = :receivingId
              AND i.skuId = :skuId
            """)
    Optional<ReceivingItemEntity> findForUpdateByReceivingIdAndSkuId(
            @Param("receivingId") Long receivingId,
            @Param("skuId")       Long skuId);

    // ─── Atomic qty mutations ──────────────────────────────────────────────────

    /**
     * Tăng receivedQty bằng atomic UPDATE — thread-safe khi nhiều scanner quét đồng thời.
     *
     * Không đọc row trước (không read-modify-write) → không có lost-update race condition.
     * Trả về số row bị ảnh hưởng:
     *   1 = cập nhật thành công
     *   0 = không tìm thấy ReceivingItem tương ứng (skuId chưa có trong đơn)
     *
     * Lưu ý: @Version trên entity KHÔNG tự tăng qua JPQL bulk UPDATE.
     * Race protection ở đây đến từ DB-level atomic UPDATE, không phải optimistic lock.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ReceivingItemEntity i
            SET i.receivedQty = i.receivedQty + :delta
            WHERE i.receivingOrder.receivingId = :receivingId
              AND i.skuId = :skuId
              AND ((:lotNumber IS NULL AND i.lotNumber IS NULL) OR i.lotNumber = :lotNumber)
            """)
    int incrementReceivedQty(
            @Param("receivingId") Long       receivingId,
            @Param("skuId")       Long       skuId,
            @Param("delta")       BigDecimal delta,
            @Param("lotNumber")   String     lotNumber);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ReceivingItemEntity i
            SET i.receivedQty = CASE
                WHEN i.receivedQty - :delta < 0 THEN 0
                ELSE i.receivedQty - :delta
            END
            WHERE i.receivingOrder.receivingId = :receivingId
              AND i.skuId = :skuId
              AND ((:lotNumber IS NULL AND i.lotNumber IS NULL) OR i.lotNumber = :lotNumber)
            """)
    int decrementReceivedQty(
            @Param("receivingId") Long       receivingId,
            @Param("skuId")       Long       skuId,
            @Param("delta")       BigDecimal delta,
            @Param("lotNumber")   String     lotNumber);
}