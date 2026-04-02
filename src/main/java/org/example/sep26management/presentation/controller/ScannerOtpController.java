package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.service.ScannerOtpService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller cho Secure QR Scanner Flow.
 *
 * SecurityConfig cần thêm (xem SecurityConfig.java):
 *   .requestMatchers("/v1/scanner-otp/verify").permitAll()
 *   .requestMatchers("/v1/scanner-otp/generate").hasAnyRole("KEEPER","QC")
 */
@RestController
@RequestMapping("/v1/scanner-otp")
@RequiredArgsConstructor
@Tag(name = "Scanner OTP", description = "Secure QR Scanner — OTP auth cho thiết bị cầm tay")
public class ScannerOtpController {

    private final ScannerOtpService scannerOtpService;

    /**
     * Step 1 — Keeper/QC tạo QR trên Web.
     * Requires full JWT (KEEPER or QC).
     * OTP gửi về email — KHÔNG trong response.
     * Response: { sessionId } → FE encode vào QR URL: /scan?sessionId={sessionId}
     */
    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('KEEPER', 'QC')")
    @Operation(summary = "Tạo QR Scanner (Step 1)",
            description = "Tạo sessionId + OTP. OTP gửi email, không có trong response. " +
                    "FE encode sessionId vào QR: `{FE_URL}/scan?sessionId={sessionId}`. " +
                    "Truyền `receivingId` (request body hoặc query param) để bind QR với phiếu cụ thể.")
    public ApiResponse<Map<String, Object>> generateQr(
            Authentication auth,
            HttpServletRequest request,
            @RequestParam(required = false) Long receivingId) {

        return scannerOtpService.generateQr(
                extractUserId(auth),
                extractEmail(auth),
                extractRole(auth),
                extractWarehouseId(auth),
                getClientIp(request),
                receivingId    // null nếu không truyền — backward compat
        );
    }

    /**
     * Step 3 — Mobile verify OTP → nhận SCANNER_TEMP JWT.
     * PUBLIC — mobile chưa có JWT khi gọi endpoint này.
     * Body: { "sessionId": "uuid", "otp": "123456" }
     */
    @PostMapping("/verify")
    @Operation(summary = "Verify OTP → Scanner Token (Step 3)",
            description = "Nhập OTP → nhận scannerToken (JWT tạm thời). " +
                    "Rate limit: 10 lần/15 phút. Brute force: 5 sai → lock session.")
    public ApiResponse<Map<String, Object>> verifyOtp(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        String sessionId = body.get("sessionId");
        String otp       = body.get("otp");

        if (sessionId == null || sessionId.isBlank())
            return ApiResponse.error("sessionId không được để trống");
        if (otp == null || !otp.matches("\\d{6}"))
            return ApiResponse.error("OTP không hợp lệ (6 chữ số)");

        return scannerOtpService.verifyOtp(sessionId, otp, getClientIp(request));
    }

    // ─── Auth helpers ─────────────────────────────────────────────────────────

    private Long extractUserId(Authentication auth) {
        if (auth != null && auth.getDetails() instanceof Map<?,?> d) {
            Object uid = d.get("userId");
            if (uid instanceof Long l)    return l;
            if (uid instanceof Integer i) return i.longValue();
        }
        throw new RuntimeException("Cannot extract userId from auth");
    }

    private String extractEmail(Authentication auth) {
        if (auth != null) return auth.getName();
        throw new RuntimeException("Cannot extract email from auth");
    }

    private String extractRole(Authentication auth) {
        if (auth != null && auth.getAuthorities() != null) {
            return auth.getAuthorities().stream()
                    .map(a -> a.getAuthority().replace("ROLE_", ""))
                    .filter(r -> "KEEPER".equals(r) || "QC".equals(r))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("User must have KEEPER or QC role"));
        }
        throw new RuntimeException("Cannot extract role from auth");
    }

    @SuppressWarnings("unchecked")
    private Long extractWarehouseId(Authentication auth) {
        if (auth != null && auth.getDetails() instanceof Map<?,?> d) {
            Object raw = d.get("warehouseIds");
            if (raw instanceof List<?> list && !list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof Long l)    return l;
                if (first instanceof Integer i) return i.longValue();
                if (first instanceof Number n)  return n.longValue();
            }
        }
        throw new RuntimeException("Cannot extract warehouseId from auth");
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) return xri.trim();
        return request.getRemoteAddr();
    }
}