package org.example.sep26management.domain.entity;

import lombok.*;
import org.example.sep26management.domain.enums.UserStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    private Long userId;

    // Authentication
    private String email;
    private String passwordHash;

    // Profile
    private String fullName;
    private String phone;
    private String gender;
    private LocalDate dateOfBirth;
    private String address;
    private String avatarUrl;

    // Account Management
    private Set<String> roleCodes; // Dynamic roles from database (e.g., ["ADMIN", "MANAGER"])
    private UserStatus status;
    private Set<String> permissionCodes; // Fine-grained permissions resolved from roles

    // Account Type
    private Boolean isPermanent;
    private LocalDate expireDate;

    // Security
    private Boolean isFirstLogin;
    private LocalDateTime lastLoginAt;
    private Integer failedLoginAttempts;
    private LocalDateTime lockedUntil;
    private LocalDateTime passwordChangedAt;

    // Timestamps
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;

    // Domain Methods
    public boolean isActive() {
        return UserStatus.ACTIVE.equals(this.status);
    }

    public boolean isPendingVerification() {
        return UserStatus.PENDING_VERIFY.equals(this.status);
    }

    public boolean isLocked() {
        return UserStatus.LOCKED.equals(this.status) ||
                (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now()));
    }

    public boolean canLogin() {
        return (isActive() || isPendingVerification()) && !isLocked();
    }

    public void incrementFailedLoginAttempts() {
        this.failedLoginAttempts = (this.failedLoginAttempts == null ? 0 : this.failedLoginAttempts) + 1;

        // Lock account after 5 failed attempts
        if (this.failedLoginAttempts >= 5) {
            this.status = UserStatus.LOCKED;
            this.lockedUntil = LocalDateTime.now().plusMinutes(15);
        }
    }

    public void resetFailedLoginAttempts() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    public void markAsVerified() {
        this.status = UserStatus.ACTIVE;
        this.isFirstLogin = false;
    }

    /**
     * Check if user requires OTP verification
     * Only first-time login requires OTP verification
     */
    public boolean requiresOtpVerification() {
        return Boolean.TRUE.equals(isFirstLogin);
    }

    /**
     * Mark OTP verification as completed (first login done)
     */
    public void markOtpVerified() {
        this.isFirstLogin = false;
    }
}