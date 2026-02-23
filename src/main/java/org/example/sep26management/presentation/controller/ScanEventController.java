package org.example.sep26management.presentation.controller;

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
public class ScanEventController {

    private final ScanEventService scanEventService;

    /**
     * POST /v1/scan-events
     * Called by the iPhone scanner (Bearer JWT scan token required).
     */
    @PostMapping
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
