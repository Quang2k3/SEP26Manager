package org.example.sep26management.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long logId;

    @Column(name = "entity_name", nullable = false, length = 200)
    private String entityName;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "old_data", columnDefinition = "JSONB")
    private String oldData;

    @Column(name = "new_data", columnDefinition = "JSONB")
    private String newData;

    @Column(name = "action_by", nullable = false)
    private Long actionBy;

    @Column(name = "action_at", nullable = false, updatable = false)
    private LocalDateTime actionAt;

    @PrePersist
    protected void onCreate() {
        if (actionAt == null) {
            actionAt = LocalDateTime.now();
        }
    }
}