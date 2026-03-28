package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadSessionService {

    private final StringRedisTemplate redisTemplate;
    private static final String PREFIX = "upload_session:";
    private static final Duration TTL = Duration.ofMinutes(15);

    public String createSession() {
        String uuid = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(PREFIX + uuid, "", TTL);
        return uuid;
    }

    public void completeSession(String uuid, String url) {
        Boolean hasKey = redisTemplate.hasKey(PREFIX + uuid);
        if (Boolean.TRUE.equals(hasKey)) {
            redisTemplate.opsForValue().set(PREFIX + uuid, url, TTL);
        } else {
            throw new BusinessException("Session upload không tồn tại hoặc đã hết hạn.");
        }
    }

    public String getSessionUrl(String uuid) {
        Boolean hasKey = redisTemplate.hasKey(PREFIX + uuid);
        if (Boolean.FALSE.equals(hasKey)) {
            return null; // Expired or not found
        }
        return redisTemplate.opsForValue().get(PREFIX + uuid);
    }
}
