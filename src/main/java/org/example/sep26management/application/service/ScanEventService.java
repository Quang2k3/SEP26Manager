package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.request.ScanEventRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.scan.ScanLineItem;
import org.example.sep26management.application.dto.scan.ScanSessionData;
import org.example.sep26management.infrastructure.SseEmitterRegistry;
import org.example.sep26management.infrastructure.persistence.entity.SkuEntity;
import org.example.sep26management.infrastructure.persistence.redis.ScanSessionRedisRepository;
import org.example.sep26management.infrastructure.persistence.repository.SkuJpaRepository;
import org.example.sep26management.infrastructure.security.JwtTokenProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScanEventService {

    private final JwtTokenProvider jwtTokenProvider;
    private final ScanSessionRedisRepository sessionRedis;
    private final SkuJpaRepository skuRepository;
    private final SseEmitterRegistry sseRegistry;

    /**
     * Process a barcode scan event sent from the iPhone.
     * The scan token is already validated by security filter before reaching here.
     *
     * @param scanToken raw Bearer token (for sessionId extraction)
     * @param request   barcode + qty
     */
    public ApiResponse<Map<String, Object>> processScan(String scanToken, ScanEventRequest request) {
        // 1. Extract sessionId — scan token embeds it; regular JWT needs it in request
        // body
        String sessionId;
        if (jwtTokenProvider.isScanToken(scanToken)) {
            sessionId = jwtTokenProvider.getSessionIdFromScanToken(scanToken);
        } else {
            // Regular user JWT (e.g. Swagger testing) — sessionId must be in request body
            if (request.getSessionId() == null || request.getSessionId().isBlank()) {
                return ApiResponse.error("sessionId is required when calling with a regular user JWT token");
            }
            sessionId = request.getSessionId();
        }

        // 2. Load session from Redis
        ScanSessionData session = sessionRedis.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Scan session expired or not found: " + sessionId));

        // 3. Lookup SKU by barcode
        Optional<SkuEntity> skuOpt = skuRepository.findActiveByBarcodeWithCategory(request.getBarcode());
        if (skuOpt.isEmpty()) {
            log.warn("Scan event: no active SKU found for barcode={}", request.getBarcode());
            return ApiResponse.error("SKU not found for barcode: " + request.getBarcode());
        }
        SkuEntity sku = skuOpt.get();

        // 4. INCR qty in session lines (thread-safe via Redis overwrite)
        List<ScanLineItem> lines = session.getLines();
        Optional<ScanLineItem> existing = lines.stream()
                .filter(l -> l.getSkuId().equals(sku.getSkuId()))
                .findFirst();

        BigDecimal newQty;
        if (existing.isPresent()) {
            ScanLineItem line = existing.get();
            newQty = line.getQty().add(request.getQty());
            line.setQty(newQty);
        } else {
            newQty = request.getQty();
            lines.add(ScanLineItem.builder()
                    .skuId(sku.getSkuId())
                    .skuCode(sku.getSkuCode())
                    .skuName(sku.getSkuName())
                    .barcode(sku.getBarcode())
                    .qty(newQty)
                    .build());
        }

        // 5. Persist updated session back to Redis (refreshes TTL)
        sessionRedis.save(sessionId, session);

        // 6. Push snapshot to SSE (laptop sees real-time update)
        sseRegistry.send(sessionId, session);

        log.info("Scan event: sessionId={} barcode={} skuCode={} qty+{} → totalQty={}",
                sessionId, request.getBarcode(), sku.getSkuCode(), request.getQty(), newQty);

        return ApiResponse.success("Scanned", Map.of(
                "ok", true,
                "skuCode", sku.getSkuCode(),
                "skuName", sku.getSkuName(),
                "newQty", newQty));
    }
}
