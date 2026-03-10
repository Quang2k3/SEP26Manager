package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.sep26management.application.dto.request.CreateGrnRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.ScanSessionResponse;
import org.example.sep26management.application.service.ReceivingSessionService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/receiving-sessions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('KEEPER') or hasRole('MANAGER') or hasRole('QC')")
@Tag(name = "Receiving Sessions (Scan)", description = "Quản lý phiên scan nhận hàng. "
        + "Quy trình: Laptop tạo session → sinh QR/scan token cho iPhone → iPhone quét barcode gửi scan event "
        + "→ Laptop nhận SSE real-time → Tạo GRN (phiếu nhập kho) từ session.")
public class ReceivingSessionController {

    private final ReceivingSessionService receivingSessionService;

    /** POST /v1/receiving-sessions — Laptop creates a session */
    @PostMapping
    @Operation(summary = "Tạo phiên scan mới (Laptop/Web)", description = "Laptop tạo phiên scan. \n\n"
            + "**Data yêu cầu:** Không cần data truyền vào.\n"
            + "**Lưu ý:** API sẽ tự động lấy `warehouseId` từ profile của user đang đăng nhập (thông qua token). "
            + "Backend trả về `sessionId` (ví dụ: chuỗi UUID) và link tạo QR code (`qrCodeUrl`). \n"
            + "👉 **FE cần LƯU LẠI `sessionId` này** để dùng làm tham số cho tất cả các API phía sau thuộc phiên làm việc này.")
    public ApiResponse<ScanSessionResponse> createSession(Authentication auth) {
        Long userId = extractUserId(auth);
        Long warehouseId = extractWarehouseId(auth);
        return receivingSessionService.createSession(warehouseId, userId);
    }

    /** POST /v1/receiving-sessions/{id}/scan-token — Generate iPhone scan JWT */
    @PostMapping("/{sessionId}/scan-token")
    @Operation(summary = "Sinh scan token cho iPhone", description = "Sinh JWT scan token cho thiết bị cầm tay. \n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable sessionId`: Chuỗi UUID, **LẤY TỪ** response của API `POST /v1/receiving-sessions` ở bước trước.\n\n"
            + "**Kết quả:** Trả về một JWT Token (`scanToken`). Token này được nhúng vào mã QR để iPhone quét và dùng làm Bearer token khi iPhone gọi lấy API `POST /v1/scan-events`.")
    public ApiResponse<Map<String, String>> generateScanToken(
            @PathVariable String sessionId,
            Authentication auth) {
        Long userId = extractUserId(auth);
        String role = extractRole(auth);
        return receivingSessionService.generateScanToken(sessionId, userId, role);
    }

    /** GET /v1/receiving-sessions/{id} — Snapshot of current lines */
    @GetMapping("/{sessionId}")
    @Operation(summary = "Xem snapshot session", description = "Lấy thông tin session hiện tại: danh sách items đã scan, số lượng, trạng thái. \n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable sessionId`: Chuỗi UUID, **LẤY TỪ** response của việc tạo Session ban đầu.")
    public ApiResponse<ScanSessionResponse> getSession(@PathVariable String sessionId) {
        return receivingSessionService.getSession(sessionId);
    }

    /** DELETE /v1/receiving-sessions/{id} — Close session */
    @DeleteMapping("/{sessionId}")
    @Operation(summary = "Đóng/Xóa session", description = "Xóa phiên scan. Chặn đứng không cho iPhone gửi event được nữa. \n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable sessionId`: Chuỗi UUID Session cần xóa.")
    public ApiResponse<Void> deleteSession(@PathVariable String sessionId) {
        return receivingSessionService.deleteSession(sessionId);
    }

    /**
     * GET /v1/receiving-sessions/{id}/stream — SSE stream for laptop.
     */
    @GetMapping(value = "/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE stream real-time", description = "Laptop subscribe SSE stream. Mỗi lần iPhone scan thành công, server tự động đẩy data snapshot mới về Web. \n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable sessionId`: Chuỗi UUID, lấy từ bước Khởi tạo phiên.\n"
            + "👉 Web truyền vào URL EventSource để kết nối stream liên tục.")
    public SseEmitter stream(@PathVariable String sessionId) {
        return receivingSessionService.stream(sessionId);
    }

    /**
     * POST /v1/receiving-sessions/{id}/create-grn
     */
    @PostMapping("/{sessionId}/create-grn")
    @Operation(summary = "Tạo GRN từ session", description = "Chốt danh sách các mặt hàng đã quét, đóng session và chuyển hóa thành Phiếu Nhập Kho (GRN) trạng thái DRAFT. \n\n"
            + "**Data yêu cầu:** \n"
            + "- `@PathVariable sessionId`: UUID của session đang quét.\n"
            + "- `Body.supplierCode`: Mã Nhà Cung Cấp — **LẤY TỪ** API `GET /v1/suppliers` (field `supplierCode`). FE hiển thị `supplierName` để người dùng chọn, sau đó gửi `supplierCode` lên đây. BE tự resolve ra `supplierId` nội bộ.\n"
            + "- `Body.sourceReferenceCode`: Mã kiện/Bill (Ví dụ: PO-001, người dùng tự nhập tay).\n\n"
            + "👉 **Kết quả trả về:** Dữ liệu chứa thuộc tính `receivingId` (Đây là mã **ORDER ID / GRN ID**). **FE lưu LẠI `receivingId` này** để:\n"
            + "  1. Submit và duyệt đơn ở màn hình tiếp theo.\n"
            + "  2. **QUAN TRỌNG — Tự động điền phiếu lên trang scanner:** Khi tạo URL trang quét QR cho iPhone, "
            + "FE phải truyền `receivingId` vào query param khi gọi `GET /v1/scan/url`. "
            + "Ví dụ: `GET /v1/scan/url?token={scanToken}&receivingId={receivingId}`. "
            + "Nếu truyền đúng, trang scanner trên iPhone sẽ **tự động hiển thị ID phiếu nhận** màu xanh — "
            + "người dùng **không cần nhập tay** nữa. Nếu thiếu `receivingId`, trang sẽ fallback hiện ô nhập tay và cảnh báo màu vàng.")
    public ApiResponse<Map<String, Object>> createGrn(
            @PathVariable String sessionId,
            @Valid @RequestBody CreateGrnRequest request,
            Authentication auth) {
        Long userId = null;
        try {
            userId = extractUserId(auth);
        } catch (Exception e) {
            // If request comes from a scanner token, it won't have a userId.
            if (auth != null && auth.getName() != null && auth.getName().startsWith("scanner:")) {
                userId = null;
            } else {
                throw e;
            }
        }
        return receivingSessionService.createGrn(sessionId, request, userId);
    }

    private Long extractUserId(Authentication auth) {
        if (auth != null && auth.getDetails() instanceof Map) {
            Object uid = ((Map<?, ?>) auth.getDetails()).get("userId");
            if (uid instanceof Long)
                return (Long) uid;
            if (uid instanceof Integer)
                return ((Integer) uid).longValue();
        }
        throw new RuntimeException("Cannot extract userId from authentication");
    }

    /**
     * Extract the first warehouseId from the JWT token claims.
     * Each user role is bound to exactly one warehouse, so we take the first entry.
     */
    private Long extractWarehouseId(Authentication auth) {
        if (auth != null && auth.getDetails() instanceof Map) {
            Object raw = ((Map<?, ?>) auth.getDetails()).get("warehouseIds");
            if (raw instanceof List<?> list && !list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof Long)
                    return (Long) first;
                if (first instanceof Integer)
                    return ((Integer) first).longValue();
                if (first instanceof Number)
                    return ((Number) first).longValue();
            }
        }
        throw new RuntimeException(
                "Cannot extract warehouseId from authentication — ensure the role is assigned to a warehouse");
    }

    private String extractRole(Authentication auth) {
        if (auth != null && auth.getAuthorities() != null) {
            return auth.getAuthorities().stream()
                    .map(a -> a.getAuthority().replace("ROLE_", ""))
                    .findFirst().orElse("KEEPER");
        }
        return "KEEPER";
    }
}