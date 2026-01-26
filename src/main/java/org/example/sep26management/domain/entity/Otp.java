package org.example.sep26management.domain.entity;

import lombok.*;
import org.example.sep26management.domain.enums.OtpType;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Otp {
    private Long otpId;
    private String email;
    private String otpCode;
    private OtpType otpType;

    private Integer attemptsRemaining;
    private Boolean isUsed;

    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime verifiedAt;

    // Domain Methods
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !isUsed && !isExpired() && attemptsRemaining > 0;
    }

    public void decrementAttempts() {
        if (this.attemptsRemaining > 0) {
            this.attemptsRemaining--;
        }
    }

    public void markAsUsed() {
        this.isUsed = true;
        this.verifiedAt = LocalDateTime.now();
    }
}