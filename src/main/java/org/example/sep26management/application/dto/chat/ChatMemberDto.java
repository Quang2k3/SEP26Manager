package org.example.sep26management.application.dto.chat;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ChatMemberDto {
    private Long userId;
    private String fullName;
    private String avatarUrl;
    private String roleCode;
}