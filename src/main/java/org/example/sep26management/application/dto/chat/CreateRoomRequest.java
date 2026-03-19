package org.example.sep26management.application.dto.chat;

import lombok.*;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor
public class CreateRoomRequest {
    private String roomType;       // DIRECT | ORDER
    private List<Long> memberIds;  // userIds to add (excluding self)
    private String refType;        // optional: GRN | SO
    private Long refId;            // optional
    private String name;           // optional display name
}