package org.example.sep26management.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.LogMessages;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {

                // ── SCANNER token path (iPhone scan events) ──────────────────
                if (jwtTokenProvider.isScanToken(jwt)) {
                    String sessionId = jwtTokenProvider.getSessionIdFromScanToken(jwt);

                    List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_SCANNER"));

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            "scanner:" + sessionId, null, authorities);

                    Map<String, Object> details = new HashMap<>();
                    details.put("sessionId", sessionId);
                    authentication.setDetails(details);

                    SecurityContextHolder.getContext().setAuthentication(authentication);

                } else {
                    // ── Normal user token path ────────────────────────────────
                    String email = jwtTokenProvider.getEmailFromToken(jwt);
                    Long userId = jwtTokenProvider.getUserIdFromToken(jwt);
                    Set<String> roleCodes = jwtTokenProvider.getRoleCodesFromToken(jwt);

                    List<SimpleGrantedAuthority> authorities = roleCodes.stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                            .collect(Collectors.toList());

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(email,
                            null, authorities);

                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    Map<String, Object> details = new HashMap<>();
                    details.put("userId", userId);
                    details.put("email", email);
                    details.put("roles", roleCodes);
                    authentication.setDetails(details);

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception ex) {
            log.error(LogMessages.JWT_AUTH_SET_FAILED, ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}