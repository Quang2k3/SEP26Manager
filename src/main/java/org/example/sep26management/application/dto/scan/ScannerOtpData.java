package org.example.sep26management.application.dto.scan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Data object lưu trong Redis cho Scanner OTP session.
 *
 * Redis key: scanner:otp:{sessionId}   TTL: 24h
 *
 * Security:
 *  - OTP hash BCrypt  →  Redis bị leak vẫn an toàn
 *  - used = true sau verify  →  one-time, chống replay attack
 *  - sessionId UUID random  →  không đoán được
 *  - failedAttempts >= MAX  →  session bị huỷ, buộc tạo mới
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScannerOtpData implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int MAX_FAILED_ATTEMPTS = 5;

    private String  sessionId;
    private Long    userId;
    private String  userEmail;

    /** "KEEPER" hoặc "QC" — bind cứng khi tạo, không thể thay đổi */
    private String  role;

    /** BCrypt hash của OTP 6 số */
    private String  otpHash;

    private Long    warehouseId;

    /**
     * Phiếu nhận hàng mà QR này phục vụ.
     * Được truyền từ FE khi generate QR (Keeper/QC đang mở đơn nào).
     * Sau verify OTP → gắn vào ScanSessionData để BE enforce.
     */
    private Long    receivingId;

    /** One-time flag — set true ngay sau khi verify thành công */
    @Builder.Default
    private boolean used = false;

    /** ISO-8601 */
    private String  createdAt;

    @Builder.Default
    private int failedAttempts = 0;
}