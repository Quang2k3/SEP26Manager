package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.LogMessages;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Random;

/**
 * Service for OTP (One-Time Password) management using Redis
 * 
 * Features:
 * - Generate 6-digit OTP
 * - Store in Redis with TTL (Time To Live)
 * - Verify OTP
 * - Handle resend cooldown (2 minutes)
 * - Brute force protection (max 3 attempts, 15 min lockout)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final EmailService emailService;

    @Value("${otp.expiration-minutes}")
    private int otpExpirationMinutes;

    @Value("${otp.resend-cooldown-seconds}")
    private int resendCooldownSeconds;

    @Value("${otp.max-attempts}")
    private int maxAttempts;

    @Value("${otp.lockout-duration-minutes}")
    private int lockoutDurationMinutes;

    // Redis key prefixes
    private static final String OTP_PREFIX = "otp:";
    private static final String OTP_ATTEMPTS_PREFIX = "otp:attempts:";
    private static final String OTP_COOLDOWN_PREFIX = "otp:cooldown:";

    /**
     * Generate and send OTP to email
     * 
     * @param email User email
     * @throws BusinessException if in cooldown period or locked out
     */
    public void generateAndSendOtp(String email) {
        log.info(LogMessages.OTP_GENERATING, email);

        // Check if in cooldown period
        if (isInCooldown(email)) {
            long remainingSeconds = getRemainingCooldownSeconds(email);
            throw new BusinessException(
                    String.format(MessageConstants.OTP_COOLDOWN, remainingSeconds));
        }

        // Check if locked out
        if (isLockedOut(email)) {
            long remainingMinutes = getRemainingLockoutMinutes(email);
            throw new BusinessException(
                    String.format(MessageConstants.OTP_LOCKED, remainingMinutes));
        }

        // Generate 6-digit OTP
        String otp = generateOtp();
        log.debug(LogMessages.OTP_GENERATED, otp, email);

        // Store OTP in Redis with TTL
        String otpKey = OTP_PREFIX + email;
        redisTemplate.opsForValue().set(
                otpKey,
                otp,
                Duration.ofMinutes(otpExpirationMinutes));
        log.info(LogMessages.OTP_STORED_REDIS, otpExpirationMinutes);

        // Reset failed attempts counter
        resetAttempts(email);

        // Set cooldown period (2 minutes)
        setCooldown(email);

        // Send OTP via email
        emailService.sendOtpEmail(email, otp, "Email Verification");

        log.info(LogMessages.OTP_SENT_SUCCESS, email);
    }

    /**
     * Verify OTP entered by user
     * 
     * @param email    User email
     * @param inputOtp OTP entered by user
     * @return true if OTP is valid, false otherwise
     * @throws BusinessException if OTP expired, not found, or locked out
     */
    public boolean verifyOtp(String email, String inputOtp) {
        log.info(LogMessages.OTP_VERIFYING, email);

        // Check if locked out
        if (isLockedOut(email)) {
            long remainingMinutes = getRemainingLockoutMinutes(email);
            throw new BusinessException(
                    String.format(MessageConstants.OTP_LOCKED, remainingMinutes));
        }

        // Get OTP from Redis
        String otpKey = OTP_PREFIX + email;
        String storedOtp = (String) redisTemplate.opsForValue().get(otpKey);

        if (storedOtp == null) {
            log.warn(LogMessages.OTP_NOT_FOUND_OR_EXPIRED, email);
            throw new BusinessException(MessageConstants.OTP_EXPIRED);
        }

        // Check if OTP matches
        if (!storedOtp.equals(inputOtp)) {
            log.warn(LogMessages.OTP_INVALID_ATTEMPT, email);
            incrementAttempts(email);
            return false;
        }

        // OTP correct - cleanup all Redis keys
        log.info(LogMessages.OTP_VERIFIED_SUCCESS, email);
        redisTemplate.delete(otpKey);
        redisTemplate.delete(OTP_ATTEMPTS_PREFIX + email);
        redisTemplate.delete(OTP_COOLDOWN_PREFIX + email);

        return true;
    }

    // ==================== Helper Methods ====================

    /**
     * Generate random 6-digit OTP
     */
    private String generateOtp() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    /**
     * Check if email is in cooldown period (can't request new OTP yet)
     */
    private boolean isInCooldown(String email) {
        String key = OTP_COOLDOWN_PREFIX + email;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Get remaining cooldown seconds
     */
    private long getRemainingCooldownSeconds(String email) {
        String key = OTP_COOLDOWN_PREFIX + email;
        Long ttl = redisTemplate.getExpire(key);
        return ttl != null && ttl > 0 ? ttl : 0;
    }

    /**
     * Set cooldown period after sending OTP
     */
    private void setCooldown(String email) {
        String key = OTP_COOLDOWN_PREFIX + email;
        redisTemplate.opsForValue().set(
                key,
                "1",
                Duration.ofSeconds(resendCooldownSeconds));
        log.debug(LogMessages.OTP_COOLDOWN_SET, resendCooldownSeconds);
    }

    /**
     * Increment failed OTP attempts
     * If reaches max attempts, lock out the email
     */
    private void incrementAttempts(String email) {
        String key = OTP_ATTEMPTS_PREFIX + email;
        Long attempts = redisTemplate.opsForValue().increment(key);

        if (attempts != null) {
            log.debug(LogMessages.OTP_FAILED_ATTEMPTS, email, attempts, maxAttempts);

            if (attempts >= maxAttempts) {
                // Lock out for configured duration
                redisTemplate.expire(key, Duration.ofMinutes(lockoutDurationMinutes));
                log.warn(LogMessages.OTP_EMAIL_LOCKED_OUT, email, lockoutDurationMinutes, attempts);
            } else {
                // Set expiration to match OTP TTL
                redisTemplate.expire(key, Duration.ofMinutes(otpExpirationMinutes));
            }
        }
    }

    /**
     * Reset failed attempts counter
     */
    private void resetAttempts(String email) {
        String key = OTP_ATTEMPTS_PREFIX + email;
        redisTemplate.delete(key);
        log.debug(LogMessages.OTP_RESET_FAILED_ATTEMPTS, email);
    }

    /**
     * Check if email is locked out due to too many failed attempts
     */
    private boolean isLockedOut(String email) {
        String key = OTP_ATTEMPTS_PREFIX + email;
        Object attemptsObj = redisTemplate.opsForValue().get(key);

        if (attemptsObj == null) {
            return false;
        }

        int attempts = attemptsObj instanceof Integer ? (Integer) attemptsObj
                : Integer.parseInt(attemptsObj.toString());

        return attempts >= maxAttempts;
    }

    /**
     * Get remaining lockout minutes
     */
    private long getRemainingLockoutMinutes(String email) {
        String key = OTP_ATTEMPTS_PREFIX + email;
        Long ttl = redisTemplate.getExpire(key);
        return ttl != null && ttl > 0 ? (ttl / 60) : 0;
    }
}
