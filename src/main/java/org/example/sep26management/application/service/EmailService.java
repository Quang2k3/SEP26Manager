package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
            // Log stacktrace để thấy nguyên nhân thật (đừng chỉ e.getMessage)
            log.error(LogMessages.EMAIL_OTP_SEND_FAILED, to, e);
            // Bạn có thể giữ log OTP để test, nhưng hiểu là FAIL
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
            log.warn(LogMessages.EMAIL_TEMP_PASSWORD_FOR_TESTING, tempPassword); // For testing
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

            // Dynamic subject based on new status
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

                Login URL: http://localhost:3000/login

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
        return String.format(
                """
                        ════════════════════════════════════════════════════════════════
                                    WAREHOUSE MANAGEMENT SYSTEM
                                      Role Assignment Notification
                        ════════════════════════════════════════════════════════════════

                        Dear User,

                        We are writing to inform you that your role has been updated in the
                        Warehouse Management System.

                        ┌────────────────────────────────────────────────────────────────┐
                        │ ROLE CHANGE DETAILS                                            │
                        ├────────────────────────────────────────────────────────────────┤
                        │                                                                │
                        │  Previous Role:  %s
                        │  New Role:       %s
                        │  Changed By:     %s
                        │                                                                │
                        └────────────────────────────────────────────────────────────────┘

                        ⚠️  IMPORTANT NOTICE:
                        This change is effective immediately. Your access permissions and
                        security clearances have been updated according to your new role.

                        📋 NEXT STEPS:
                        • Please log out and log back in to ensure all permissions are
                          properly applied
                        • Review your new role responsibilities in the system documentation
                        • Contact your manager if you have any questions about this change

                        ────────────────────────────────────────────────────────────────

                        If you believe this role change was made in error, please contact
                        your manager or system administrator immediately.

                        Best regards,
                        Warehouse Management Team

                        ════════════════════════════════════════════════════════════════
                        This is an automated notification. Please do not reply to this email.
                        ════════════════════════════════════════════════════════════════
                        """,
                oldRole, newRole, changedBy);
    }

    private String buildStatusChangeEmailBody(String oldStatus, String newStatus, LocalDate suspendUntil,
            String reason, String changedBy) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        if ("INACTIVE".equals(newStatus)) {
            String suspensionType = suspendUntil != null ? "Temporary"
                    : "Permanent (until reactivated by administrator)";
            String suspendUntilText = suspendUntil != null ? suspendUntil.format(formatter) : "N/A";
            String reasonText = reason != null && !reason.isEmpty() ? reason : "Not specified";
            return String.format("""
                    ════════════════════════════════════════════════════════════════
                                WAREHOUSE MANAGEMENT SYSTEM
                                Account Deactivation Notice
                    ════════════════════════════════════════════════════════════════

                    Dear User,

                    We are writing to inform you that your account has been deactivated
                    in the Warehouse Management System.

                    ┌────────────────────────────────────────────────────────────────┐
                    │ STATUS CHANGE DETAILS                                          │
                    ├────────────────────────────────────────────────────────────────┤
                    │                                                                │
                    │  Previous Status:    %s
                    │  New Status:         %s
                    │  Suspension Type:    %s
                    │  Suspended Until:    %s
                    │  Changed By:         %s
                    │  Reason:             %s
                    │                                                                │
                    └────────────────────────────────────────────────────────────────┘

                    ⚠️  IMPORTANT NOTICE:
                    Your account has been deactivated and you will NOT be able to access
                    the Warehouse Management System until your account is reactivated.

                    All active sessions will be terminated and login attempts will be
                    blocked during the suspension period.

                    ────────────────────────────────────────────────────────────────

                    If you believe this deactivation was made in error or have questions
                    about this change, please contact your manager or system administrator
                    immediately.

                    Best regards,
                    Warehouse Management Team

                    ════════════════════════════════════════════════════════════════
                    This is an automated notification. Please do not reply to this email.
                    ════════════════════════════════════════════════════════════════
                    """, oldStatus, newStatus, suspensionType, suspendUntilText, changedBy, reasonText);
        } else {
            return String.format("""
                    ════════════════════════════════════════════════════════════════
                                WAREHOUSE MANAGEMENT SYSTEM
                                Account Reactivation Notice
                    ════════════════════════════════════════════════════════════════

                    Dear User,

                    Good news! Your account has been reactivated in the Warehouse
                    Management System.

                    ┌────────────────────────────────────────────────────────────────┐
                    │ STATUS CHANGE DETAILS                                          │
                    ├────────────────────────────────────────────────────────────────┤
                    │                                                                │
                    │  Previous Status:    %s
                    │  New Status:         %s
                    │  Changed By:         %s
                    │                                                                │
                    └────────────────────────────────────────────────────────────────┘

                    ✅ ACCOUNT ACCESS RESTORED:
                    You can now access the Warehouse Management System using your
                    existing credentials. All system features are available according
                    to your assigned role and permissions.

                    📋 NEXT STEPS:
                    • Log in to the system with your email and password
                    • Review any updates or changes made during the suspension period
                    • Contact your manager if you have any questions

                    ────────────────────────────────────────────────────────────────

                    Welcome back to the Warehouse Management System!

                    Best regards,
                    Warehouse Management Team

                    ════════════════════════════════════════════════════════════════
                    This is an automated notification. Please do not reply to this email.
                    ════════════════════════════════════════════════════════════════
                    """, oldStatus, newStatus, changedBy);
        }
    }
}