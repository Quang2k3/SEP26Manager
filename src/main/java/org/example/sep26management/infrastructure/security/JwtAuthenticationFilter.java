package org.example.sep26management.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt)) {
                try {
                    if (jwtTokenProvider.validateToken(jwt)) {
                        String email = jwtTokenProvider.getEmailFromToken(jwt);
                        Long userId = jwtTokenProvider.getUserIdFromToken(jwt);
                        String role = jwtTokenProvider.getRoleFromToken(jwt);

                        log.debug("JWT Valid - Email: {}, UserId: {}, Role: {}", email, userId, role);

                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        email,
                                        null,
                                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
                                );

                        Map<String, Object> details = new HashMap<>();
                        details.put("userId", userId);
                        details.put("email", email);
                        details.put("role", role);
                        authentication.setDetails(details);

                        SecurityContextHolder.getContext().setAuthentication(authentication);

                        log.debug("Authentication set for user: {} (ID: {})", email, userId);
                    }
                } catch (Exception e) {
                    log.warn("Invalid JWT token: {}", e.getMessage());
                }
            }
        } catch (Exception ex) {
            log.error("Error in JWT filter: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        try {
            String bearerToken = request.getHeader("Authorization");

            if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
                return bearerToken.substring(7);
            }
        } catch (Exception e) {
            log.warn("Error extracting JWT: {}", e.getMessage());
        }

        return null;
    }
}