package org.example.sep26management.application.dto.chat;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class SendMessageRequest {
    private String content;
}