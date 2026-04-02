package org.example.sep26management.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Subscriber: nhận JSON từ Redis Pub/Sub "putaway-task-events"
 * → push STOMP tới /topic/putaway/{warehouseId}
 *
 * FE subscribe: client.subscribe('/topic/putaway/{warehouseId}', handler)
 *
 * Payload FE nhận:
 * {
 *   "type": "putaway_status_changed",
 *   "taskId": 42,
 *   "warehouseId": 1,
 *   "newStatus": "DONE",
 *   "oldStatus": "IN_PROGRESS",
 *   "grnCode": "GRN-...",
 *   "actorUserId": 5,
 *   "eventType": "CONFIRMED",
 *   "timestamp": "2025-09-26T..."
 * }
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PutawayEventSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody());
            PutawayTaskEvent event = objectMapper.readValue(body, PutawayTaskEvent.class);

            log.debug("[Putaway] Received from Redis: taskId={} status={} warehouseId={}",
                    event.getTaskId(), event.getNewStatus(), event.getWarehouseId());

            Map<String, Object> payload = new HashMap<>();
            payload.put("type",        "putaway_status_changed");
            payload.put("taskId",      event.getTaskId());
            payload.put("warehouseId", event.getWarehouseId());
            payload.put("newStatus",   event.getNewStatus());
            payload.put("oldStatus",   event.getOldStatus() != null ? event.getOldStatus() : "");
            payload.put("grnCode",     event.getGrnCode()   != null ? event.getGrnCode()   : "");
            payload.put("actorUserId", event.getActorUserId() != null ? event.getActorUserId() : 0L);
            payload.put("eventType",   event.getEventType()  != null ? event.getEventType()  : "STATUS_CHANGED");
            payload.put("timestamp",   event.getTimestamp());

            String destination = "/topic/putaway/" + event.getWarehouseId();
            messagingTemplate.convertAndSend(destination, payload);
            log.info("[Putaway] WS pushed → {} | taskId={} status={}",
                    destination, event.getTaskId(), event.getNewStatus());

        } catch (Exception e) {
            log.warn("[Putaway] Failed to process Redis message: {}", e.getMessage());
        }
    }
}