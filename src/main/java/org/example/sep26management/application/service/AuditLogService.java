package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.infrastructure.persistence.entity.AuditLogEntity;
import org.example.sep26management.infrastructure.persistence.repository.AuditLogJpaRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogJpaRepository auditLogRepository;

    @Async
    @Transactional
    public void logAction(
            Long userId,
            String action,
            String entityType,
            Long entityId,
            String description,
            String ipAddress,
            String userAgent
    ) {
        try {
            AuditLogEntity auditLog = AuditLogEntity.builder()
                    .userId(userId)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .description(description)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .build();

            auditLogRepository.save(auditLog);
            log.debug("Audit log saved: {} - {}", action, description);
        } catch (Exception e) {
            log.error("Failed to save audit log", e);
        }
    }

    @Async
    @Transactional
    public void logAction(
            Long userId,
            String action,
            String entityType,
            Long entityId,
            String description,
            String ipAddress,
            String userAgent,
            String oldValue,
            String newValue
    ) {
        try {
            AuditLogEntity auditLog = AuditLogEntity.builder()
                    .userId(userId)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .description(description)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .build();

            auditLogRepository.save(auditLog);
            log.debug("Audit log with values saved: {} - {}", action, description);
        } catch (Exception e) {
            log.error("Failed to save audit log with values", e);
        }
    }

    @Async
    @Transactional
    public void logFailedLogin(String email, String reason, String ipAddress) {
        try {
            AuditLogEntity auditLog = AuditLogEntity.builder()
                    .action("LOGIN_FAILED")
                    .entityType("USER")
                    .description("Failed login attempt for: " + email + ". Reason: " + reason)
                    .ipAddress(ipAddress)
                    .build();

            auditLogRepository.save(auditLog);
            log.debug("Failed login logged: {}", email);
        } catch (Exception e) {
            log.error("Failed to log failed login", e);
        }
    }
}