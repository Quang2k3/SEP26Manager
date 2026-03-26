package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * NotificationService — push realtime WebSocket notification tới client.
 *
 * Dùng SimpMessagingTemplate (đã có sẵn từ spring-boot-starter-websocket).
 *
 * Topic conventions:
 *   /topic/notifications/{ROLE}   — broadcast tới tất cả user có role đó
 *   /user/queue/notifications      — gửi tới 1 user cụ thể (qua email/Principal)
 *
 * Payload format:
 * {
 *   "type": "grn_approved",          // khớp với NotificationType ở FE
 *   "referenceId": 42,               // id của entity liên quan
 *   "referenceCode": "GRN-240001",   // code hiển thị
 *   "subtitle": "Supplier ABC",
 *   "timestamp": "2025-09-26T..."
 * }
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    // ─── Broadcast tới toàn bộ user của một role ─────────────────────────────

    /**
     * Gửi notification tới tất cả user đang subscribe /topic/notifications/{role}.
     *
     * @param role           MANAGER | QC | KEEPER
     * @param type           khớp với NotificationType ở FE (vd: "grn_approved")
     * @param referenceId    id của entity liên quan
     * @param referenceCode  code hiển thị (vd: "GRN-240001")
     * @param subtitle       mô tả ngắn (tên supplier/customer/...)
     */
    public void notifyRole(String role, String type,
                           Long referenceId, String referenceCode, String subtitle) {
        Map<String, Object> payload = buildPayload(type, referenceId, referenceCode, subtitle);
        String destination = "/topic/notifications/" + role;
        try {
            messagingTemplate.convertAndSend(destination, payload);
            log.debug("WS notify → {} | type={} ref={}", destination, type, referenceCode);
        } catch (Exception e) {
            // Không để WS failure phá vỡ business transaction chính
            log.warn("WS notify failed (role={}, type={}): {}", role, type, e.getMessage());
        }
    }

    /**
     * Gửi notification tới nhiều role cùng lúc.
     */
    public void notifyRoles(String[] roles, String type,
                            Long referenceId, String referenceCode, String subtitle) {
        for (String role : roles) {
            notifyRole(role, type, referenceId, referenceCode, subtitle);
        }
    }

    /**
     * Gửi notification tới 1 user cụ thể qua email (Spring Security Principal name).
     */
    public void notifyUser(String userEmail, String type,
                           Long referenceId, String referenceCode, String subtitle) {
        Map<String, Object> payload = buildPayload(type, referenceId, referenceCode, subtitle);
        try {
            messagingTemplate.convertAndSendToUser(userEmail, "/queue/notifications", payload);
            log.debug("WS notify → user:{} | type={} ref={}", userEmail, type, referenceCode);
        } catch (Exception e) {
            log.warn("WS notify failed (user={}, type={}): {}", userEmail, type, e.getMessage());
        }
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private Map<String, Object> buildPayload(String type, Long referenceId,
                                             String referenceCode, String subtitle) {
        return Map.of(
                "type",          type,
                "referenceId",   referenceId != null ? referenceId : 0L,
                "referenceCode", referenceCode != null ? referenceCode : "",
                "subtitle",      subtitle != null ? subtitle : "",
                "timestamp",     Instant.now().toString()
        );
    }
}