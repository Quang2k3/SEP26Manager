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
import org.example.sep26management.application.service.OutboundQcService;
import org.example.sep26management.application.dto.request.QcScanRequest;
import org.example.sep26management.infrastructure.persistence.repository.PickingTaskItemJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.PickingTaskJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.SkuJpaRepository;
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
    private final OutboundQcService outboundQcService;
    private final PickingTaskItemJpaRepository pickingTaskItemRepository;

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

        // ── Outbound QC mode: điện thoại scan barcode SKU → gọi qcScanItem trực tiếp ──
        if ("outbound_qc".equals(request.getMode()) && request.getTaskId() != null) {
            return processOutboundQcScan(request, sessionId);
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
            // [FIX] Merge attachmentUrl — mỗi thùng FAIL có ảnh riêng, cộng dồn tất cả
            if (request.getAttachmentUrl() != null && !request.getAttachmentUrl().isBlank()) {
                line.setAttachmentUrl(mergePhotoUrls(line.getAttachmentUrl(), request.getAttachmentUrl()));
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
                    // [FIX] Lưu attachmentUrl vào session line — thiếu dòng này là mất ảnh
                    .attachmentUrl(request.getAttachmentUrl())
                    .build());
        }

        // 6. Persist updated session back to Redis (refreshes TTL)
        sessionRedis.save(sessionId, session);

        // 7. Push snapshot to SSE (laptop sees real-time update)
        sseRegistry.send(sessionId, session);

        log.info("Scan event: sessionId={} barcode={} skuCode={} condition={} qty+{} → totalQty={}",
                sessionId, request.getBarcode(), sku.getSkuCode(), condition, request.getQty(), newQty);

        // Map.of() throws NullPointerException if any value is null
        // Use HashMap instead to safely handle null barcode
        java.util.Map<String, Object> resultData = new java.util.HashMap<>();
        resultData.put("skuId", sku.getSkuId());
        resultData.put("skuCode", sku.getSkuCode());
        resultData.put("skuName", sku.getSkuName() != null ? sku.getSkuName() : "");
        resultData.put("barcode", sku.getBarcode() != null ? sku.getBarcode() : "");
        resultData.put("condition", condition);
        resultData.put("newQty", newQty);
        return ApiResponse.success("Scanned", resultData);
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
                                                           BigDecimal qtyToRemove, Long receivingId) {
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

        // Also decrement receivingItem.receivedQty if receivingId was provided
        if (receivingId != null) {
            try {
                BigDecimal decrement = qtyToRemove != null && qtyToRemove.compareTo(BigDecimal.ZERO) > 0
                        ? qtyToRemove : line.getQty();
                receivingItemRepo.findByReceivingOrderReceivingIdAndSkuId(receivingId, skuId)
                        .ifPresent(item -> {
                            BigDecimal current = item.getReceivedQty() != null ? item.getReceivedQty() : BigDecimal.ZERO;
                            BigDecimal newQty = current.subtract(decrement);
                            item.setReceivedQty(newQty.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newQty);
                            receivingItemRepo.save(item);
                            log.info("Decremented ReceivingItem for order {}: SKU={}, -={}",
                                    receivingId, skuId, decrement);
                        });
            } catch (Exception e) {
                log.warn("Could not decrement ReceivingItem: {}", e.getMessage());
            }
        }

        return ApiResponse.success("Item updated/removed from scan session", Map.of(
                "skuId", skuId,
                "condition", normalizedCondition,
                "remainingLines", lines.size()));
    }
    /**
     * Xử lý scan event cho outbound QC.
     * Điện thoại scan barcode SKU → tìm picking task item → cộng dồn qcPassQty/qcFailQty.
     * Không ghi đè qcResult — tránh mất kết quả khi 1 SKU có nhiều unit (VD: 1 PASS + 1 FAIL).
     */
    private ApiResponse<Map<String, Object>> processOutboundQcScan(ScanEventRequest request, String sessionId) {
        Long taskId = request.getTaskId();
        String barcode = request.getBarcode();
        String condition = request.getCondition() != null ? request.getCondition().toUpperCase() : "PASS";
        String reasonCode = request.getReasonCode();

        // Tìm SKU theo barcode hoặc skuCode
        Optional<SkuEntity> skuOpt = skuRepository.findActiveByBarcodeWithCategory(barcode);
        if (skuOpt.isEmpty()) skuOpt = skuRepository.findActiveBySkuCodeWithCategory(barcode);
        if (skuOpt.isEmpty()) {
            log.warn("Outbound QC scan: SKU not found for barcode={}", barcode);
            return ApiResponse.error("SKU không tìm thấy: " + barcode);
        }
        SkuEntity sku = skuOpt.get();

        // Tìm picking task item thuộc task này và đúng SKU
        // Ưu tiên item chưa scan hết (qcPassQty + qcFailQty < requiredQty)
        var items = pickingTaskItemRepository.findByPickingTaskId(taskId);
        var taskItem = items.stream()
                .filter(i -> i.getSkuId().equals(sku.getSkuId()))
                .filter(i -> {
                    // Còn chỗ để scan thêm unit
                    java.math.BigDecimal scanned = safeQty(i.getQcPassQty()).add(safeQty(i.getQcFailQty()));
                    return scanned.compareTo(i.getRequiredQty()) < 0;
                })
                .findFirst()
                // Fallback: lấy item đầu tiên có cùng SKU dù đã scan hết
                .orElse(items.stream()
                        .filter(i -> i.getSkuId().equals(sku.getSkuId()))
                        .findFirst()
                        .orElse(null));

        if (taskItem == null) {
            return ApiResponse.error("Không tìm thấy mặt hàng " + sku.getSkuCode() + " trong Pick List #" + taskId);
        }

        // ── Cộng dồn qty thay vì ghi đè — FIX BUG: 1 PASS + 1 FAIL = đúng ──
        java.math.BigDecimal one = java.math.BigDecimal.ONE;
        if ("FAIL".equals(condition)) {
            taskItem.setQcFailQty(safeQty(taskItem.getQcFailQty()).add(one));
            // [MULTI-PHOTO] Merge JSON array ảnh — không ghi đè, cộng dồn
            if (request.getAttachmentUrl() != null && !request.getAttachmentUrl().isBlank()) {
                taskItem.setQcAttachmentUrl(mergePhotoUrls(taskItem.getQcAttachmentUrl(), request.getAttachmentUrl()));
            }
            if (reasonCode != null && !reasonCode.isBlank()) {
                taskItem.setQcNote(reasonCode);
            } else if (taskItem.getQcNote() == null) {
                taskItem.setQcNote("Lỗi phát hiện khi scan");
            }
        } else {
            taskItem.setQcPassQty(safeQty(taskItem.getQcPassQty()).add(one));
        }

        // Worst-case qcResult: FAIL nếu có bất kỳ unit FAIL nào
        String newResult = safeQty(taskItem.getQcFailQty()).compareTo(java.math.BigDecimal.ZERO) > 0
                ? "FAIL" : "PASS";
        taskItem.setQcResult(newResult);

        // Set qcScannedAt khi lần scan đầu tiên
        if (taskItem.getQcScannedAt() == null) {
            taskItem.setQcScannedAt(java.time.LocalDateTime.now());
        }

        // Cập nhật timestamp khi scan cuối
        java.math.BigDecimal totalScanned = safeQty(taskItem.getQcPassQty()).add(safeQty(taskItem.getQcFailQty()));
        if (totalScanned.compareTo(taskItem.getRequiredQty()) >= 0) {
            taskItem.setQcScannedAt(java.time.LocalDateTime.now());
        }

        pickingTaskItemRepository.save(taskItem);

        log.info("Outbound QC scan OK: taskId={}, SKU={}, condition={}, passQty={}, failQty={}/{}",
                taskId, sku.getSkuCode(), condition,
                taskItem.getQcPassQty(), taskItem.getQcFailQty(), taskItem.getRequiredQty());

        // Push SSE update nếu có session
        if (sessionId != null) {
            try {
                var sessionOpt = sessionRedis.findById(sessionId);
                if (sessionOpt.isPresent()) {
                    var allItems = pickingTaskItemRepository.findByPickingTaskId(taskId);
                    // Đã scan đủ = tổng (passQty + failQty) >= requiredQty
                    long pendingCount = allItems.stream()
                            .filter(i -> safeQty(i.getQcPassQty()).add(safeQty(i.getQcFailQty()))
                                    .compareTo(i.getRequiredQty()) < 0)
                            .count();
                    boolean allScanned = pendingCount == 0 && !allItems.isEmpty();
                    // passCount = số row có failQty = 0 và passQty > 0
                    // failCount = số row có failQty > 0
                    long passCount = allItems.stream()
                            .filter(i -> safeQty(i.getQcFailQty()).compareTo(java.math.BigDecimal.ZERO) == 0
                                    && safeQty(i.getQcPassQty()).compareTo(java.math.BigDecimal.ZERO) > 0)
                            .count();
                    long failCount = allItems.stream()
                            .filter(i -> safeQty(i.getQcFailQty()).compareTo(java.math.BigDecimal.ZERO) > 0)
                            .count();
                    java.util.Map<String, Object> ssePayload = new java.util.HashMap<>();
                    ssePayload.put("type", "qc_scan");
                    ssePayload.put("skuCode", sku.getSkuCode());
                    ssePayload.put("skuName", sku.getSkuName() != null ? sku.getSkuName() : "");
                    ssePayload.put("result", condition);
                    ssePayload.put("taskItemId", taskItem.getPickingTaskItemId());
                    ssePayload.put("passQty", safeQty(taskItem.getQcPassQty()));
                    ssePayload.put("failQty", safeQty(taskItem.getQcFailQty()));
                    ssePayload.put("allScanned", allScanned);
                    ssePayload.put("passCount", passCount);
                    ssePayload.put("failCount", failCount);
                    ssePayload.put("pendingCount", pendingCount);
                    sseRegistry.send(sessionId, ssePayload);
                }
            } catch (Exception ignored) {}
        }

        java.util.Map<String, Object> returnData = new java.util.HashMap<>();
        returnData.put("skuCode", sku.getSkuCode());
        returnData.put("skuName", sku.getSkuName() != null ? sku.getSkuName() : "");
        returnData.put("result", condition);
        returnData.put("taskItemId", taskItem.getPickingTaskItemId());
        returnData.put("passQty", safeQty(taskItem.getQcPassQty()));
        returnData.put("failQty", safeQty(taskItem.getQcFailQty()));
        returnData.put("requiredQty", taskItem.getRequiredQty());
        return ApiResponse.success("QC " + condition + " — " + sku.getSkuCode(), returnData);
    }

    /** Null-safe BigDecimal helper */
    private java.math.BigDecimal safeQty(java.math.BigDecimal v) {
        return v != null ? v : java.math.BigDecimal.ZERO;
    }

    /**
     * [MULTI-PHOTO] Merge ảnh: existing + incoming → JSON array string (tối đa 5, không trùng).
     * Tương thích ngược: existing là URL đơn (row cũ) → wrap thành ["url"].
     */
    private String mergePhotoUrls(String existing, String incoming) {
        java.util.List<String> urls = new java.util.ArrayList<>();
        for (String src : new String[]{existing, incoming}) {
            if (src == null || src.isBlank()) continue;
            if (src.trim().startsWith("[")) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                    java.util.List<String> parsed = om.readValue(src, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>(){});
                    urls.addAll(parsed);
                } catch (Exception e) { urls.add(src); }
            } else {
                urls.add(src);
            }
        }
        java.util.List<String> deduped = urls.stream().distinct().limit(5).collect(java.util.stream.Collectors.toList());
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(deduped);
        } catch (Exception e) {
            return deduped.isEmpty() ? null : deduped.get(0);
        }
    }
}