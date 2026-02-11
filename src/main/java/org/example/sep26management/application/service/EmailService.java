package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

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
            log.error("spring.mail.username is empty. Check mail config/env. fromEmail='{}'", fromEmail);
            return CompletableFuture.completedFuture(false);
        }
        if (to.isEmpty()) {
            log.error("toEmail is empty");
            return CompletableFuture.completedFuture(false);
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject("Warehouse Management - " + purpose);
            message.setText(buildOtpEmailBody(otpCode, purpose));

            mailSender.send(message);
            log.info("OTP email sent successfully to: {}", to);
            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            // Log stacktrace để thấy nguyên nhân thật (đừng chỉ e.getMessage)
            log.error("Failed to send OTP email to: {}", to, e);
            // Bạn có thể giữ log OTP để test, nhưng hiểu là FAIL
            log.warn("OTP code for testing: {}", otpCode);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Send OTP email for email verification (simpler overload)
     * Uses HTML template for better presentation
     * 
     * @param toEmail Recipient email address
     * @param otpCode 6-digit OTP code
     */
    // @Async
    // public void sendOtpEmail(String toEmail, String otpCode) {
    // try {
    // SimpleMailMessage message = new SimpleMailMessage();
    // message.setFrom(fromEmail);
    // message.setTo(toEmail);
    // message.setSubject("Email Verification - Your OTP Code");
    // message.setText(buildEmailVerificationOtpBody(otpCode));

    // mailSender.send(message);
    // log.info("Email verification OTP sent successfully to: {}", toEmail);
    // } catch (Exception e) {
    // log.error("Failed to send email verification OTP to: {} - Error: {}",
    // toEmail, e.getMessage());
    // log.warn("OTP code for testing: {}", otpCode); // For testing without real
    // email
    // }
    // }

    @Async
    public void sendWelcomeEmail(String toEmail, String tempPassword, String role) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Welcome to Warehouse Management System");
            message.setText(buildWelcomeEmailBody(toEmail, tempPassword, role));

            mailSender.send(message);
            log.info("Welcome email sent successfully to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send welcome email to: {} - Error: {}", toEmail, e.getMessage());
            log.warn("Temp password for testing: {}", tempPassword); // For testing
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
            log.info("Status change email sent successfully to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send status change email to: {} - Error: {}", toEmail, e.getMessage());
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

    /**
     * Build email body for email verification OTP
     * Enhanced formatting for better readability
     */
    private String buildEmailVerificationOtpBody(String otpCode) {
        return String.format("""
                ╔══════════════════════════════════════════╗
                ║   SEP26 WAREHOUSE MANAGEMENT SYSTEM      ║
                ║         Email Verification               ║
                ╚══════════════════════════════════════════╝

                Dear User,

                Thank you for registering with SEP26 Warehouse Management System.

                Your email verification code is:

                ┌─────────────────┐
                │   %s   │
                └─────────────────┘

                ⏱️  This code will expire in 5 minutes.
                🔒  For security, do not share this code with anyone.

                ℹ️  If you didn't request this code, please ignore this email.

                Best regards,
                SEP26 Warehouse Management Team

                ─────────────────────────────────────────
                This is an automated message, please do not reply.
                """, otpCode);
    }
}