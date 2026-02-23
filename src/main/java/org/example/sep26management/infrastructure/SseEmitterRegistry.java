package org.example.sep26management.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory registry of SSE emitters, keyed by sessionId.
 * Laptop subscribes to /receiving-sessions/{id}/stream and is pushed
 * snapshot events after each scan event.
 */
@Component
@Slf4j
public class SseEmitterRegistry {

    private final ConcurrentMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public void register(String sessionId, SseEmitter emitter) {
        emitters.put(sessionId, emitter);
        emitter.onCompletion(() -> emitters.remove(sessionId));
        emitter.onTimeout(() -> emitters.remove(sessionId));
        emitter.onError(e -> emitters.remove(sessionId));
        log.debug("SSE emitter registered for session {}", sessionId);
    }

    public void send(String sessionId, Object data) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter == null)
            return;
        try {
            emitter.send(SseEmitter.event()
                    .name("snapshot")
                    .data(data));
        } catch (Exception e) {
            log.warn("Failed to send SSE to session {}, removing emitter", sessionId);
            emitters.remove(sessionId);
        }
    }

    public void remove(String sessionId) {
        SseEmitter emitter = emitters.remove(sessionId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        }
    }
}
