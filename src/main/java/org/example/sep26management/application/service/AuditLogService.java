package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.LogMessages;
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
            String userAgent) {
        try {
            AuditLogEntity auditLog = AuditLogEntity.builder()
                    .actionBy(userId)
                    .action(action)
                    .entityName(entityType != null ? entityType : "UNKNOWN")
                    .entityId(entityId != null ? entityId : 0L)
                    .oldData(null)
                    .newData(description != null ? "{\"description\":\"" + description + "\"}" : null)
                    .build();

            auditLogRepository.save(auditLog);
            log.debug(LogMessages.AUDIT_SAVED, action, description);
        } catch (Exception e) {
            log.error(LogMessages.AUDIT_SAVE_FAILED, e);
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
            String newValue) {
        try {
            AuditLogEntity auditLog = AuditLogEntity.builder()
                    .actionBy(userId)
                    .action(action)
                    .entityName(entityType != null ? entityType : "UNKNOWN")
                    .entityId(entityId != null ? entityId : 0L)
                    .oldData(oldValue != null ? "{\"value\":\"" + oldValue + "\"}" : null)
                    .newData(newValue != null ? "{\"value\":\"" + newValue + "\"}" : null)
                    .build();

            auditLogRepository.save(auditLog);
            log.debug(LogMessages.AUDIT_WITH_VALUES_SAVED, action, description);
        } catch (Exception e) {
            log.error(LogMessages.AUDIT_SAVE_WITH_VALUES_FAILED, e);
        }
    }

    @Async
    @Transactional
    public void logFailedLogin(String email, String reason, String ipAddress) {
        try {
            AuditLogEntity auditLog = AuditLogEntity.builder()
                    .action("LOGIN_FAILED")
                    .entityName("USER")
                    .entityId(0L) // No specific user ID for failed login
                    .actionBy(0L) // No user ID for failed login
                    .newData("{\"email\":\"" + email + "\",\"reason\":\"" + reason + "\",\"ipAddress\":\"" + ipAddress
                            + "\"}")
                    .build();

            auditLogRepository.save(auditLog);
            log.debug(LogMessages.AUDIT_FAILED_LOGIN_LOGGED, email);
        } catch (Exception e) {
            log.error(LogMessages.AUDIT_FAILED_LOGIN_LOG_ERROR, e);
        }
    }
}