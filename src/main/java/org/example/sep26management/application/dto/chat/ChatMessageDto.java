package org.example.sep26management.application.dto.chat;

import com.fasterxml.jackson.annotation.JsonFormat;
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

    /**
     * Ép Jackson serialize thành ISO-8601 string thay vì array [y,m,d,h,min,s].
     * FE dùng new Date(createdAt) sẽ parse đúng.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private LocalDateTime createdAt;

    private String type; // "MESSAGE" | "JOIN" | "LEAVE" | "TYPING"
}