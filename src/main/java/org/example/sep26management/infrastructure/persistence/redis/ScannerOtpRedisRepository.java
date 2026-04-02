package org.example.sep26management.infrastructure.persistence.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.scan.ScannerOtpData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis repository cho Scanner OTP sessions.
 *
 * Key scheme:
 *   scanner:otp:{sessionId}            — OTP session data, TTL 24h
 *   scanner:ratelimit:{userId}:{ip}    — brute-force counter, TTL 15 phút
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class ScannerOtpRedisRepository {

    private static final String OTP_PREFIX        = "scanner:otp:";
    private static final String RATE_LIMIT_PREFIX  = "scanner:ratelimit:";

    private static final Duration OTP_TTL          = Duration.ofHours(24);
    private static final Duration RATE_LIMIT_TTL   = Duration.ofMinutes(15);
    private static final int      RATE_LIMIT_MAX   = 10;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // ─── OTP Session ──────────────────────────────────────────────────────────

    public void save(ScannerOtpData data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(OTP_PREFIX + data.getSessionId(), json, OTP_TTL);
        } catch (Exception e) {
            log.error("[ScannerOtp] Failed to save session {}", data.getSessionId(), e);
            throw new RuntimeException("Failed to save scanner OTP session", e);
        }
    }

    public Optional<ScannerOtpData> findById(String sessionId) {
        try {
            String json = redisTemplate.opsForValue().get(OTP_PREFIX + sessionId);
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, ScannerOtpData.class));
        } catch (Exception e) {
            log.error("[ScannerOtp] Failed to read session {}", sessionId, e);
            return Optional.empty();
        }
    }

    /**
     * Cập nhật (tăng failedAttempts hoặc set used=true).
     * Không reset TTL — giữ nguyên 24h từ lúc tạo.
     * Cách làm: đọc TTL còn lại → set với TTL đó.
     */
    public void update(ScannerOtpData data) {
        try {
            Long ttl = redisTemplate.getExpire(OTP_PREFIX + data.getSessionId());
            long remainSec = (ttl != null && ttl > 0) ? ttl : OTP_TTL.getSeconds();
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(OTP_PREFIX + data.getSessionId(), json, Duration.ofSeconds(remainSec));
        } catch (Exception e) {
            log.error("[ScannerOtp] Failed to update session {}", data.getSessionId(), e);
        }
    }

    public void delete(String sessionId) {
        redisTemplate.delete(OTP_PREFIX + sessionId);
    }

    /** Trả về giây còn lại (để FE countdown / FE biết TTL cấp cho token) */
    public long getTtlSeconds(String sessionId) {
        Long ttl = redisTemplate.getExpire(OTP_PREFIX + sessionId);
        return (ttl != null && ttl > 0) ? ttl : 0L;
    }

    // ─── Rate Limit ───────────────────────────────────────────────────────────

    public boolean isRateLimited(Long userId, String ip) {
        String key  = RATE_LIMIT_PREFIX + userId + ":" + ip;
        String val  = redisTemplate.opsForValue().get(key);
        if (val == null) return false;
        try { return Integer.parseInt(val) >= RATE_LIMIT_MAX; }
        catch (NumberFormatException e) { return false; }
    }

    public void incrementRateLimit(Long userId, String ip) {
        String key   = RATE_LIMIT_PREFIX + userId + ":" + ip;
        Long   count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, RATE_LIMIT_TTL);
        }
    }

    public void resetRateLimit(Long userId, String ip) {
        redisTemplate.delete(RATE_LIMIT_PREFIX + userId + ":" + ip);
    }
}