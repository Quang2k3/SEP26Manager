package org.example.sep26management.infrastructure.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.LogMessages;
import org.example.sep26management.domain.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms}")
    private long jwtExpirationMs;

    @Value("${jwt.remember-me-expiration-ms}")
    private long jwtRememberMeExpirationMs;

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    public JwtTokenProvider(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // ─── Full user token ──────────────────────────────────────────────────────

    public String generateToken(User user, boolean rememberMe) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId",      user.getUserId());
        claims.put("email",       user.getEmail());
        claims.put("roles",       user.getRoleCodes()      != null ? String.join(",", user.getRoleCodes())      : "");
        claims.put("permissions", user.getPermissionCodes() != null ? String.join(",", user.getPermissionCodes()) : "");
        claims.put("fullName",    user.getFullName());
        claims.put("warehouseIds", user.getWarehouseIds()  != null ? user.getWarehouseIds() : List.of());

        long expiration = rememberMe ? jwtRememberMeExpirationMs : jwtExpirationMs;

        Date now        = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getEmail())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    // ─── Scanner token (hiện tại — TTL cứng 2h, dùng cho QR token cũ) ────────

    public String generateScanToken(String sessionId, Long warehouseId, String role, Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type",        "SCANNER");
        claims.put("sessionId",   sessionId);
        claims.put("warehouseId", warehouseId);
        claims.put("roles",       role != null ? role : "KEEPER");
        if (userId != null) claims.put("userId", userId);

        Date now        = new Date();
        Date expiryDate = new Date(now.getTime() + 2 * 60 * 60 * 1000L); // 2h

        return Jwts.builder()
                .setClaims(claims)
                .setSubject("scanner:" + sessionId)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * Scanner Temporary Token — cấp sau khi OTP verify thành công.
     *
     * Khác generateScanToken():
     *  - TTL động (= thời gian còn lại của OTP session, tối đa 24h)
     *  - type = "SCANNER_TEMP" để phân biệt với scan token cũ
     *
     * @param tokenTtlMs TTL tính bằng milliseconds
     */
    public String generateScannerTemporaryToken(
            String sessionId, Long warehouseId, String role, Long userId, long tokenTtlMs) {

        Map<String, Object> claims = new HashMap<>();
        claims.put("type",        "SCANNER_TEMP");
        claims.put("sessionId",   sessionId);
        claims.put("warehouseId", warehouseId);
        claims.put("roles",       role != null ? role : "KEEPER");
        if (userId != null) claims.put("userId", userId);

        long safeTtl = Math.max(60_000L, Math.min(tokenTtlMs, 86_400_000L)); // min 1 phút, max 24h

        Date now        = new Date();
        Date expiryDate = new Date(now.getTime() + safeTtl);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject("scanner:" + sessionId)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    // ─── Pending OTP token (login 2FA) ────────────────────────────────────────

    public String generatePendingToken(String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "PENDING_OTP");

        Date now        = new Date();
        Date expiryDate = new Date(now.getTime() + 10 * 60 * 1000L);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(email)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    // ─── Extractors ───────────────────────────────────────────────────────────

    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public Long getUserIdFromToken(String token) {
        Object uid = parseClaims(token).get("userId");
        if (uid == null)              return null;
        if (uid instanceof Long)      return (Long) uid;
        if (uid instanceof Integer)   return ((Integer) uid).longValue();
        if (uid instanceof Number)    return ((Number) uid).longValue();
        try { return Long.parseLong(uid.toString()); } catch (Exception e) { return null; }
    }

    public Set<String> getRoleCodesFromToken(String token) {
        String rolesStr = parseClaims(token).get("roles", String.class);
        if (rolesStr == null || rolesStr.isEmpty()) return new HashSet<>();
        return Arrays.stream(rolesStr.split(",")).collect(Collectors.toSet());
    }

    @SuppressWarnings("unchecked")
    public List<Long> getWarehouseIdsFromToken(String token) {
        Object raw = parseClaims(token).get("warehouseIds");
        if (raw == null)        return Collections.emptyList();
        if (raw instanceof List) {
            return ((List<?>) raw).stream()
                    .map(v -> ((Number) v).longValue())
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    public Set<String> getPermissionCodesFromToken(String token) {
        String str = parseClaims(token).get("permissions", String.class);
        if (str == null || str.isEmpty()) return new HashSet<>();
        return Arrays.stream(str.split(",")).collect(Collectors.toSet());
    }

    public String getEmailFromPendingToken(String token) {
        Claims claims = parseClaims(token);
        if (!"PENDING_OTP".equals(claims.get("type", String.class)))
            throw new JwtException("Invalid token type");
        return claims.getSubject();
    }

    /**
     * Lấy warehouseId (singular) từ SCANNER / SCANNER_TEMP token.
     * Dùng trong JwtAuthenticationFilter để bridge sang warehouseIds list
     * mà ReceivingSessionController cần.
     */
    public Long getWarehouseIdFromScanToken(String token) {
        try {
            Object raw = parseClaims(token).get("warehouseId");
            if (raw == null)            return null;
            if (raw instanceof Long l)  return l;
            if (raw instanceof Integer i) return i.longValue();
            if (raw instanceof Number n)  return n.longValue();
            return Long.parseLong(raw.toString());
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isScanToken(String token) {
        try {
            String type = parseClaims(token).get("type", String.class);
            return "SCANNER".equals(type) || "SCANNER_TEMP".equals(type);
        } catch (Exception e) { return false; }
    }

    public String getSessionIdFromScanToken(String token) {
        Claims claims = parseClaims(token);
        String type   = claims.get("type", String.class);
        if (!"SCANNER".equals(type) && !"SCANNER_TEMP".equals(type))
            throw new JwtException("Not a scanner token");
        return claims.get("sessionId", String.class);
    }

    // ─── Validate & Blacklist ─────────────────────────────────────────────────

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token);
            if (isBlacklisted(token)) {
                log.warn("JWT token is blacklisted (user already logged out)");
                return false;
            }
            return true;
        } catch (MalformedJwtException ex)    { log.error(LogMessages.JWT_INVALID_TOKEN); }
        catch (ExpiredJwtException ex)       { log.error(LogMessages.JWT_EXPIRED_TOKEN); }
        catch (UnsupportedJwtException ex)   { log.error(LogMessages.JWT_UNSUPPORTED_TOKEN); }
        catch (IllegalArgumentException ex)  { log.error(LogMessages.JWT_CLAIMS_EMPTY); }
        return false;
    }

    public void blacklistToken(String token) {
        try {
            Claims claims     = parseClaims(token);
            long  remainingMs = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (remainingMs > 0) {
                redisTemplate.opsForValue().set(BLACKLIST_PREFIX + token, "1", Duration.ofMillis(remainingMs));
                log.info("JWT token blacklisted, TTL={}ms", remainingMs);
            }
        } catch (Exception ex) {
            log.warn("Could not blacklist token (may already be expired): {}", ex.getMessage());
        }
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token));
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}