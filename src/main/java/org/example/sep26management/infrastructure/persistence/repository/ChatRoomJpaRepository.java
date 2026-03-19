package org.example.sep26management.infrastructure.persistence.repository;

import org.example.sep26management.infrastructure.persistence.entity.ChatRoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomJpaRepository extends JpaRepository<ChatRoomEntity, Long> {

    /** Tất cả phòng chat mà user là thành viên */
    @Query("SELECT r FROM ChatRoomEntity r WHERE :userId MEMBER OF r.memberIds ORDER BY r.createdAt DESC")
    List<ChatRoomEntity> findByMember(@Param("userId") Long userId);

    /** Tìm phòng DIRECT giữa 2 user (tránh tạo trùng) */
    @Query("""
        SELECT r FROM ChatRoomEntity r
        WHERE r.roomType = 'DIRECT'
          AND :u1 MEMBER OF r.memberIds
          AND :u2 MEMBER OF r.memberIds
          AND SIZE(r.memberIds) = 2
        """)
    Optional<ChatRoomEntity> findDirectRoom(
            @Param("u1") Long userId1,
            @Param("u2") Long userId2);

    /** Tìm phòng gắn với đơn hàng (GRN/SO) */
    Optional<ChatRoomEntity> findByRefTypeAndRefId(String refType, Long refId);
}