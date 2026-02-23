package org.example.sep26management.infrastructure.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.LogMessages;
import org.example.sep26management.domain.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
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

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generate JWT token for user
     */
    public String generateToken(User user, boolean rememberMe) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getUserId());
        claims.put("email", user.getEmail());
        // Store role codes as comma-separated string or list
        claims.put("roles", user.getRoleCodes() != null ? String.join(",", user.getRoleCodes()) : "");
        // Store permission codes as comma-separated string for fine-grained checks on frontend if needed
        claims.put("permissions",
                user.getPermissionCodes() != null ? String.join(",", user.getPermissionCodes()) : "");
        claims.put("fullName", user.getFullName());

        long expiration = rememberMe ? jwtRememberMeExpirationMs : jwtExpirationMs;

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getEmail())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * Get email from JWT token
     */
    public String getEmailFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.getSubject();
    }

    /**
     * Get user ID from JWT token
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.get("userId", Long.class);
    }

    /**
     * Get role codes from JWT token
     */
    public Set<String> getRoleCodesFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        String rolesStr = claims.get("roles", String.class);
        if (rolesStr == null || rolesStr.isEmpty()) {
            return new HashSet<>();
        }
        return Arrays.stream(rolesStr.split(","))
                .collect(Collectors.toSet());
    }

    /**
     * Get permission codes from JWT token
     */
    public Set<String> getPermissionCodesFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        String permissionsStr = claims.get("permissions", String.class);
        if (permissionsStr == null || permissionsStr.isEmpty()) {
            return new HashSet<>();
        }
        return Arrays.stream(permissionsStr.split(","))
                .collect(Collectors.toSet());
    }

    /**
     * Validate JWT token
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (MalformedJwtException ex) {
            log.error(LogMessages.JWT_INVALID_TOKEN);
        } catch (ExpiredJwtException ex) {
            log.error(LogMessages.JWT_EXPIRED_TOKEN);
        } catch (UnsupportedJwtException ex) {
            log.error(LogMessages.JWT_UNSUPPORTED_TOKEN);
        } catch (IllegalArgumentException ex) {
            log.error(LogMessages.JWT_CLAIMS_EMPTY);
        }
        return false;
    }

    /**
     * Generate a short-lived pending token for OTP verification.
     * This token carries the user's email and expires in 10 minutes.
     *
     * @param email User email
     * @return Pending JWT token
     */
    public String generatePendingToken(String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "PENDING_OTP");

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + 10 * 60 * 1000L); // 10 minutes

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(email)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * Extract email from a pending OTP token.
     * Validates that the token type is PENDING_OTP.
     *
     * @param token Pending token
     * @return Email from the token
     * @throws JwtException if token is invalid, expired, or not a pending token
     */
    public String getEmailFromPendingToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        String type = claims.get("type", String.class);
        if (!"PENDING_OTP".equals(type)) {
            throw new JwtException("Invalid token type");
        }

        return claims.getSubject();
    }

    /**
     * Generate a scan token for iPhone scanner (no DB user required).
     * Claims: type=SCANNER, sessionId, exp=10 minutes.
     */
    public String generateScanToken(String sessionId, Long warehouseId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "SCANNER");
        claims.put("sessionId", sessionId);
        claims.put("warehouseId", warehouseId);
        claims.put("roles", "SCANNER");

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + 10 * 60 * 1000L); // 10 minutes

        return Jwts.builder()
                .setClaims(claims)
                .setSubject("scanner:" + sessionId)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * Check if a JWT token is a SCANNER type token.
     */
    public boolean isScanToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return "SCANNER".equals(claims.get("type", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extract sessionId from a SCANNER token.
     */
    public String getSessionIdFromScanToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        String type = claims.get("type", String.class);
        if (!"SCANNER".equals(type)) {
            throw new JwtException("Not a scanner token");
        }
        return claims.get("sessionId", String.class);
    }
}
