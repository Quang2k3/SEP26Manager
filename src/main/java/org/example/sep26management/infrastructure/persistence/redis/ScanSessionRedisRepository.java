package org.example.sep26management.infrastructure.persistence.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.scan.ScanSessionData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Slf4j
public class ScanSessionRedisRepository {

    private static final String KEY_PREFIX = "scan:session:";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void save(String sessionId, ScanSessionData data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(KEY_PREFIX + sessionId, json, TTL);
        } catch (Exception e) {
            log.error("Failed to save scan session {} to Redis", sessionId, e);
            throw new RuntimeException("Failed to save scan session", e);
        }
    }

    public Optional<ScanSessionData> findById(String sessionId) {
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + sessionId);
            if (json == null)
                return Optional.empty();
            return Optional.of(objectMapper.readValue(json, ScanSessionData.class));
        } catch (Exception e) {
            log.error("Failed to read scan session {} from Redis", sessionId, e);
            return Optional.empty();
        }
    }

    public void delete(String sessionId) {
        redisTemplate.delete(KEY_PREFIX + sessionId);
    }

    public void refreshTtl(String sessionId) {
        redisTemplate.expire(KEY_PREFIX + sessionId, TTL);
    }
}
