package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.scan.ScannerOtpData;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.example.sep26management.infrastructure.persistence.redis.ScannerOtpRedisRepository;
import org.example.sep26management.infrastructure.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Business logic cho Secure QR Scanner Flow.
 *
 * FLOW:
 *  Step 1 — Keeper/QC bấm "Generate QR":
 *    POST /v1/scanner-otp/generate
 *    → sessionId (UUID) + OTP BCrypt-hashed → Redis TTL 24h
 *    → OTP gửi email (KHÔNG có trong response / URL)
 *    → response: { sessionId, ttlSeconds, message }
 *
 *  Step 2 — Mobile mở: /scan?sessionId={uuid}
 *    → FE hiện form nhập OTP
 *
 *  Step 3 — Mobile POST /v1/scanner-otp/verify { sessionId, otp }
 *    → validate: tồn tại, chưa used, brute-force ok, OTP khớp
 *    → cấp SCANNER_TEMP JWT (TTL = thời gian còn lại)
 *    → xóa session (one-time done)
 *
 *  Step 4 — Scanner gửi scan-events với SCANNER_TEMP JWT
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScannerOtpService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ScannerOtpRedisRepository otpRedis;
    private final JwtTokenProvider          jwtTokenProvider;
    private final EmailService              emailService;
    private final PasswordEncoder           passwordEncoder;

    // ─── Step 1: Generate QR ─────────────────────────────────────────────────

    public ApiResponse<Map<String, Object>> generateQr(
            Long userId, String userEmail, String role,
            Long warehouseId, String clientIp, Long receivingId) {

        if (!"KEEPER".equals(role) && !"QC".equals(role)) {
            throw new BusinessException("Role không hợp lệ cho scanner: " + role);
        }

        // Rate limit: 10 generate / 15 phút / userId+IP
        if (otpRedis.isRateLimited(userId, clientIp)) {
            throw new BusinessException("Quá nhiều yêu cầu tạo QR. Vui lòng thử lại sau 15 phút.");
        }

        String sessionId = UUID.randomUUID().toString();
        String rawOtp    = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        String otpHash   = passwordEncoder.encode(rawOtp);

        ScannerOtpData data = ScannerOtpData.builder()
                .sessionId(sessionId)
                .userId(userId)
                .userEmail(userEmail)
                .role(role)
                .otpHash(otpHash)
                .warehouseId(warehouseId)
                .receivingId(receivingId)          // bind phiếu cụ thể vào QR
                .used(false)
                .createdAt(Instant.now().toString())
                .failedAttempts(0)
                .build();

        otpRedis.save(data);
        otpRedis.incrementRateLimit(userId, clientIp);

        // Gửi OTP qua email bất đồng bộ
        sendScannerOtpEmail(userEmail, rawOtp, role, sessionId);

        log.info("[ScannerOtp] Generated: sessionId={} userId={} role={} warehouse={}",
                sessionId, userId, role, warehouseId);

        return ApiResponse.success("QR đã tạo. OTP đã gửi về email.", Map.of(
                "sessionId",  sessionId,
                "role",       role,
                "ttlSeconds", 86400L,
                "message",    "OTP đã gửi về " + maskEmail(userEmail)
        ));
    }

    // ─── Step 3: Verify OTP ───────────────────────────────────────────────────

    public ApiResponse<Map<String, Object>> verifyOtp(
            String sessionId, String inputOtp, String clientIp) {

        ScannerOtpData data = otpRedis.findById(sessionId)
                .orElseThrow(() -> new BusinessException(
                        "QR không hợp lệ hoặc đã hết hạn. Vui lòng tạo QR mới."));

        // Rate limit trên userId+IP
        if (otpRedis.isRateLimited(data.getUserId(), clientIp)) {
            throw new BusinessException("Quá nhiều lần thử. Vui lòng thử lại sau 15 phút.");
        }

        // Replay attack check
        if (data.isUsed()) {
            throw new BusinessException("QR này đã được sử dụng. Vui lòng tạo QR mới.");
        }

        // Session-level brute force check
        if (data.getFailedAttempts() >= ScannerOtpData.MAX_FAILED_ATTEMPTS) {
            otpRedis.delete(sessionId);
            throw new BusinessException("QR bị khoá do nhập sai quá nhiều lần. Vui lòng tạo QR mới.");
        }

        // Verify OTP (BCrypt)
        if (!passwordEncoder.matches(inputOtp, data.getOtpHash())) {
            data.setFailedAttempts(data.getFailedAttempts() + 1);
            otpRedis.update(data);
            otpRedis.incrementRateLimit(data.getUserId(), clientIp);

            int remaining = ScannerOtpData.MAX_FAILED_ATTEMPTS - data.getFailedAttempts();
            log.warn("[ScannerOtp] Wrong OTP: sessionId={} userId={} attempts={}/{}",
                    sessionId, data.getUserId(), data.getFailedAttempts(), ScannerOtpData.MAX_FAILED_ATTEMPTS);

            if (remaining <= 0) {
                otpRedis.delete(sessionId);
                throw new BusinessException("OTP sai. QR đã bị khoá. Vui lòng tạo QR mới.");
            }
            throw new BusinessException("OTP không đúng. Còn " + remaining + " lần thử.");
        }

        // ✓ OTP đúng — lấy TTL còn lại trước khi xoá
        long remainingTtl = otpRedis.getTtlSeconds(sessionId);

        // Mark used + xoá session (one-time done)
        data.setUsed(true);
        otpRedis.update(data);
        otpRedis.delete(sessionId);
        otpRedis.resetRateLimit(data.getUserId(), clientIp);

        log.info("[ScannerOtp] OTP verified ✓ sessionId={} userId={} role={}",
                sessionId, data.getUserId(), data.getRole());

        // Cấp SCANNER_TEMP JWT — TTL = thời gian còn lại của session (max 24h)
        long tokenTtlMs = Math.min(remainingTtl, 86_400L) * 1_000L;
        String scannerToken = jwtTokenProvider.generateScannerTemporaryToken(
                sessionId, data.getWarehouseId(), data.getRole(), data.getUserId(), tokenTtlMs);

        // Build response — includinging receivingId để FE (OtpGate) truyền vào createSession
        java.util.Map<String, Object> verifyResult = new java.util.HashMap<>();
        verifyResult.put("scannerToken", scannerToken);
        verifyResult.put("role",         data.getRole());
        verifyResult.put("warehouseId",  data.getWarehouseId());
        verifyResult.put("sessionId",    sessionId);
        verifyResult.put("ttlSeconds",   remainingTtl);
        if (data.getReceivingId() != null) {
            verifyResult.put("receivingId", data.getReceivingId());
        }
        return ApiResponse.success("Xác thực OTP thành công. Scanner sẵn sàng.", verifyResult);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void sendScannerOtpEmail(String email, String otp, String role, String sessionId) {
        String roleVi   = "KEEPER".equals(role) ? "Thủ kho (Keeper)" : "Kiểm soát chất lượng (QC)";
        String subject  = "[WMS] Mã OTP Scanner — " + roleVi;
        String body     = String.format("""
                Xin chào,

                Bạn vừa yêu cầu tạo QR Scanner cho vai trò: %s

                Mã OTP của bạn là:

                    %s

                Mã này có hiệu lực trong 24 giờ và chỉ dùng được 1 lần.
                Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email này.

                Trân trọng,
                Hệ thống Quản lý Kho WMS
                """, roleVi, otp);
        emailService.sendSimpleEmail(email, subject, body);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts  = email.split("@");
        String   masked = parts[0].charAt(0) + "***";
        return masked + "@" + parts[1];
    }
}