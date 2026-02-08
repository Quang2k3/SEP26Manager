package org.example.sep26management.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.sep26management.domain.enums.OtpType;

import java.time.LocalDateTime;

// Temporarily disabled to avoid database schema validation
// TODO: Re-enable when otps table is available in database
// Uncomment @Entity and fix table name when ready
// @Entity
// @Table(name = "otps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "otp_id")
    private Long otpId;

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Column(name = "otp_code", nullable = false, length = 6)
    private String otpCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "otp_type", nullable = false, length = 50)
    private OtpType otpType;

    @Column(name = "attempts_remaining")
    private Integer attemptsRemaining;

    @Column(name = "is_used")
    private Boolean isUsed;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (attemptsRemaining == null) {
            attemptsRemaining = 5;
        }
        if (isUsed == null) {
            isUsed = false;
        }
    }
}