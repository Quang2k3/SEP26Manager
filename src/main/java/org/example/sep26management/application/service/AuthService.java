package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.request.*;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.LoginResponse;
import org.example.sep26management.domain.entity.User;
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

    @Value("${otp.expiration-minutes:3}")
    private int otpExpirationMinutes;

    @Value("${otp.max-attempts:5}")
    private int otpMaxAttempts;

    @Value("${otp.resend-limit-per-hour:5}")
    private int otpResendLimitPerHour;

    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {
        log.info("Login attempt for email: {}", request.getEmail());

        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("Login failed: User not found for email: {}", request.getEmail());
                    auditLogService.logFailedLogin(request.getEmail(), "User not found", ipAddress);
                    return new UnauthorizedException("Invalid account or password.");
                });

        if (!canLogin(user)) {
            auditLogService.logFailedLogin(user.getEmail(), "Account not eligible", ipAddress);
            throw new UnauthorizedException("Account is disabled.");
        }

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

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        user = userRepository.save(user);

        // Step 7: Generate JWT token using UserEntityMapper
        User domainUser = userEntityMapper.toDomain(user);
        String token = jwtTokenProvider.generateToken(
                domainUser,
                Boolean.TRUE.equals(request.getRememberMe()));

        long expiresIn = Boolean.TRUE.equals(request.getRememberMe())
                ? 7 * 24 * 60 * 60 * 1000L
                : 60 * 60 * 1000L;

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
     * UC-AUTH-02: Logout
     */
    public ApiResponse<Void> logout(Long userId, String ipAddress, String userAgent) {
        log.info("Logout for user ID: {}", userId);

        auditLogService.logAction(
                userId,
                "LOGOUT",
                "USER",
                userId,
                "User logged out",
                ipAddress,
                userAgent);

        log.info("Logout successful for user ID: {}", userId);
        return ApiResponse.success("Logged out successfully");
    }

    private LoginResponse handleFirstLogin(UserEntity user, String ipAddress, String userAgent) {
        String otpCode = generateOtp(user.getEmail(), OtpType.FIRST_LOGIN);
        emailService.sendOtpEmail(user.getEmail(), otpCode, "First Login Verification");

        auditLogService.logAction(
                user.getUserId(),
                "LOGIN_REQUIRES_VERIFICATION",
                "USER",
                user.getUserId(),
                "First login - OTP sent",
                ipAddress,
                userAgent,
                null,
                null
        );

        log.info("First login detected - OTP sent to: {}", user.getEmail());

        return LoginResponse.builder()
                .requiresVerification(true)
                .user(LoginResponse.UserInfoDTO.builder()
                        .userId(user.getUserId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .role(user.getRole())
                        .build())
                .build();
    }

    private void handleFailedLogin(UserEntity user, String ipAddress) {
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);

        if (user.getFailedLoginAttempts() >= 5) {
            user.setStatus(UserStatus.LOCKED);
            user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
            userRepository.save(user);
            auditLogService.logFailedLogin(user.getEmail(), "Account locked due to failed attempts", ipAddress);
            throw new UnauthorizedException("Account is disabled.");
        }

        userRepository.save(user);
        auditLogService.logFailedLogin(user.getEmail(), "Invalid password", ipAddress);
    }

    private boolean canLogin(UserEntity user) {
        if (user.getStatus() == UserStatus.INACTIVE || user.getStatus() == UserStatus.LOCKED) {
            return false;
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            return false;
        }

        if (Boolean.FALSE.equals(user.getIsPermanent()) &&
                user.getExpireDate() != null &&
                user.getExpireDate().isBefore(java.time.LocalDate.now())) {
            return false;
        }

        return true;
    }

    // Temporarily disabled - otps table not available
    /*
     * private String generateOtp(String email, OtpType otpType) {
     * // Generate 6-digit OTP
     * Random random = new Random();
     * String otpCode = String.format("%06d", random.nextInt(1000000));
     * 
     * // Save to database
     * OtpEntity otp = OtpEntity.builder()
     * .email(email)
     * .otpCode(otpCode)
     * .otpType(otpType)
     * .attemptsRemaining(otpMaxAttempts)
     * .isUsed(false)
     * .expiresAt(LocalDateTime.now().plusMinutes(otpExpirationMinutes))
     * .build();
     * 
     * otpRepository.save(otp);
     * 
     * return otpCode;
     * }
     */
}