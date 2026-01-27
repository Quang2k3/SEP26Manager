package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public void sendOtpEmail(String toEmail, String otpCode, String purpose) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Warehouse Management - " + purpose);
            message.setText(buildOtpEmailBody(otpCode, purpose));

            mailSender.send(message);
            log.info("OTP email sent successfully to: {}", toEmail);
        } catch (Exception e) {
            // Chỉ log error, KHÔNG throw exception
            log.error("Failed to send OTP email to: {} - Error: {}", toEmail, e.getMessage());
            log.warn("OTP code for testing: {}", otpCode); // For testing without real email
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
}