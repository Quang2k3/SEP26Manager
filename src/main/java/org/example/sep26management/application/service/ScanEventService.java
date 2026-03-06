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
     * Process a barcode scan event sent from the iPhone/Tablet.
     * Now supports condition (PASS/FAIL) to separate good and damaged items.
     */
    public ApiResponse<Map<String, Object>> processScan(String scanToken, ScanEventRequest request) {
        // 1. Extract sessionId
        String sessionId;
        if (jwtTokenProvider.isScanToken(scanToken)) {
            sessionId = jwtTokenProvider.getSessionIdFromScanToken(scanToken);
        } else {
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

        // 4. Normalize condition (default PASS)
        String condition = request.getCondition() != null ? request.getCondition().toUpperCase() : "PASS";
        if (!"PASS".equals(condition) && !"FAIL".equals(condition)) {
            return ApiResponse.error("Invalid condition. Must be PASS or FAIL.");
        }

        // 5. INCR qty — keyed by (skuId + condition)
        List<ScanLineItem> lines = session.getLines();
        Optional<ScanLineItem> existing = lines.stream()
                .filter(l -> l.getSkuId().equals(sku.getSkuId()) && condition.equals(l.getCondition()))
                .findFirst();

        BigDecimal newQty;
        if (existing.isPresent()) {
            ScanLineItem line = existing.get();
            newQty = line.getQty().add(request.getQty());
            line.setQty(newQty);
            // Update reasonCode if provided (last reason wins for aggregated lines)
            if (request.getReasonCode() != null) {
                line.setReasonCode(request.getReasonCode());
            }
        } else {
            newQty = request.getQty();
            lines.add(ScanLineItem.builder()
                    .skuId(sku.getSkuId())
                    .skuCode(sku.getSkuCode())
                    .skuName(sku.getSkuName())
                    .barcode(sku.getBarcode())
                    .qty(newQty)
                    .condition(condition)
                    .reasonCode(request.getReasonCode())
                    .build());
        }

        // 6. Persist updated session back to Redis (refreshes TTL)
        sessionRedis.save(sessionId, session);

        // 7. Push snapshot to SSE (laptop sees real-time update)
        sseRegistry.send(sessionId, session);

        log.info("Scan event: sessionId={} barcode={} skuCode={} condition={} qty+{} → totalQty={}",
                sessionId, request.getBarcode(), sku.getSkuCode(), condition, request.getQty(), newQty);

        return ApiResponse.success("Scanned", Map.of(
                "skuId", sku.getSkuId(),
                "skuCode", sku.getSkuCode(),
                "skuName", sku.getSkuName(),
                "barcode", sku.getBarcode(),
                "condition", condition,
                "newQty", newQty));
    }

    /**
     * Remove a specific scan line item from the session.
     * Used when Keeper scans an item incorrectly and needs to undo.
     *
     * @param sessionId session to modify
     * @param skuId     SKU to remove
     * @param condition PASS or FAIL — must match exactly
     */
    public ApiResponse<Map<String, Object>> removeScanItem(String sessionId, Long skuId, String condition) {
        ScanSessionData session = sessionRedis.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Scan session expired or not found: " + sessionId));

        String normalizedCondition = condition != null ? condition.toUpperCase() : "PASS";
        List<ScanLineItem> lines = session.getLines();

        boolean removed = lines.removeIf(
                l -> l.getSkuId().equals(skuId) && normalizedCondition.equals(l.getCondition()));

        if (!removed) {
            return ApiResponse
                    .error("Item not found in session: skuId=" + skuId + ", condition=" + normalizedCondition);
        }

        sessionRedis.save(sessionId, session);
        sseRegistry.send(sessionId, session);

        log.info("Scan item removed: sessionId={} skuId={} condition={}", sessionId, skuId, normalizedCondition);

        return ApiResponse.success("Item removed from scan session", Map.of(
                "skuId", skuId,
                "condition", normalizedCondition,
                "remainingLines", lines.size()));
    }
}
