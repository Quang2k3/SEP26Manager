package org.example.sep26management.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Publisher: serialize PutawayTaskEvent → JSON → Redis Pub/Sub channel.
 *
 * Redis Pub/Sub đảm bảo mọi instance BE khi scale ngang đều nhận event
 * và push WebSocket đến clients đang connect vào instance đó.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PutawayEventPublisher {

    /** Phải khớp với channel trong PutawayRedisConfig */
    public static final String CHANNEL = "putaway-task-events";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void publish(PutawayTaskEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(CHANNEL, payload);
            log.debug("[Putaway] Published taskId={} status={} → channel={}",
                    event.getTaskId(), event.getNewStatus(), CHANNEL);
        } catch (Exception e) {
            // Không để Redis failure phá vỡ business transaction chính
            log.warn("[Putaway] Failed to publish event: {}", e.getMessage());
        }
    }
}