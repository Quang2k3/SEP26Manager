package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.ChatMessageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ChatMessageJpaRepository extends JpaRepository<ChatMessageEntity, Long> {

    /** Messages trong phòng, mới nhất trước, có phân trang */
    Page<ChatMessageEntity> findByRoomIdOrderByCreatedAtDesc(Long roomId, Pageable pageable);

    /** Đếm tin chưa đọc cho user trong phòng */
    @Query(value = """
        SELECT COUNT(*) FROM chat_messages m
        WHERE m.room_id = :roomId
          AND m.sender_id != :userId
          AND NOT EXISTS (
              SELECT 1 FROM chat_message_reads r
              WHERE r.message_id = m.message_id AND r.user_id = :userId
          )
        """, nativeQuery = true)
    long countUnread(@Param("roomId") Long roomId, @Param("userId") Long userId);

    /** Tổng tin chưa đọc của user (tất cả phòng) */
    @Query(value = """
        SELECT COUNT(*) FROM chat_messages m
        JOIN chat_room_members mb ON mb.room_id = m.room_id AND mb.user_id = :userId
        WHERE m.sender_id != :userId
          AND NOT EXISTS (
              SELECT 1 FROM chat_message_reads r
              WHERE r.message_id = m.message_id AND r.user_id = :userId
          )
        """, nativeQuery = true)
    long countTotalUnread(@Param("userId") Long userId);

    /** Mark tất cả tin trong phòng là đã đọc */
    @Query(value = """
        INSERT INTO chat_message_reads (message_id, user_id)
        SELECT m.message_id, :userId FROM chat_messages m
        WHERE m.room_id = :roomId
          AND m.sender_id != :userId
          AND NOT EXISTS (
              SELECT 1 FROM chat_message_reads r
              WHERE r.message_id = m.message_id AND r.user_id = :userId
          )
        """, nativeQuery = true)
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    void markAllRead(@Param("roomId") Long roomId, @Param("userId") Long userId);
}