package org.example.sep26management.application.dto.chat;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ChatRoomDto {
    private Long roomId;
    private String roomType;
    private String refType;
    private Long refId;
    private String name;
    private LocalDateTime createdAt;
    private List<ChatMemberDto> members;
    private ChatMessageDto lastMessage;
    private long unreadCount;
}