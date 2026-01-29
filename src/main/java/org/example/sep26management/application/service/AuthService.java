package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.request.*;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.LoginResponse;
import org.example.sep26management.application.mapper.UserMapper;
import org.example.sep26management.domain.enums.OtpType;
import org.example.sep26management.domain.enums.UserStatus;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.example.sep26management.infrastructure.exception.UnauthorizedException;
import org.example.sep26management.infrastructure.persistence.entity.OtpEntity;
import org.example.sep26management.infrastructure.persistence.entity.UserEntity;
import org.example.sep26management.infrastructure.persistence.repository.OtpJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.UserJpaRepository;
import org.example.sep26management.infrastructure.security.JwtTokenProvider;
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
    private final OtpJpaRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final UserMapper userMapper;

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
                    auditLogService.logFailedLogin(request.getEmail(), "User not found", ipAddress);
                    return new UnauthorizedException("Invalid account or password.");
                });

        if (!canLogin(user)) {
            auditLogService.logFailedLogin(user.getEmail(), "Account not eligible", ipAddress);
            throw new UnauthorizedException("Account is disabled.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            handleFailedLogin(user, ipAddress);
            throw new UnauthorizedException("Invalid account or password.");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        user = userRepository.save(user);

        if (Boolean.TRUE.equals(user.getIsFirstLogin())) {
            return handleFirstLogin(user, ipAddress, userAgent);
        }

        String token = jwtTokenProvider.generateToken(
                user.getUserId(),
                user.getEmail(),
                user.getRole().name(),
                Boolean.TRUE.equals(request.getRememberMe())
        );

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
                userAgent,
                null,
                null
        );

        log.info("Login successful for user: {}", user.getEmail());

        return buildLoginResponse(user, token, expiresIn, false);
    }

    public LoginResponse verifyFirstLoginOtp(
            String email,
            VerifyOtpRequest request,
            String ipAddress,
            String userAgent
    ) {
        log.info("Verifying first login OTP for email: {}", email);

        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        OtpEntity otp = verifyOtp(email, request.getOtpCode(), OtpType.FIRST_LOGIN);

        otp.setIsUsed(true);
        otp.setVerifiedAt(LocalDateTime.now());
        otpRepository.save(otp);

        user.setStatus(UserStatus.ACTIVE);
        user.setIsFirstLogin(false);
        user = userRepository.save(user);

        String token = jwtTokenProvider.generateToken(
                user.getUserId(),
                user.getEmail(),
                user.getRole().name(),
                false
        );

        auditLogService.logAction(
                user.getUserId(),
                "FIRST_LOGIN_VERIFY",
                "USER",
                user.getUserId(),
                "First login verification successful",
                ipAddress,
                userAgent,
                null,
                null
        );

        log.info("First login verified for user: {}", user.getEmail());

        return buildLoginResponse(user, token, 60 * 60 * 1000L, false);
    }

    public ApiResponse<Void> sendResetPasswordOtp(ForgotPasswordRequest request) {
        log.info("Password reset requested for email: {}", request.getEmail());
        checkOtpRateLimit(request.getEmail(), OtpType.RESET_PASSWORD);
        String otpCode = generateOtp(request.getEmail(), OtpType.RESET_PASSWORD);
        emailService.sendOtpEmail(request.getEmail(), otpCode, "Password Reset");
        log.info("Reset password OTP sent to: {}", request.getEmail());
        return ApiResponse.success("The OTP has been sent to your email.");
    }

    public ApiResponse<Void> verifyResetPasswordOtp(String email, VerifyOtpRequest request) {
        log.info("Verifying reset password OTP for email: {}", email);
        OtpEntity otp = verifyOtp(email, request.getOtpCode(), OtpType.RESET_PASSWORD);
        userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Account does not exist"));
        otp.setVerifiedAt(LocalDateTime.now());
        otpRepository.save(otp);
        log.info("Reset password OTP verified for: {}", email);
        return ApiResponse.success("The OTP has been verified successfully.");
    }

    public ApiResponse<Void> resetPassword(String email, ResetPasswordRequest request) {
        log.info("Resetting password for email: {}", email);

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("The confirmed password does not match.");
        }

        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Account does not exist"));

        OtpEntity otp = otpRepository.findValidOtp(email, OtpType.RESET_PASSWORD, LocalDateTime.now())
                .orElseThrow(() -> new BusinessException("OTP verification required"));

        if (otp.getVerifiedAt() == null) {
            throw new BusinessException("OTP not verified");
        }

        if (otp.getIsUsed()) {
            throw new BusinessException("This reset link has expired");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        otp.setIsUsed(true);
        otpRepository.save(otp);

        auditLogService.logAction(
                user.getUserId(),
                "PASSWORD_RESET",
                "USER",
                user.getUserId(),
                "Password reset successful",
                null,
                null,
                null,
                "Password reset via OTP"
        );

        log.info("Password reset successful for: {}", email);
        return ApiResponse.success("The password has been reset successfully. Please login again.");
    }

    public ApiResponse<Void> resendOtp(String email, OtpType otpType) {
        log.info("Resending OTP for email: {} type: {}", email, otpType);
        checkOtpRateLimit(email, otpType);

        Optional<OtpEntity> existingOtp = otpRepository.findValidOtp(email, otpType, LocalDateTime.now());
        if (existingOtp.isPresent() && !existingOtp.get().getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Current OTP is still valid. Please wait until it expires.");
        }

        String otpCode = generateOtp(email, otpType);
        String subject = otpType == OtpType.FIRST_LOGIN ? "First Login Verification" : "Password Reset";
        emailService.sendOtpEmail(email, otpCode, subject);

        log.info("OTP resent to: {}", email);
        return ApiResponse.success("The OTP has been sent to your email.");
    }

    public ApiResponse<Void> logout(Long userId, String ipAddress, String userAgent) {
        log.info("Logout for user ID: {}", userId);

        auditLogService.logAction(
                userId,
                "LOGOUT",
                "USER",
                userId,
                "User logged out",
                ipAddress,
                userAgent,
                null,
                null
        );

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

    private void checkOtpRateLimit(String email, OtpType otpType) {
        long recentOtpCount = otpRepository.countRecentOtps(
                email,
                otpType,
                LocalDateTime.now().minusHours(1)
        );

        if (recentOtpCount >= otpResendLimitPerHour) {
            throw new BusinessException("OTP email quota exceeded in the current time window.");
        }
    }

    private OtpEntity verifyOtp(String email, String otpCode, OtpType otpType) {
        OtpEntity otp = otpRepository.findValidOtp(email, otpType, LocalDateTime.now())
                .orElseThrow(() -> new BusinessException("Your OTP has expired. Please request a new one."));

        if (otp.getIsUsed()) {
            throw new BusinessException("This OTP has already been used.");
        }

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("The OTP code has expired. You can request a new OTP now.");
        }

        if (otp.getAttemptsRemaining() <= 0) {
            throw new BusinessException("Your OTP has expired due to too many incorrect attempts. Please request a new OTP.");
        }

        if (!otp.getOtpCode().equals(otpCode)) {
            otp.setAttemptsRemaining(otp.getAttemptsRemaining() - 1);
            otpRepository.save(otp);

            if (otp.getAttemptsRemaining() <= 0) {
                throw new BusinessException("Your OTP has expired due to too many incorrect attempts. Please request a new OTP.");
            }

            throw new BusinessException(
                    String.format("The OTP code is incorrect. You have %d attempts remaining.",
                            otp.getAttemptsRemaining())
            );
        }

        return otp;
    }

    private String generateOtp(String email, OtpType otpType) {
        Random random = new Random();
        String otpCode = String.format("%06d", random.nextInt(1000000));

        OtpEntity otp = OtpEntity.builder()
                .email(email)
                .otpCode(otpCode)
                .otpType(otpType)
                .attemptsRemaining(otpMaxAttempts)
                .isUsed(false)
                .expiresAt(LocalDateTime.now().plusMinutes(otpExpirationMinutes))
                .build();

        otpRepository.save(otp);
        log.info("OTP generated for {} (type: {}): {}", email, otpType, otpCode);
        return otpCode;
    }

    private LoginResponse buildLoginResponse(
            UserEntity user,
            String token,
            Long expiresIn,
            boolean requiresVerification
    ) {
        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .requiresVerification(requiresVerification)
                .user(LoginResponse.UserInfoDTO.builder()
                        .userId(user.getUserId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .role(user.getRole())
                        .avatarUrl(user.getAvatarUrl())
                        .build())
                .build();
    }
}