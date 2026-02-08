package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.request.*;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.LoginResponse;
import org.example.sep26management.domain.entity.User;
import org.example.sep26management.domain.enums.OtpType;
import org.example.sep26management.domain.enums.UserStatus;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.example.sep26management.infrastructure.exception.UnauthorizedException;
// Temporarily disabled - otps table not available
// import org.example.sep26management.infrastructure.persistence.entity.OtpEntity;
import org.example.sep26management.infrastructure.persistence.entity.UserEntity;
// import org.example.sep26management.infrastructure.persistence.repository.OtpJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.UserJpaRepository;
import org.example.sep26management.infrastructure.security.JwtTokenProvider;
import org.example.sep26management.infrastructure.mapper.UserEntityMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthService {

    private final UserJpaRepository userRepository;
    // Temporarily disabled - otps table not available in database
    // Uncomment when otps table is created
    // private final OtpJpaRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final UserEntityMapper userEntityMapper; // Add mapper injection

    @Value("${otp.expiration-minutes}")
    private int otpExpirationMinutes;

    @Value("${otp.max-attempts}")
    private int otpMaxAttempts;

    @Value("${otp.resend-limit-per-hour}")
    private int otpResendLimitPerHour;

    /**
     * UC-AUTH-01: Login with JWT
     */
    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {
        log.info("Login attempt for email: {}", request.getEmail());

        // Step 4: Verify credentials
        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("Login failed: User not found for email: {}", request.getEmail());
                    auditLogService.logFailedLogin(request.getEmail(), "User not found", ipAddress);
                    return new UnauthorizedException("Invalid account or password.");
                });

        // Step 5: Check account eligibility (BR-AUTH-01)
        if (!canLogin(user)) {
            auditLogService.logFailedLogin(user.getEmail(), "Account not eligible", ipAddress);
            throw new UnauthorizedException("Account is disabled.");
        }

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Login failed: Invalid password for email: {}, failed attempts: {}", 
                    user.getEmail(), user.getFailedLoginAttempts() + 1);
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);

            // Lock after 5 failed attempts
            if (user.getFailedLoginAttempts() >= 5) {
                user.setStatus(UserStatus.LOCKED);
                user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
                userRepository.save(user);
                auditLogService.logFailedLogin(user.getEmail(), "Account locked due to failed attempts", ipAddress);
                throw new UnauthorizedException("Account is disabled.");
            }

            userRepository.save(user);
            auditLogService.logFailedLogin(user.getEmail(), "Invalid password", ipAddress);
            throw new UnauthorizedException("Invalid account or password.");
        }

        // Reset failed attempts on successful password verification
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // Step 7: Generate JWT token using UserEntityMapper
        User domainUser = userEntityMapper.toDomain(user);
        String token = jwtTokenProvider.generateToken(
                domainUser,
                Boolean.TRUE.equals(request.getRememberMe()));

        // Calculate expiration
        long expiresIn = Boolean.TRUE.equals(request.getRememberMe())
                ? 7 * 24 * 60 * 60 * 1000L // 7 days
                : 5 * 60 * 1000L; // 5 minutes

        auditLogService.logAction(
                user.getUserId(),
                "LOGIN",
                "USER",
                user.getUserId(),
                "Successful login",
                ipAddress,
                userAgent);

        // Step 8: Return success response
        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .requiresVerification(false)
                .user(LoginResponse.UserInfoDTO.builder()
                        .userId(user.getUserId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .roleCodes(domainUser.getRoleCodes()) // Use from domain model
                        .avatarUrl(user.getAvatarUrl())
                        .build())
                .build();
    }

    /**
     * UC-PERS-01: Forgot Password - Send OTP
     * TEMPORARILY DISABLED - otps table not available in database
     */
    public ApiResponse<Void> sendResetPasswordOtp(ForgotPasswordRequest request) {
        log.warn("Password reset OTP requested but OTP functionality is disabled (otps table not available)");
        throw new BusinessException("Password reset via OTP is temporarily disabled. Please contact administrator.");
        
        /* Original implementation - re-enable when otps table is available
        log.info("Password reset requested for email: {}", request.getEmail());

        // Check rate limiting (BR-AUTH-15: Privacy & Obfuscation)
        // Always send OTP to valid email format, regardless of account existence
        long recentOtpCount = otpRepository.countRecentOtps(
                request.getEmail(),
                OtpType.RESET_PASSWORD,
                LocalDateTime.now().minusHours(1));

        if (recentOtpCount >= otpResendLimitPerHour) {
            throw new BusinessException("OTP email quota exceeded in the current time window.");
        }

        // Generate and send OTP
        String otpCode = generateOtp(request.getEmail(), OtpType.RESET_PASSWORD);
        emailService.sendOtpEmail(request.getEmail(), otpCode, "Password Reset");

        return ApiResponse.success("The OTP has been sent to your email.");
        */
    }

    /**
     * Verify Reset Password OTP
     * TEMPORARILY DISABLED - otps table not available in database
     */
    public ApiResponse<Void> verifyResetPasswordOtp(
            String email,
            VerifyOtpRequest request) {
        log.warn("Password reset OTP verification requested but OTP functionality is disabled");
        throw new BusinessException("Password reset via OTP is temporarily disabled. Please contact administrator.");
        
        /* Original implementation - re-enable when otps table is available
        log.info("Verifying reset password OTP for email: {}", email);

        // Find valid OTP
        OtpEntity otp = otpRepository.findValidOtp(email, OtpType.RESET_PASSWORD, LocalDateTime.now())
                .orElseThrow(() -> new BusinessException("Your OTP has expired. Please request a new one."));

        // Check validity
        if (otp.getIsUsed()) {
            throw new BusinessException("This OTP has already been used.");
        }

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Your OTP has expired. Please request a new one.");
        }

        if (otp.getAttemptsRemaining() <= 0) {
            throw new BusinessException(
                    "Your OTP has expired due to too many incorrect attempts. Please request a new OTP.");
        }

        // Verify OTP code
        if (!otp.getOtpCode().equals(request.getOtpCode())) {
            otp.setAttemptsRemaining(otp.getAttemptsRemaining() - 1);
            otpRepository.save(otp);

            if (otp.getAttemptsRemaining() <= 0) {
                throw new BusinessException(
                        "Your OTP has expired due to too many incorrect attempts. Please request a new OTP.");
            }

            throw new BusinessException(
                    String.format("Incorrect OTP code. You have %d attempts remaining.",
                            otp.getAttemptsRemaining()));
        }

        // Check if account exists (Step 7a)
        Optional<UserEntity> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            throw new BusinessException("Account does not exist");
        }

        // Mark OTP as verified (but not used yet)
        otp.setVerifiedAt(LocalDateTime.now());
        otpRepository.save(otp);

        return ApiResponse.success("The OTP has been verified successfully.");
        */
    }

    /**
     * Reset Password after OTP verification
     * TEMPORARILY DISABLED - otps table not available in database
     */
    public ApiResponse<Void> resetPassword(
            String email,
            ResetPasswordRequest request) {
        log.warn("Password reset requested but OTP functionality is disabled");
        throw new BusinessException("Password reset via OTP is temporarily disabled. Please contact administrator.");
        
        /* Original implementation - re-enable when otps table is available
        log.info("Resetting password for email: {}", email);

        // Validate password match
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("The confirmed password does not match.");
        }

        // Find user
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Account does not exist"));

        // Find verified OTP
        OtpEntity otp = otpRepository.findValidOtp(email, OtpType.RESET_PASSWORD, LocalDateTime.now())
                .orElseThrow(() -> new BusinessException("OTP verification required"));

        if (otp.getVerifiedAt() == null) {
            throw new BusinessException("OTP not verified");
        }

        if (otp.getIsUsed()) {
            throw new BusinessException("This reset link has expired");
        }

        // Update password
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);

        // Mark OTP as used
        otp.setIsUsed(true);
        otpRepository.save(otp);

        auditLogService.logAction(
                user.getUserId(),
                "PASSWORD_RESET",
                "USER",
                user.getUserId(),
                "Password reset successful",
                null,
                null);

        return ApiResponse.success("The password has been reset successfully. Please login again.");
        */
    }

    /**
     * Resend OTP
     * TEMPORARILY DISABLED - otps table not available in database
     */
    public ApiResponse<Void> resendOtp(String email, OtpType otpType) {
        log.warn("Resend OTP requested but OTP functionality is disabled");
        throw new BusinessException("OTP functionality is temporarily disabled. Please contact administrator.");
        
        /* Original implementation - re-enable when otps table is available
        log.info("Resending OTP for email: {} type: {}", email, otpType);

        // Check rate limiting
        long recentOtpCount = otpRepository.countRecentOtps(
                email,
                otpType,
                LocalDateTime.now().minusHours(1));

        if (recentOtpCount >= otpResendLimitPerHour) {
            throw new BusinessException("OTP email quota exceeded in the current time window.");
        }

        // Check if previous OTP is expired
        Optional<OtpEntity> existingOtp = otpRepository.findValidOtp(email, otpType, LocalDateTime.now());
        if (existingOtp.isPresent() && !existingOtp.get().getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Current OTP is still valid. Please wait until it expires.");
        }

        // Generate new OTP
        String otpCode = generateOtp(email, otpType);
        String subject = "Password Reset";
        emailService.sendOtpEmail(email, otpCode, subject);

        return ApiResponse.success("The OTP has been sent to your email.");
        */
    }

    /**
     * UC-AUTH-02: Logout
     */
    public ApiResponse<Void> logout(Long userId, String ipAddress, String userAgent) {
        log.info("Logout for user ID: {}", userId);

        // Invalidate session (can be implemented with Redis for token blacklist)
        // For now, just log the action

        auditLogService.logAction(
                userId,
                "LOGOUT",
                "USER",
                userId,
                "User logged out",
                ipAddress,
                userAgent);

        return ApiResponse.success("Logged out successfully");
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    private boolean canLogin(UserEntity user) {
        // Check status
        if (user.getStatus() == UserStatus.INACTIVE || user.getStatus() == UserStatus.LOCKED) {
            return false;
        }

        // Check if locked temporarily
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            return false;
        }

        // Check account expiration
        if (Boolean.FALSE.equals(user.getIsPermanent()) &&
                user.getExpireDate() != null &&
                user.getExpireDate().isBefore(java.time.LocalDate.now())) {
            return false;
        }

        return true;
    }

    // Temporarily disabled - otps table not available
    /*
    private String generateOtp(String email, OtpType otpType) {
        // Generate 6-digit OTP
        Random random = new Random();
        String otpCode = String.format("%06d", random.nextInt(1000000));

        // Save to database
        OtpEntity otp = OtpEntity.builder()
                .email(email)
                .otpCode(otpCode)
                .otpType(otpType)
                .attemptsRemaining(otpMaxAttempts)
                .isUsed(false)
                .expiresAt(LocalDateTime.now().plusMinutes(otpExpirationMinutes))
                .build();

        otpRepository.save(otp);

        return otpCode;
    }
    */
}