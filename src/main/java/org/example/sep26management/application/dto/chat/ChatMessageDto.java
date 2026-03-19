package org.example.sep26management.application.dto.chat;

import lombok.*;
import java.time.LocalDateTime;

/** DTO dùng cho cả REST response và WebSocket broadcast */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ChatMessageDto {
    private Long messageId;
    private Long roomId;
    private Long senderId;
    private String senderName;
    private String senderAvatar;
    private String content;
    private LocalDateTime createdAt;
    private String type; // "MESSAGE" | "JOIN" | "LEAVE" | "TYPING"
}