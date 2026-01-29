package org.example.sep26management.infrastructure.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration:3600000}")
    private Long jwtExpiration;

    @Value("${app.jwt.remember-me-expiration:604800000}")
    private Long rememberMeExpiration;

    /**
     * Decode BASE64 secret key (HS512 requires >= 512 bits)
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);

        // Debug when DEV (optional)
        log.debug("JWT signing key length (bytes): {}", keyBytes.length);

        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generate JWT token
     */
    public String generateToken(Long userId, String email, String role, boolean rememberMe) {
        Date now = new Date();
        Date expiryDate = new Date(
                now.getTime() + (rememberMe ? rememberMeExpiration : jwtExpiration)
        );

        return Jwts.builder()
                .setSubject(email)
                .claim("userId", userId)
                .claim("email", email)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * Extract userId from token
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        Object userIdObj = claims.get("userId");

        if (userIdObj instanceof Integer) {
            return ((Integer) userIdObj).longValue();
        }
        if (userIdObj instanceof Long) {
            return (Long) userIdObj;
        }
        if (userIdObj != null) {
            return Long.parseLong(userIdObj.toString());
        }

        throw new RuntimeException("UserId not found in JWT token");
    }

    /**
     * Extract email from token
     */
    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Extract role from token
     */
    public String getRoleFromToken(String token) {
        return parseClaims(token).get("role", String.class);
    }

    /**
     * Validate JWT token
     */
    public boolean validateToken(String authToken) {
        try {
            parseClaims(authToken);
            return true;
        } catch (SecurityException ex) {
            log.error("Invalid JWT signature");
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token");
        } catch (ExpiredJwtException ex) {
            log.error("Expired JWT token");
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token");
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty");
        }
        return false;
    }

    /**
     * Parse JWT claims
     */
    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
