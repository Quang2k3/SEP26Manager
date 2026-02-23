package org.example.sep26management.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "putaway_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PutawayTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "putaway_task_id")
    private Long putawayTaskId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "receiving_id", nullable = false)
    private Long receivingId;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "from_location_id")
    private Long fromLocationId;

    @Column(name = "assigned_to")
    private Long assignedTo;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @OneToMany(mappedBy = "putawayTask", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PutawayTaskItemEntity> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null)
            status = "OPEN";
    }
}
