package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.MessageConstants;
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
import org.example.sep26management.application.dto.response.UserResponse;
import org.example.sep26management.infrastructure.mapper.UserEntityMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
        private final OtpService otpService; // Add OTP service for email verification

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
                                        return new UnauthorizedException(MessageConstants.INVALID_CREDENTIALS);
                                });

                // Step 5: Check account eligibility (BR-AUTH-01)
                if (!canLogin(user)) {
                        auditLogService.logFailedLogin(user.getEmail(), "Account not eligible", ipAddress);
                        throw new UnauthorizedException(MessageConstants.ACCOUNT_DISABLED);
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
                                auditLogService.logFailedLogin(user.getEmail(), "Account locked due to failed attempts",
                                                ipAddress);
                                throw new UnauthorizedException(MessageConstants.ACCOUNT_DISABLED);
                        }

                        userRepository.save(user);
                        auditLogService.logFailedLogin(user.getEmail(), "Invalid password", ipAddress);
                        throw new UnauthorizedException(MessageConstants.INVALID_CREDENTIALS);
                }

                // Reset failed attempts on successful password verification
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(null);
                user.setLastLoginAt(LocalDateTime.now());
                userRepository.save(user);

                // ===== OTP VERIFICATION CHECK (FIRST LOGIN ONLY) =====
                // Convert to domain model for business logic
                User domainUser = userEntityMapper.toDomain(user);

                // Check if OTP verification is required (only for first login)
                if (domainUser.requiresOtpVerification()) {
                        log.info("First login detected, OTP verification required for user: {}", user.getEmail());

                        // Generate and send OTP
                        otpService.generateAndSendOtp(user.getEmail());

                        // Audit log
                        auditLogService.logAction(
                                        user.getUserId(),
                                        "LOGIN_PENDING_OTP",
                                        "USER",
                                        user.getUserId(),
                                        "First login - OTP verification required",
                                        ipAddress,
                                        userAgent);

                        // Generate pending token for OTP verification
                        String pendingToken = jwtTokenProvider.generatePendingToken(user.getEmail());

                        // Return response indicating verification required
                        return LoginResponse.builder()
                                        .requiresVerification(true)
                                        .pendingToken(pendingToken)
                                        .user(LoginResponse.UserInfoDTO.builder()
                                                        .userId(user.getUserId())
                                                        .email(user.getEmail())
                                                        .fullName(user.getFullName())
                                                        .build())
                                        .build();
                }

                // Step 7: Generate JWT token using UserEntityMapper
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
         * Complete OTP verification after first login and generate JWT token
         * Called after OTP is successfully verified
         * 
         * @param email     User email
         * @param ipAddress IP address
         * @param userAgent User agent
         * @return LoginResponse with JWT token
         */
        public LoginResponse completeEmailVerification(String email, String ipAddress, String userAgent) {
                log.info("Completing OTP verification for first login: {}", email);

                // Find user
                UserEntity user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new UnauthorizedException(MessageConstants.USER_NOT_FOUND));

                // Mark first login as completed
                user.setIsFirstLogin(false);
                userRepository.save(user);

                // Convert to domain model
                User domainUser = userEntityMapper.toDomain(user);

                // Generate JWT token (default remember me = false for security)
                String token = jwtTokenProvider.generateToken(domainUser, false);
                long expiresIn = 5 * 60 * 1000L; // 5 minutes

                // Audit log
                auditLogService.logAction(
                                user.getUserId(),
                                "FIRST_LOGIN_COMPLETED",
                                "USER",
                                user.getUserId(),
                                "OTP verified successfully, first login completed",
                                ipAddress,
                                userAgent);

                log.info("First login OTP verification completed for user: {}", email);

                // Return full login response
                return LoginResponse.builder()
                                .token(token)
                                .tokenType("Bearer")
                                .expiresIn(expiresIn)
                                .requiresVerification(false)
                                .user(LoginResponse.UserInfoDTO.builder()
                                                .userId(user.getUserId())
                                                .email(user.getEmail())
                                                .fullName(user.getFullName())
                                                .roleCodes(domainUser.getRoleCodes())
                                                .avatarUrl(user.getAvatarUrl())
                                                .build())
                                .build();
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

                return ApiResponse.success(MessageConstants.LOGOUT_SUCCESS);
        }

        /**
         * UC-AUTH-03: Get Current User
         */
        @Transactional(readOnly = true)
        public UserResponse getCurrentUser(Long userId) {
                log.info("Fetching current user for ID: {}", userId);

                UserEntity userEntity = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(MessageConstants.USER_NOT_FOUND));

                User user = userEntityMapper.toDomain(userEntity);

                return UserResponse.builder()
                                .userId(user.getUserId())
                                .email(user.getEmail())
                                .fullName(user.getFullName())
                                .phone(user.getPhone())
                                .gender(user.getGender())
                                .dateOfBirth(user.getDateOfBirth())
                                .address(user.getAddress())
                                .avatarUrl(user.getAvatarUrl())
                                .roleCodes(user.getRoleCodes())
                                .status(user.getStatus())
                                .isPermanent(user.getIsPermanent())
                                .expireDate(user.getExpireDate())
                                .lastLoginAt(user.getLastLoginAt())
                                .createdAt(user.getCreatedAt())
                                .build();
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