package org.example.sep26management.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity @Table(name = "chat_rooms")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChatRoomEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id") private Long roomId;

    @Column(name = "room_type", nullable = false, length = 20)
    private String roomType; // DIRECT | ORDER

    @Column(name = "ref_type", length = 50)
    private String refType;  // GRN | SO | null

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "name", length = 200)
    private String name;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "chat_room_members",
            joinColumns = @JoinColumn(name = "room_id"))
    @Column(name = "user_id")
    @Builder.Default
    private Set<Long> memberIds = new HashSet<>();

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}