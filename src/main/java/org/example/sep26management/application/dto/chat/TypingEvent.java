package org.example.sep26management.application.dto.chat;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class TypingEvent {
    private Long roomId;
    private Long userId;
    private String userName;
    private boolean typing;
}