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
@Tag(name = "Scan Events", description = "Xử lý sự kiện quét barcode từ iPhone. "
        + "iPhone gửi barcode + scan token → server tra cứu SKU → cập nhật session → push SSE về laptop.")
public class ScanEventController {

    private final ScanEventService scanEventService;

    /**
     * POST /v1/scan-events
     * Called by the iPhone scanner (Bearer JWT scan token required).
     */
    @PostMapping
    @Operation(summary = "Gửi sự kiện quét barcode", description = "iPhone gửi barcode đã quét. Yêu cầu Bearer scan token (sinh từ /receiving-sessions/{id}/scan-token). "
            + "Server tra cứu SKU theo barcode, cập nhật session, và push thông báo real-time về laptop.")
    public ApiResponse<Map<String, Object>> scan(
            @Valid @RequestBody ScanEventRequest request,
            HttpServletRequest httpRequest) {

        String scanToken = extractToken(httpRequest);
        return scanEventService.processScan(scanToken, request);
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        throw new RuntimeException("Missing Authorization header");
    }
}
