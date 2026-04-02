package org.example.sep26management.application.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Event object cho Realtime Putaway Task.
 *
 * Flow:
 *   PutawayTaskService  →  PutawayEventPublisher.publish()
 *     → Redis Pub/Sub channel "putaway-task-events"
 *       → PutawayEventSubscriber (mọi instance nhận)
 *         → SimpMessagingTemplate → /topic/putaway/{warehouseId}
 *           → FE (STOMP subscriber)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PutawayTaskEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long   taskId;
    private Long   warehouseId;
    private String newStatus;   // PENDING / IN_PROGRESS / DONE
    private String oldStatus;
    private String grnCode;
    private Long   actorUserId;

    /** "STATUS_CHANGED" | "ALLOCATED" | "CONFIRMED" */
    private String eventType;

    @Builder.Default
    private String timestamp = Instant.now().toString();
}