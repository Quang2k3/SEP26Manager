package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.chat.*;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.example.sep26management.infrastructure.persistence.entity.*;
import org.example.sep26management.infrastructure.persistence.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatRoomJpaRepository roomRepo;
    private final ChatMessageJpaRepository messageRepo;
    private final UserJpaRepository userRepo;
    private final SimpMessagingTemplate messagingTemplate;

    // ─── Rooms ────────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<ChatRoomDto> getOrCreateRoom(Long currentUserId, CreateRoomRequest req) {
        if ("DIRECT".equals(req.getRoomType())) {
            if (req.getMemberIds() == null || req.getMemberIds().isEmpty())
                throw new BusinessException("Direct room cần ít nhất 1 user khác");
            Long otherId = req.getMemberIds().get(0);
            // Tìm phòng đã tồn tại
            Optional<ChatRoomEntity> existing = roomRepo.findDirectRoom(currentUserId, otherId);
            if (existing.isPresent()) {
                return ApiResponse.success("OK", toDto(existing.get(), currentUserId));
            }
        } else if ("ORDER".equals(req.getRoomType()) && req.getRefType() != null && req.getRefId() != null) {
            Optional<ChatRoomEntity> existing = roomRepo.findByRefTypeAndRefId(req.getRefType(), req.getRefId());
            if (existing.isPresent()) {
                // Thêm current user nếu chưa là thành viên
                ChatRoomEntity room = existing.get();
                room.getMemberIds().add(currentUserId);
                roomRepo.save(room);
                return ApiResponse.success("OK", toDto(room, currentUserId));
            }
        }

        // Tạo phòng mới
        Set<Long> members = new HashSet<>();
        members.add(currentUserId);
        if (req.getMemberIds() != null) members.addAll(req.getMemberIds());

        // Nếu ORDER room: tự động thêm tất cả managers (nếu không có members cụ thể)
        if ("ORDER".equals(req.getRoomType()) && req.getMemberIds() == null) {
            userRepo.findAll().stream()
                    .filter(u -> u.getRoles() != null &&
                            u.getRoles().stream().anyMatch(r -> "MANAGER".equals(r.getRoleCode())))
                    .forEach(u -> members.add(u.getUserId()));
        }

        ChatRoomEntity room = ChatRoomEntity.builder()
                .roomType(req.getRoomType())
                .refType(req.getRefType())
                .refId(req.getRefId())
                .name(req.getName())
                .memberIds(members)
                .build();
        room = roomRepo.save(room);
        return ApiResponse.success("Room created", toDto(room, currentUserId));
    }

    @Transactional(readOnly = true)
    public ApiResponse<List<ChatRoomDto>> listRooms(Long userId) {
        List<ChatRoomEntity> rooms = roomRepo.findByMember(userId);
        List<ChatRoomDto> dtos = rooms.stream()
                .map(r -> toDto(r, userId))
                .collect(Collectors.toList());
        return ApiResponse.success("OK", dtos);
    }

    // ─── Messages ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<List<ChatMessageDto>> getMessages(Long roomId, Long userId, int page, int size) {
        assertMember(roomId, userId);
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        List<ChatMessageDto> msgs = messageRepo
                .findByRoomIdOrderByCreatedAtDesc(roomId, pageable)
                .getContent()
                .stream()
                .map(this::toMessageDto)
                .collect(Collectors.toList());
        Collections.reverse(msgs); // chronological order
        return ApiResponse.success("OK", msgs);
    }

    @Transactional
    public ChatMessageDto sendMessage(Long roomId, Long senderId, String content) {
        assertMember(roomId, senderId);
        if (content == null || content.isBlank())
            throw new BusinessException("Nội dung tin nhắn không được trống");

        ChatMessageEntity msg = ChatMessageEntity.builder()
                .roomId(roomId)
                .senderId(senderId)
                .content(content.trim())
                .build();
        msg = messageRepo.save(msg);
        ChatMessageDto dto = toMessageDto(msg);

        // Broadcast to all room members via WebSocket
        messagingTemplate.convertAndSend("/topic/room." + roomId, dto);
        log.debug("Message sent: roomId={} senderId={}", roomId, senderId);
        return dto;
    }

    @Transactional
    public ApiResponse<Void> markRead(Long roomId, Long userId) {
        assertMember(roomId, userId);
        messageRepo.markAllRead(roomId, userId);
        // Notify sender(s) that messages were read
        messagingTemplate.convertAndSend("/topic/room." + roomId + ".read",
                Map.of("roomId", roomId, "readBy", userId));
        return ApiResponse.success("Marked as read", null);
    }

    public ApiResponse<Long> getTotalUnread(Long userId) {
        return ApiResponse.success("OK", messageRepo.countTotalUnread(userId));
    }

    // ─── Typing ───────────────────────────────────────────────────────────────

    public void broadcastTyping(Long roomId, Long userId, boolean typing) {
        var room = roomRepo.findById(roomId).orElse(null);
        if (room == null || !room.getMemberIds().contains(userId)) return;
        var user = userRepo.findById(userId).orElse(null);
        var event = new TypingEvent(roomId, userId,
                user != null ? user.getFullName() : "User", typing);
        messagingTemplate.convertAndSend("/topic/room." + roomId + ".typing", event);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void assertMember(Long roomId, Long userId) {
        ChatRoomEntity room = roomRepo.findById(roomId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy phòng chat #" + roomId));
        if (!room.getMemberIds().contains(userId))
            throw new BusinessException("Bạn không phải thành viên phòng này");
    }

    private ChatRoomDto toDto(ChatRoomEntity room, Long currentUserId) {
        List<ChatMemberDto> members = room.getMemberIds().stream()
                .map(uid -> userRepo.findById(uid)
                        .map(u -> ChatMemberDto.builder()
                                .userId(u.getUserId())
                                .fullName(u.getFullName())
                                .avatarUrl(u.getAvatarUrl())
                                .build())
                        .orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // Last message
        var lastPage = messageRepo.findByRoomIdOrderByCreatedAtDesc(
                room.getRoomId(), PageRequest.of(0, 1));
        ChatMessageDto lastMsg = lastPage.hasContent()
                ? toMessageDto(lastPage.getContent().get(0)) : null;

        long unread = messageRepo.countUnread(room.getRoomId(), currentUserId);

        // Auto-name DIRECT rooms from the other member's name
        String displayName = room.getName();
        if (displayName == null && "DIRECT".equals(room.getRoomType())) {
            displayName = members.stream()
                    .filter(m -> !m.getUserId().equals(currentUserId))
                    .map(ChatMemberDto::getFullName)
                    .findFirst().orElse("Direct Chat");
        }

        return ChatRoomDto.builder()
                .roomId(room.getRoomId())
                .roomType(room.getRoomType())
                .refType(room.getRefType())
                .refId(room.getRefId())
                .name(displayName)
                .createdAt(room.getCreatedAt())
                .members(members)
                .lastMessage(lastMsg)
                .unreadCount(unread)
                .build();
    }

    private ChatMessageDto toMessageDto(ChatMessageEntity msg) {
        var user = userRepo.findById(msg.getSenderId()).orElse(null);
        return ChatMessageDto.builder()
                .messageId(msg.getMessageId())
                .roomId(msg.getRoomId())
                .senderId(msg.getSenderId())
                .senderName(user != null ? user.getFullName() : "Unknown")
                .senderAvatar(user != null ? user.getAvatarUrl() : null)
                .content(msg.getContent())
                .createdAt(msg.getCreatedAt())
                .type("MESSAGE")
                .build();
    }
}