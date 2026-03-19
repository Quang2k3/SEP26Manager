package org.example.sep26management.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.infrastructure.security.JwtTokenProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Client subscribes to /topic/... and /user/queue/...
        registry.enableSimpleBroker("/topic", "/user");
        // Client sends to /app/...
        registry.setApplicationDestinationPrefixes("/app");
        // For user-specific messages
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns("*")
                .withSockJS()
                .setInterceptors(new HttpSessionHandshakeInterceptor());
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) return message;

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // ── Authenticate on CONNECT, store Principal in session ──────────
                    String token = accessor.getFirstNativeHeader("Authorization");
                    if (token != null && token.startsWith("Bearer ")) {
                        token = token.substring(7);
                    }
                    if (token == null) {
                        token = accessor.getFirstNativeHeader("token");
                    }
                    if (token != null && jwtTokenProvider.validateToken(token)) {
                        try {
                            Long userId = jwtTokenProvider.getUserIdFromToken(token);
                            String email = jwtTokenProvider.getEmailFromToken(token);
                            List<String> roles = jwtTokenProvider.getRoleCodesFromToken(token)
                                    .stream().collect(Collectors.toList());
                            var authorities = roles.stream()
                                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                                    .collect(Collectors.toList());
                            var auth = new UsernamePasswordAuthenticationToken(email, null, authorities);
                            auth.setDetails(Map.of("userId", userId, "token", token));
                            accessor.setUser(auth);
                            // Persist Principal so subsequent SEND/SUBSCRIBE frames also carry it
                            accessor.setLeaveMutable(true);
                            log.debug("WS CONNECT authenticated: userId={}", userId);
                        } catch (Exception e) {
                            log.warn("WS CONNECT auth failed: {}", e.getMessage());
                        }
                    }
                } else if (accessor.getCommand() != null) {
                    // ── For SEND/SUBSCRIBE/etc: ensure Principal is propagated ───────
                    // Spring's SimpAnnotationMethod injects Principal from the session;
                    // but if accessor lost it, attempt token re-auth as fallback.
                    if (accessor.getUser() == null) {
                        String token = accessor.getFirstNativeHeader("Authorization");
                        if (token != null && token.startsWith("Bearer ")) {
                            token = token.substring(7);
                        }
                        if (token != null && jwtTokenProvider.validateToken(token)) {
                            try {
                                Long userId = jwtTokenProvider.getUserIdFromToken(token);
                                String email = jwtTokenProvider.getEmailFromToken(token);
                                List<String> roles = jwtTokenProvider.getRoleCodesFromToken(token)
                                        .stream().collect(Collectors.toList());
                                var authorities = roles.stream()
                                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                                        .collect(Collectors.toList());
                                var auth = new UsernamePasswordAuthenticationToken(email, null, authorities);
                                auth.setDetails(Map.of("userId", userId, "token", token));
                                accessor.setUser(auth);
                            } catch (Exception e) {
                                log.warn("WS re-auth failed on {}: {}", accessor.getCommand(), e.getMessage());
                            }
                        }
                    }
                }
                return message;
            }
        });
    }
}