package org.example.sep26management.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.sep26management.application.dto.request.ScanEventRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.service.ScanEventService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/scan-events")
@RequiredArgsConstructor
@Tag(name = "Scan Events", description = "Xử lý sự kiện quét barcode từ iPhone/Tablet. "
        + "Hỗ trợ 2 chế độ: PASS (hàng tốt) và FAIL (hàng lỗi kèm reasonCode).")
public class ScanEventController {

    private final ScanEventService scanEventService;

    /**
     * POST /v1/scan-events
     * Called by the iPhone scanner (Bearer JWT scan token required).
     */
    @PostMapping
    @Operation(summary = "Gửi sự kiện quét barcode", description = "Dành cho thiết bị cầm tay. Gửi tọa độ quét mã vạch của 1 sản phẩm. \n\n"
            + "**Data yêu cầu:** \n"
            + "- **Header:** `Authorization: Bearer <scanToken>` (Token này **LẤY TỪ** API `POST /v1/receiving-sessions/{sessionId}/scan-token`).\n"
            + "- `Body.barcode`: Chuỗi mã vạch quét được trên sản phẩm.\n"
            + "- `Body.condition`: Tình trạng hàng, chọn `PASS` (Đạt) hoặc `FAIL` (Lỗi).\n"
            + "  - *Lưu ý:* Nếu chọn `FAIL`, **BẮT BUỘC** truyền thêm `Body.reasonCode` (Ví dụ: `LEAK`, `TORN_PACKAGING`). Server sẽ lưu lại và tự động đẩy lô hàng này sang luồng kiểm tra chất lượng (QC).")
    public ApiResponse<Map<String, Object>> scan(
            @Valid @RequestBody ScanEventRequest request,
            HttpServletRequest httpRequest) {

        String scanToken = extractToken(httpRequest);
        return scanEventService.processScan(scanToken, request);
    }

    /**
     * DELETE /v1/scan-events
     * Remove a specific scan line item (e.g. Keeper scanned wrong item).
     */
    @DeleteMapping
    @Operation(summary = "Xoá dòng quét nhầm", description = "Xoá một dòng/sản phẩm đã lỡ quét sai khỏi session. \n\n"
            + "**Data yêu cầu:** \n"
            + "- `Query.sessionId`: Chuỗi UUID của phiên làm việc hiện tại.\n"
            + "- `Query.skuId`: ID của sản phẩm (lấy từ dữ liệu stream SSE đang hiển thị trên web).\n"
            + "- `Query.condition`: Trạng thái quét lúc nãy (PASS/FAIL).")
    public ApiResponse<Map<String, Object>> removeScanItem(
            @RequestParam String sessionId,
            @RequestParam Long skuId,
            @RequestParam(defaultValue = "PASS") String condition) {

        return scanEventService.removeScanItem(sessionId, skuId, condition);
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        throw new RuntimeException("Missing Authorization header");
    }
}
