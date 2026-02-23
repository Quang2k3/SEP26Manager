package org.example.sep26management.presentation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.sep26management.application.dto.request.CreateGrnRequest;
import org.example.sep26management.application.dto.request.CreateSessionRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.ScanSessionResponse;
import org.example.sep26management.application.service.ReceivingSessionService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/v1/receiving-sessions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('KEEPER') or hasRole('MANAGER')")
public class ReceivingSessionController {

    private final ReceivingSessionService receivingSessionService;

    /** POST /v1/receiving-sessions — Laptop creates a session */
    @PostMapping
    public ApiResponse<ScanSessionResponse> createSession(
            @Valid @RequestBody CreateSessionRequest request,
            Authentication auth) {
        Long userId = extractUserId(auth);
        return receivingSessionService.createSession(request, userId);
    }

    /** POST /v1/receiving-sessions/{id}/scan-token — Generate iPhone scan JWT */
    @PostMapping("/{sessionId}/scan-token")
    public ApiResponse<Map<String, String>> generateScanToken(
            @PathVariable String sessionId,
            Authentication auth) {
        Long userId = extractUserId(auth);
        return receivingSessionService.generateScanToken(sessionId, userId);
    }

    /** GET /v1/receiving-sessions/{id} — Snapshot of current lines */
    @GetMapping("/{sessionId}")
    public ApiResponse<ScanSessionResponse> getSession(@PathVariable String sessionId) {
        return receivingSessionService.getSession(sessionId);
    }

    /** DELETE /v1/receiving-sessions/{id} — Close session */
    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable String sessionId) {
        return receivingSessionService.deleteSession(sessionId);
    }

    /**
     * GET /v1/receiving-sessions/{id}/stream — SSE stream for laptop.
     * Produces text/event-stream; laptop subscribes and receives snapshot after
     * each scan.
     */
    @GetMapping(value = "/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String sessionId) {
        return receivingSessionService.stream(sessionId);
    }

    /**
     * POST /v1/receiving-sessions/{id}/create-grn
     * Turn scan session into a DRAFT GRN (receiving_orders + receiving_items).
     */
    @PostMapping("/{sessionId}/create-grn")
    public ApiResponse<Map<String, Object>> createGrn(
            @PathVariable String sessionId,
            @Valid @RequestBody CreateGrnRequest request,
            Authentication auth) {
        Long userId = extractUserId(auth);
        return receivingSessionService.createGrn(sessionId, request, userId);
    }

    @SuppressWarnings("unchecked")
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
}
