package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.scan.ScannerOtpData;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.example.sep26management.infrastructure.persistence.redis.ScannerOtpRedisRepository;
import org.example.sep26management.infrastructure.persistence.repository.ReceivingOrderJpaRepository;
import org.example.sep26management.infrastructure.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Business logic cho Secure QR Scanner Flow.
 *
 * FLOW:
 *  Step 1 — Keeper/QC bấm "Generate QR" trên web:
 *    POST /v1/scanner-otp/generate?receivingId=284
 *    → Nếu QC: atomic claim receiving_orders.assigned_qc_id ngay lập tức
 *    → sessionId + OTP BCrypt-hashed → Redis TTL 24h
 *    → OTP gửi email (KHÔNG có trong response)
 *    → response: { sessionId, ttlSeconds }
 *    → Push WS "qc_claimed" → QC khác thấy lock ngay (< 2s)
 *
 *  Step 2 — Mobile mở: /scan?sessionId={uuid} → nhập OTP
 *
 *  Step 3 — Mobile POST /v1/scanner-otp/verify { sessionId, otp }
 *    → validate → cấp SCANNER_TEMP JWT
 *
 *  Step 4 — Phone gọi POST /receiving-sessions?receivingId=284
 *    → tạo ScanSessionData với assignedQcId đã set sẵn
 *
 *  Step 5 — Phone scan barcode → POST /v1/scan-events
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScannerOtpService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ScannerOtpRedisRepository  otpRedis;
    private final JwtTokenProvider           jwtTokenProvider;
    private final EmailService               emailService;
    private final PasswordEncoder            passwordEncoder;
    private final ReceivingOrderJpaRepository receivingOrderRepo;
    private final NotificationService        notificationService;
    private final org.example.sep26management.infrastructure.persistence.redis.ScanSessionRedisRepository sessionRedis;

    // ─── Step 1: Generate QR ─────────────────────────────────────────────────

    @Transactional
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

        // ── QC claim: xảy ra ngay khi bấm "QC Scan" trên web ─────────────────
        // Không đợi scan QR hay nhập OTP — claim ngay để QC B thấy lock sớm nhất có thể.
        // Nếu đã có QC khác claim → từ chối, không gửi OTP.
        if ("QC".equals(role) && receivingId != null) {
            int claimed = receivingOrderRepo.claimQcAssignment(receivingId, userId);
            if (claimed == 0) {
                // Kiểm tra có phải chính mình đang claim không (re-generate QR)
                var orderOpt = receivingOrderRepo.findById(receivingId);
                if (orderOpt.isPresent() && orderOpt.get().getAssignedQcId() != null
                        && !orderOpt.get().getAssignedQcId().equals(userId)) {
                    log.warn("[QCClaim-OTP] Phiếu #{} đã bị QC userId={} claim. Từ chối userId={}.",
                            receivingId, orderOpt.get().getAssignedQcId(), userId);
                    throw new BusinessException(
                            "Phiếu #" + receivingId + " đang được QC khác kiểm định. "
                                    + "Vui lòng chờ hoặc liên hệ quản lý.");
                }
                // Nếu assigned_qc_id == userId → chính mình re-generate QR → cho phép tiếp tục
                log.info("[QCClaim-OTP] QC userId={} re-generating QR for receivingId={}", userId, receivingId);
            } else {
                // Claim thành công → push WS ngay để QC B thấy lock
                log.info("[QCClaim-OTP] QC userId={} claimed receivingId={}", userId, receivingId);
                try {
                    notificationService.notifyRoles(
                            new String[]{"QC", "MANAGER"},
                            "qc_claimed",
                            receivingId,
                            "Phiếu #" + receivingId,
                            "QC userId=" + userId + " bắt đầu kiểm định"
                    );
                } catch (Exception ignored) {}
            }
        }

        // Tạo OTP session
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
                .receivingId(receivingId)
                .used(false)
                .createdAt(Instant.now().toString())
                .failedAttempts(0)
                .build();

        otpRedis.save(data);
        otpRedis.incrementRateLimit(userId, clientIp);
        sendScannerOtpEmail(userEmail, rawOtp, role, sessionId);

        // CREATE SCAN SESSION DATA IMMEDIATELY
        // Để Frontend Web có thể subscribe SSE stream ngay lập tức mà không bị lỗi 500
        org.example.sep26management.application.dto.scan.ScanSessionData sessionData = org.example.sep26management.application.dto.scan.ScanSessionData.builder()
                .sessionId(sessionId)
                .warehouseId(warehouseId)
                .createdBy(userId)
                .receivingId(receivingId)
                .role(role)
                .lines(new java.util.ArrayList<>())
                .build();
        // pre-claim QC if needed
        if ("QC".equals(role) && receivingId != null) {
                sessionData.setAssignedQcId(userId);
        }
        
        // Lưu ScanSessionData vào Redis để Laptop có thể SSE ngay
        sessionRedis.save(sessionId, sessionData);
        sessionRedis.saveActiveSession(warehouseId, userId, sessionId);
        
        log.info("[ScannerOtp] Generated + ScanSession created: sessionId={} userId={} role={} receivingId={}",
                sessionId, userId, role, receivingId);

        Map<String, Object> resp = new HashMap<>();
        resp.put("sessionId",  sessionId);
        resp.put("role",       role);
        resp.put("ttlSeconds", 86400L);
        resp.put("message",    "OTP đã gửi về " + maskEmail(userEmail));
        return ApiResponse.success("QR đã tạo. OTP đã gửi về email.", resp);
    }

    // ─── Step 3: Verify OTP ───────────────────────────────────────────────────

    public ApiResponse<Map<String, Object>> verifyOtp(
            String sessionId, String inputOtp, String clientIp) {

        ScannerOtpData data = otpRedis.findById(sessionId)
                .orElseThrow(() -> new BusinessException(
                        "QR không hợp lệ hoặc đã hết hạn. Vui lòng tạo QR mới."));

        if (otpRedis.isRateLimited(data.getUserId(), clientIp)) {
            throw new BusinessException("Quá nhiều lần thử. Vui lòng thử lại sau 15 phút.");
        }

        if (data.isUsed()) {
            throw new BusinessException("QR này đã được sử dụng. Vui lòng tạo QR mới.");
        }

        if (data.getFailedAttempts() >= ScannerOtpData.MAX_FAILED_ATTEMPTS) {
            otpRedis.delete(sessionId);
            throw new BusinessException("QR bị khoá do nhập sai quá nhiều lần. Vui lòng tạo QR mới.");
        }

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

        // OTP đúng
        long remainingTtl = otpRedis.getTtlSeconds(sessionId);
        // FIXED: Chỉ mark used=true, KHÔNG xóa key ngay.
        // Key sẽ được xóa sau khi FE tạo scan session + scan token thành công.
        // Điều này cho phép FE retry tạo session nếu lần đầu thất bại,
        // mà không cần người dùng phải tạo lại mã OTP mới.
        data.setUsed(true);
        otpRedis.update(data);
        otpRedis.resetRateLimit(data.getUserId(), clientIp);

        log.info("[ScannerOtp] OTP verified ✓ sessionId={} userId={} role={}",
                sessionId, data.getUserId(), data.getRole());

        long tokenTtlMs = Math.min(remainingTtl, 86_400L) * 1_000L;
        String scannerToken = jwtTokenProvider.generateScannerTemporaryToken(
                sessionId, data.getWarehouseId(), data.getRole(), data.getUserId(), tokenTtlMs);

        Map<String, Object> result = new HashMap<>();
        result.put("scannerToken", scannerToken);
        result.put("role",         data.getRole());
        result.put("warehouseId",  data.getWarehouseId());
        result.put("sessionId",    sessionId);
        result.put("ttlSeconds",   remainingTtl);
        if (data.getReceivingId() != null) {
            result.put("receivingId", data.getReceivingId());
        }
        return ApiResponse.success("Xác thực OTP thành công. Scanner sẵn sàng.", result);
    }

    // ─── Step 3b: Cleanup OTP sau khi scan session tạo thành công ────────────
    // Được gọi bởi FE sau khi POST /receiving-sessions + /scan-token đều thành công.
    // Tách ra endpoint riêng để tránh lỗi "OTP đã dùng nhưng session chưa tạo được".
    public void cleanupOtpSession(String sessionId) {
        otpRedis.delete(sessionId);
        log.info("[ScannerOtp] OTP session cleaned up after successful scan session: {}", sessionId);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void sendScannerOtpEmail(String email, String otp, String role, String sessionId) {
        String roleVi  = "KEEPER".equals(role) ? "Thủ kho (Keeper)" : "Kiểm soát chất lượng (QC)";
        String subject = "[WMS] Mã OTP Scanner — " + roleVi;
        String body    = String.format("""
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
        String[] parts = email.split("@");
        return parts[0].charAt(0) + "***@" + parts[1];
    }
}