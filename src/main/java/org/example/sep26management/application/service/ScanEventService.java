package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.request.ScanEventRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.scan.ScanLineItem;
import org.example.sep26management.application.dto.scan.ScanSessionData;
import org.example.sep26management.infrastructure.SseEmitterRegistry;
import org.example.sep26management.infrastructure.persistence.entity.ReceivingItemEntity;
import org.example.sep26management.infrastructure.persistence.entity.ReceivingOrderEntity;
import org.example.sep26management.infrastructure.persistence.entity.SkuEntity;
import org.example.sep26management.infrastructure.persistence.redis.ScanSessionRedisRepository;
import org.example.sep26management.infrastructure.persistence.repository.ReceivingItemJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.ReceivingOrderJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.SkuJpaRepository;
import org.example.sep26management.infrastructure.security.JwtTokenProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScanEventService {

    private final JwtTokenProvider jwtTokenProvider;
    private final ScanSessionRedisRepository sessionRedis;
    private final SkuJpaRepository skuRepository;
    private final SseEmitterRegistry sseRegistry;
    private final ReceivingOrderJpaRepository receivingOrderRepo;
    private final ReceivingItemJpaRepository receivingItemRepo;

    /**
     * Process a barcode scan event sent from the iPhone/Tablet.
     * Now supports condition (PASS/FAIL) to separate good and damaged items.
     */
    @Transactional
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

        // 3. Lookup SKU by barcode, fallback to skuCode (for manual input)
        Optional<SkuEntity> skuOpt = skuRepository.findActiveByBarcodeWithCategory(request.getBarcode());
        if (skuOpt.isEmpty()) {
            skuOpt = skuRepository.findActiveBySkuCodeWithCategory(request.getBarcode());
        }
        if (skuOpt.isEmpty()) {
            log.warn("Scan event: no active SKU found for barcode/skuCode={}", request.getBarcode());
            return ApiResponse.error("SKU not found: " + request.getBarcode());
        }
        SkuEntity sku = skuOpt.get();

        // 4. Normalize condition (default PASS)
        String condition = request.getCondition() != null ? request.getCondition().toUpperCase() : "PASS";
        if (!"PASS".equals(condition) && !"FAIL".equals(condition)) {
            return ApiResponse.error("Invalid condition. Must be PASS or FAIL.");
        }

        // 4.1 Optional: update ReceivingItem directly if receivingId provided
        // Wrapped in try-catch so scan session is always updated even if order update fails
        if (request.getReceivingId() != null) {
            try {
                Long receivingId = request.getReceivingId();
                ReceivingOrderEntity order = receivingOrderRepo.findById(receivingId)
                        .orElseThrow(() -> new RuntimeException("Receiving order not found: " + receivingId));

                String orderStatus = order.getStatus() != null ? order.getStatus().toUpperCase() : "DRAFT";
                if (!("POSTED".equals(orderStatus) || "PUTAWAY_DONE".equals(orderStatus)
                        || "CANCELLED".equals(orderStatus) || "REJECTED".equals(orderStatus))) {

                    receivingItemRepo
                            .findByReceivingOrderReceivingIdAndSkuId(receivingId, sku.getSkuId())
                            .ifPresent(item -> {
                                BigDecimal inc = request.getQty() != null ? request.getQty() : BigDecimal.ONE;
                                BigDecimal current = item.getReceivedQty() != null ? item.getReceivedQty() : BigDecimal.ZERO;
                                item.setReceivedQty(current.add(inc));
                                if ("FAIL".equals(condition)) {
                                    item.setCondition("FAIL");
                                    item.setQcRequired(true);
                                    if (request.getReasonCode() != null && !request.getReasonCode().isBlank()) {
                                        item.setReasonCode(request.getReasonCode());
                                    }
                                }
                                receivingItemRepo.save(item);
                                log.info("Updated ReceivingItem for order {}: SKU={}", receivingId, sku.getSkuCode());
                            });
                }
            } catch (Exception e) {
                // Log warning but continue — session must always be updated
                log.warn("Could not update ReceivingItem for receivingId={}, skuCode={}: {}",
                        request.getReceivingId(), sku.getSkuCode(), e.getMessage());
            }
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
    public ApiResponse<Map<String, Object>> removeScanItem(String sessionId, Long skuId, String condition,
                                                           BigDecimal qtyToRemove) {
        ScanSessionData session = sessionRedis.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Scan session expired or not found: " + sessionId));

        String normalizedCondition = condition != null ? condition.toUpperCase() : "PASS";
        List<ScanLineItem> lines = session.getLines();

        Optional<ScanLineItem> targetLine = lines.stream()
                .filter(l -> l.getSkuId().equals(skuId) && normalizedCondition.equals(l.getCondition()))
                .findFirst();

        if (targetLine.isEmpty()) {
            return ApiResponse
                    .error("Item not found in session: skuId=" + skuId + ", condition=" + normalizedCondition);
        }

        ScanLineItem line = targetLine.get();

        if (qtyToRemove != null && qtyToRemove.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal remainingQty = line.getQty().subtract(qtyToRemove);
            if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) {
                lines.remove(line);
                log.info("Scan item fully removed (qty <= 0): sessionId={} skuId={} condition={}", sessionId, skuId,
                        normalizedCondition);
            } else {
                line.setQty(remainingQty);
                log.info("Scan item partially removed: sessionId={} skuId={} condition={}, removed={}, remaining={}",
                        sessionId, skuId, normalizedCondition, qtyToRemove, remainingQty);
            }
        } else {
            lines.remove(line);
            log.info("Scan item fully removed (no qty specified): sessionId={} skuId={} condition={}", sessionId, skuId,
                    normalizedCondition);
        }

        sessionRedis.save(sessionId, session);
        sseRegistry.send(sessionId, session);

        return ApiResponse.success("Item updated/removed from scan session", Map.of(
                "skuId", skuId,
                "condition", normalizedCondition,
                "remainingLines", lines.size()));
    }
}