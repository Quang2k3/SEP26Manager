package org.example.sep26management.infrastructure.persistence.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.scan.ScanSessionData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Slf4j
public class ScanSessionRedisRepository {

    private static final String KEY_PREFIX = "scan:session:";
    private static final String ACTIVE_SESSION_PREFIX = "scan:active_session:";
    private static final Duration TTL = Duration.ofHours(1);

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

    public void saveActiveSession(Long warehouseId, Long userId, String sessionId) {
        String key = ACTIVE_SESSION_PREFIX + warehouseId + ":" + userId;
        redisTemplate.opsForValue().set(key, sessionId, TTL);
    }

    public Optional<String> findActiveSession(Long warehouseId, Long userId) {
        String key = ACTIVE_SESSION_PREFIX + warehouseId + ":" + userId;
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    public void deleteActiveSession(Long warehouseId, Long userId) {
        String key = ACTIVE_SESSION_PREFIX + warehouseId + ":" + userId;
        redisTemplate.delete(key);
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

    /**
     * Cập nhật session lines bằng Lua script — atomic trên Redis.
     * Redis single-threaded nên Lua không bị interrupt giửa chng.
     * Trả về newQty dạng String, hoặc null nếu session hết hạn.
     */
    private static final String LUA_UPDATE_LINE =
            "local raw = redis.call('GET', KEYS[1])\n" +
            "if not raw then return nil end\n" +
            "local data = cjson.decode(raw)\n" +
            "if not data.lines then data.lines = {} end\n" +
            "local skuId = ARGV[1]\n" +
            "local cond = ARGV[2]\n" +
            "local delta = tonumber(ARGV[3])\n" +
            "local ttl = tonumber(ARGV[4])\n" +
            "local skuCode = ARGV[5]\n" +
            "local skuName = ARGV[6]\n" +
            "local barcode = ARGV[7]\n" +
            "local found = false\n" +
            "local newQty = delta\n" +
            "for i, line in ipairs(data.lines) do\n" +
            "  if tostring(line.skuId) == skuId and line.condition == cond then\n" +
            "    line.qty = (line.qty or 0) + delta\n" +
            "    newQty = line.qty\n" +
            "    found = true\n" +
            "    break\n" +
            "  end\n" +
            "end\n" +
            "if not found then\n" +
            "  table.insert(data.lines, {skuId=tonumber(skuId), condition=cond, qty=delta, skuCode=skuCode, skuName=skuName, barcode=barcode})\n" +
            "end\n" +
            "redis.call('SET', KEYS[1], cjson.encode(data), 'EX', ttl)\n" +
            "return tostring(newQty)\n";

    public String atomicUpdateLine(String sessionId, Long skuId, String condition, java.math.BigDecimal delta, String skuCode, String skuName, String barcode) {
        try {
            DefaultRedisScript<String> script = new DefaultRedisScript<>(LUA_UPDATE_LINE, String.class);
            long ttlSec = TTL.getSeconds();
            Long currentTtl = redisTemplate.getExpire(KEY_PREFIX + sessionId);
            if (currentTtl != null && currentTtl > 0) ttlSec = currentTtl;
            Object result = redisTemplate.execute(
                    script,
                    List.of(KEY_PREFIX + sessionId),
                    String.valueOf(skuId),
                    condition,
                    delta.toPlainString(),
                    String.valueOf(ttlSec),
                    skuCode != null ? skuCode : "",
                    skuName != null ? skuName : "",
                    barcode != null ? barcode : ""
            );
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            log.error("[ScanSession] atomicUpdateLine failed sessionId={} sku={}: {}", sessionId, skuId, e.getMessage());
            return null;
        }
    }
}