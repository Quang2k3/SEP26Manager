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
    @Operation(summary = "Gửi sự kiện quét barcode", description = "Quét barcode với condition=PASS (hàng tốt) hoặc condition=FAIL (hàng lỗi). "
            + "Khi FAIL, cần kèm reasonCode (VD: LEAK, TORN_PACKAGING, DENTED).")
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
    @Operation(summary = "Xoá dòng quét nhầm", description = "Xoá một dòng khỏi session quét. Cần truyền sessionId, skuId, và condition (PASS/FAIL).")
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
