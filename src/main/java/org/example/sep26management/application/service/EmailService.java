package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

import org.example.sep26management.application.constants.LogMessages;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    // ─── Generic simple email (dùng bởi ScannerOtpService) ───────────────────

    @Async
    public void sendSimpleEmail(String toEmail, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("[Email] Sent '{}' → {}", subject, toEmail);
        } catch (Exception e) {
            log.error("[Email] Failed to send '{}' → {}: {}", subject, toEmail, e.getMessage());
        }
    }

    // ─── OTP (login / password reset) ─────────────────────────────────────────

    @Async
    public CompletableFuture<Boolean> sendOtpEmail(String toEmail, String otpCode, String purpose) {
        String from = (fromEmail == null) ? "" : fromEmail.trim();
        String to = (toEmail == null) ? "" : toEmail.trim();

        if (from.isEmpty()) {
            log.error(LogMessages.EMAIL_FROM_EMPTY, fromEmail);
            return CompletableFuture.completedFuture(false);
        }
        if (to.isEmpty()) {
            log.error(LogMessages.EMAIL_TO_EMPTY);
            return CompletableFuture.completedFuture(false);
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject("Warehouse Management - " + purpose);
            message.setText(buildOtpEmailBody(otpCode, purpose));

            mailSender.send(message);
            log.info(LogMessages.EMAIL_OTP_SENT_SUCCESS, to);
            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            log.error(LogMessages.EMAIL_OTP_SEND_FAILED, to, e);
            log.warn(LogMessages.EMAIL_OTP_CODE_FOR_TESTING, otpCode);
            return CompletableFuture.completedFuture(false);
        }
    }

    @Async
    public void sendWelcomeEmail(String toEmail, String tempPassword, String role) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Welcome to Warehouse Management System");
            message.setText(buildWelcomeEmailBody(toEmail, tempPassword, role));

            mailSender.send(message);
            log.info(LogMessages.EMAIL_WELCOME_SENT_SUCCESS, toEmail);
        } catch (Exception e) {
            log.error(LogMessages.EMAIL_WELCOME_SEND_FAILED, toEmail, e.getMessage());
            log.warn(LogMessages.EMAIL_TEMP_PASSWORD_FOR_TESTING, tempPassword);
        }
    }

    @Async
    public void sendStatusChangeEmail(String toEmail, String statusText) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Account Status Update");
            message.setText(buildStatusChangeEmailBody(statusText));

            mailSender.send(message);
            log.info(LogMessages.EMAIL_STATUS_CHANGE_SENT_SUCCESS, toEmail);
        } catch (Exception e) {
            log.error(LogMessages.EMAIL_STATUS_CHANGE_SEND_FAILED, toEmail, e.getMessage());
        }
    }

    @Async
    public void sendRoleChangeEmail(String toEmail, String oldRole, String newRole, String changedBy) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Role Change Notification");
            message.setText(buildRoleChangeEmailBody(oldRole, newRole, changedBy));

            mailSender.send(message);
            log.info(LogMessages.EMAIL_ROLE_CHANGE_SENT_SUCCESS, toEmail);
        } catch (Exception e) {
            log.error(LogMessages.EMAIL_ROLE_CHANGE_SEND_FAILED, toEmail, e.getMessage());
        }
    }

    @Async
    public void sendStatusChangeEmail(String toEmail, String oldStatus, String newStatus,
            LocalDate suspendUntil, String reason, String changedBy) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);

            if ("INACTIVE".equals(newStatus)) {
                message.setSubject("Account Deactivation Notice");
            } else {
                message.setSubject("Account Reactivation Notice");
            }

            message.setText(buildStatusChangeEmailBody(oldStatus, newStatus, suspendUntil, reason, changedBy));

            mailSender.send(message);
            log.info(LogMessages.EMAIL_STATUS_CHANGE_SENT_SUCCESS, toEmail);
        } catch (Exception e) {
            log.error(LogMessages.EMAIL_STATUS_CHANGE_SEND_FAILED, toEmail, e.getMessage());
        }
    }

    // ─── Email body builders ──────────────────────────────────────────────────

    private String buildOtpEmailBody(String otpCode, String purpose) {
        return String.format("""
                Dear User,

                Your OTP code for %s is:

                %s

                This code will expire in 3 minutes.

                If you did not request this code, please ignore this email.

                Best regards,
                Warehouse Management Team
                """, purpose, otpCode);
    }

    private String buildWelcomeEmailBody(String email, String tempPassword, String role) {
        return String.format("""
                Dear New User,

                Welcome to Warehouse Management System!

                Your account has been created successfully.

                Login Credentials:
                - Email: %s
                - Temporary Password: %s
                - Role: %s

                Please login and change your password on first login.

                Best regards,
                Warehouse Management Team
                """, email, tempPassword, role);
    }

    private String buildStatusChangeEmailBody(String statusText) {
        return String.format("""
                Dear User,

                Your account has been %s.

                If you have any questions, please contact the administrator.

                Best regards,
                Warehouse Management Team
                """, statusText);
    }

    private String buildRoleChangeEmailBody(String oldRole, String newRole, String changedBy) {
        return String.format("""
                Dear User,

                Your role in the Warehouse Management System has been updated.

                Previous Role : %s
                New Role      : %s
                Changed By    : %s

                Please log out and log back in to apply the new permissions.

                Best regards,
                Warehouse Management Team
                """, oldRole, newRole, changedBy);
    }

    private String buildStatusChangeEmailBody(String oldStatus, String newStatus, LocalDate suspendUntil,
            String reason, String changedBy) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        if ("INACTIVE".equals(newStatus)) {
            String suspendUntilText = suspendUntil != null ? suspendUntil.format(formatter) : "N/A";
            String reasonText = (reason != null && !reason.isEmpty()) ? reason : "Not specified";
            return String.format("""
                    Dear User,

                    Your account has been deactivated.

                    Previous Status  : %s
                    New Status       : %s
                    Suspended Until  : %s
                    Reason           : %s
                    Changed By       : %s

                    Contact your manager if you believe this was an error.

                    Best regards,
                    Warehouse Management Team
                    """, oldStatus, newStatus, suspendUntilText, reasonText, changedBy);
        } else {
            return String.format("""
                    Dear User,

                    Your account has been reactivated.

                    Previous Status : %s
                    New Status      : %s
                    Changed By      : %s

                    You may now log in to the system.

                    Best regards,
                    Warehouse Management Team
                    """, oldStatus, newStatus, changedBy);
        }
    }
}