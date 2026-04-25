package org.example.sep26management.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.request.ScanEventRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.scan.ScanLineItem;
import org.example.sep26management.application.dto.scan.ScanSessionData;
import org.example.sep26management.application.service.NotificationService;
import org.example.sep26management.infrastructure.SseEmitterRegistry;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.example.sep26management.infrastructure.persistence.entity.PickingTaskItemEntity;
import org.example.sep26management.infrastructure.persistence.entity.ReceivingOrderEntity;
import org.example.sep26management.infrastructure.persistence.entity.SkuEntity;
import org.example.sep26management.infrastructure.persistence.redis.ScanSessionRedisRepository;
import org.example.sep26management.infrastructure.persistence.repository.PickingTaskItemJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.ReceivingItemJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.ReceivingOrderJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.SkuJpaRepository;
import org.example.sep26management.infrastructure.security.JwtTokenProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScanEventService {

    private final JwtTokenProvider             jwtTokenProvider;
    private final ScanSessionRedisRepository   sessionRedis;
    private final SkuJpaRepository             skuRepository;
    private final SseEmitterRegistry           sseRegistry;
    private final ReceivingOrderJpaRepository  receivingOrderRepo;
    private final ReceivingItemJpaRepository   receivingItemRepo;
    private final PickingTaskItemJpaRepository pickingTaskItemRepository;
    private final ObjectMapper                 objectMapper;
    private final NotificationService          notificationService;

    // ─── Inbound scan ─────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<Map<String, Object>> processScan(String scanToken, ScanEventRequest request) {

        // 1. Extract sessionId
        String sessionId;
        if (jwtTokenProvider.isScanToken(scanToken)) {
            sessionId = jwtTokenProvider.getSessionIdFromScanToken(scanToken);
        } else {
            if (request.getSessionId() == null || request.getSessionId().isBlank()) {
                return ApiResponse.error("sessionId là bắt buộc khi dùng user JWT token");
            }
            sessionId = request.getSessionId();
        }

        // 2. Outbound QC mode → delegate
        if ("outbound_qc".equals(request.getMode()) && request.getTaskId() != null) {
            return processOutboundQcScan(request, sessionId);
        }

        // 3. Load session
        ScanSessionData session = sessionRedis.findById(sessionId)
                .orElseThrow(() -> new BusinessException("Phiên scan đã hết hạn. Vui lòng tạo QR mới."));

        // ── Giải pháp 1: Validate receivingId binding ─────────────────────────
        if (session.getReceivingId() != null && request.getReceivingId() != null
                && !session.getReceivingId().equals(request.getReceivingId())) {
            log.warn("[Security] Session bound receivingId={} nhưng request gửi receivingId={}. userId={}",
                    session.getReceivingId(), request.getReceivingId(), session.getCreatedBy());
            return ApiResponse.error(
                    "QR này chỉ dùng được cho phiếu #" + session.getReceivingId()
                            + ". Vui lòng tạo QR mới cho phiếu #" + request.getReceivingId() + ".");
        }

        // ── Giải pháp 2: QC claim lock ────────────────────────────────────────
        if ("QC".equals(session.getRole())) {
            ApiResponse<Map<String, Object>> claimError = handleQcClaim(session, sessionId);
            if (claimError != null) return claimError;
        }

        // 4. Lookup SKU
        Optional<SkuEntity> skuOpt = skuRepository.findActiveByBarcodeWithCategory(request.getBarcode());
        if (skuOpt.isEmpty()) skuOpt = skuRepository.findActiveBySkuCodeWithCategory(request.getBarcode());
        if (skuOpt.isEmpty()) {
            log.warn("Scan: SKU không tìm thấy barcode={}", request.getBarcode());
            return ApiResponse.error("Không tìm thấy SKU: " + request.getBarcode());
        }
        SkuEntity sku = skuOpt.get();

        // 5. Normalize condition
        String condition = request.getCondition() != null ? request.getCondition().toUpperCase() : "PASS";
        if (!"PASS".equals(condition) && !"FAIL".equals(condition)) {
            return ApiResponse.error("Condition không hợp lệ. Chỉ chấp nhận PASS hoặc FAIL.");
        }

        // 6. Atomic UPDATE receivedQty (tránh lost-update)
        if (request.getReceivingId() != null) {
            atomicIncrementReceivingItem(request, sku, condition);
        }

        // 7. Cập nhật session Redis — dùng Lua atomic để tránh lost-update khi 2 scan đồng thời
        BigDecimal inc = request.getQty() != null ? request.getQty() : BigDecimal.ONE;
        String luaResult = sessionRedis.atomicUpdateLine(
                sessionId, sku.getSkuId(), condition, inc, sku.getSkuCode(), sku.getSkuName(), sku.getBarcode(), request.getLotNumber());
        BigDecimal newQty = luaResult != null ? new BigDecimal(luaResult) : inc;

        // 7b. Reload session từ Redis để push SSE (Lua đã save rồi) và gán ảnh + reasonCode nếu có
        sessionRedis.findById(sessionId).ifPresent(updated -> {
            boolean changed = false;
            for (int i = updated.getLines().size() - 1; i >= 0; i--) {
                ScanLineItem line = updated.getLines().get(i);
                if (sku.getSkuId().equals(line.getSkuId()) && condition.equals(line.getCondition())) {
                    // Add attachmentUrl into the fetched session line
                    if (request.getAttachmentUrl() != null && !request.getAttachmentUrl().isBlank()) {
                        line.setAttachmentUrl(mergePhotoUrls(line.getAttachmentUrl(), request.getAttachmentUrl()));
                        changed = true;
                    }
                    // [DEFECT-TAGS] Lưu reasonCode (chứa defect tags) vào scan line
                    if (request.getReasonCode() != null && !request.getReasonCode().isBlank()) {
                        line.setReasonCode(request.getReasonCode());
                        changed = true;
                    }
                    break;
                }
            }
            if (changed) {
                sessionRedis.save(sessionId, updated);
            }
            // 8. Push SSE
            sseRegistry.send(sessionId, updated);
        });

        log.info("Scan OK: session={} sku={} condition={} qty={}", sessionId, sku.getSkuCode(), condition, newQty);

        // Dùng HashMap thay Map.of() để tránh NullPointerException khi value null
        Map<String, Object> result = new HashMap<>();
        result.put("skuId",     sku.getSkuId());
        result.put("skuCode",   sku.getSkuCode() != null   ? sku.getSkuCode()   : "");
        result.put("skuName",   sku.getSkuName() != null   ? sku.getSkuName()   : "");
        result.put("barcode",   sku.getBarcode() != null   ? sku.getBarcode()   : "");
        result.put("condition", condition);
        result.put("newQty",    newQty);
        return ApiResponse.success("Scanned", result);
    }

    // ─── Remove scan item ──────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<Map<String, Object>> removeScanItem(
            String sessionId, Long skuId, String condition, BigDecimal qtyToRemove, Long receivingId, String lotNumber) {

        ScanSessionData session = sessionRedis.findById(sessionId)
                .orElseThrow(() -> new BusinessException("Phiên scan đã hết hạn. Vui lòng tạo QR mới."));

        String norm = condition != null ? condition.toUpperCase() : "PASS";
        List<ScanLineItem> lines = session.getLines();

        Optional<ScanLineItem> target = lines.stream()
                .filter(l -> l.getSkuId().equals(skuId) && norm.equals(l.getCondition()) &&
                             (lotNumber == null || lotNumber.equals(l.getLotNumber())))
                .findFirst();

        if (target.isEmpty()) {
            return ApiResponse.error("Không tìm thấy item: skuId=" + skuId + " lot=" + lotNumber + " condition=" + norm);
        }
        ScanLineItem line = target.get();

        if (qtyToRemove != null && qtyToRemove.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal remaining = line.getQty().subtract(qtyToRemove);
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) lines.remove(line);
            else line.setQty(remaining);
        } else {
            lines.remove(line);
        }

        sessionRedis.save(sessionId, session);
        sseRegistry.send(sessionId, session);

        if (receivingId != null) {
            try {
                BigDecimal dec = (qtyToRemove != null && qtyToRemove.compareTo(BigDecimal.ZERO) > 0)
                        ? qtyToRemove : line.getQty();
                int affected = receivingItemRepo.decrementReceivedQty(receivingId, skuId, dec, lotNumber);
                if (affected > 0) {
                    log.info("[ATOMIC] -{} receivedQty receivingId={} skuId={}", dec, receivingId, skuId);
                }
            } catch (Exception e) {
                log.warn("decrementReceivedQty failed: {}", e.getMessage());
            }
        }

        // HashMap thay Map.of() — tránh NPE khi skuId hoặc norm là null
        Map<String, Object> result = new HashMap<>();
        result.put("skuId",          skuId);
        result.put("condition",      norm);
        result.put("remainingLines", lines.size());
        return ApiResponse.success("Item đã xóa/giảm", result);
    }

    // ─── Outbound QC scan ──────────────────────────────────────────────────────
    // PHẢI là protected để Spring AOP proxy wrap được @Transactional.
    // private method không được Spring intercept → transaction không hoạt động.

    @Transactional
    protected ApiResponse<Map<String, Object>> processOutboundQcScan(ScanEventRequest request, String sessionId) {
        Long   taskId     = request.getTaskId();
        String condition  = request.getCondition() != null ? request.getCondition().toUpperCase() : "PASS";
        String reasonCode = request.getReasonCode();

        // Lookup SKU
        Optional<SkuEntity> skuOpt = skuRepository.findActiveByBarcodeWithCategory(request.getBarcode());
        if (skuOpt.isEmpty()) skuOpt = skuRepository.findActiveBySkuCodeWithCategory(request.getBarcode());
        if (skuOpt.isEmpty()) {
            log.warn("Outbound QC: SKU không tìm thấy barcode={}", request.getBarcode());
            return ApiResponse.error("Không tìm thấy SKU: " + request.getBarcode());
        }
        SkuEntity sku = skuOpt.get();

        // Tìm picking task item — ưu tiên chưa scan đủ
        var items = pickingTaskItemRepository.findByPickingTaskId(taskId);
        PickingTaskItemEntity taskItem = items.stream()
                .filter(i -> i.getSkuId().equals(sku.getSkuId()))
                .filter(i -> safeQty(i.getQcPassQty()).add(safeQty(i.getQcFailQty()))
                        .compareTo(i.getRequiredQty()) < 0)
                .findFirst()
                .orElse(items.stream()
                        .filter(i -> i.getSkuId().equals(sku.getSkuId()))
                        .findFirst().orElse(null));

        if (taskItem == null) {
            return ApiResponse.error("Không tìm thấy " + sku.getSkuCode() + " trong Pick List #" + taskId);
        }

        Long itemId = taskItem.getPickingTaskItemId();

        // Atomic increment
        int affected;
        if ("FAIL".equals(condition)) {
            affected = pickingTaskItemRepository.incrementQcFailQty(itemId);
            if (affected > 0 && (request.getAttachmentUrl() != null || reasonCode != null)) {
                taskItem = pickingTaskItemRepository.findById(itemId).orElse(taskItem);
                if (request.getAttachmentUrl() != null && !request.getAttachmentUrl().isBlank()) {
                    taskItem.setQcAttachmentUrl(mergePhotoUrls(taskItem.getQcAttachmentUrl(), request.getAttachmentUrl()));
                }
                taskItem.setQcNote(reasonCode != null && !reasonCode.isBlank()
                        ? reasonCode : (taskItem.getQcNote() == null ? "Lỗi phát hiện khi scan" : taskItem.getQcNote()));
                pickingTaskItemRepository.save(taskItem);
            }
        } else {
            affected = pickingTaskItemRepository.incrementQcPassQty(itemId);
        }

        if (affected == 0) log.warn("Outbound QC increment 0 rows: itemId={}", itemId);

        taskItem = pickingTaskItemRepository.findById(itemId).orElse(taskItem);

        log.info("Outbound QC OK: taskId={} sku={} {} pass={} fail={}/{}",
                taskId, sku.getSkuCode(), condition,
                taskItem.getQcPassQty(), taskItem.getQcFailQty(), taskItem.getRequiredQty());

        pushOutboundQcSse(sessionId, taskId, sku, condition, taskItem);

        Map<String, Object> result = new HashMap<>();
        result.put("skuCode",     sku.getSkuCode() != null ? sku.getSkuCode() : "");
        result.put("skuName",     sku.getSkuName() != null ? sku.getSkuName() : "");
        result.put("result",      condition);
        result.put("taskItemId",  taskItem.getPickingTaskItemId());
        result.put("passQty",     safeQty(taskItem.getQcPassQty()));
        result.put("failQty",     safeQty(taskItem.getQcFailQty()));
        result.put("requiredQty", taskItem.getRequiredQty());
        return ApiResponse.success("QC " + condition + " — " + sku.getSkuCode(), result);
    }

    // ─── Private helpers ───────────────────────────────────────────────────────

    /**
     * QC claim: QC đầu tiên scan → claim session + atomic claim trên DB.
     * Trả null nếu claim OK, trả ApiResponse lỗi nếu QC khác đã claim trước.
     */
    private ApiResponse<Map<String, Object>> handleQcClaim(ScanSessionData session, String sessionId) {
        if (session.getAssignedQcId() == null) {
            session.setAssignedQcId(session.getCreatedBy());
            sessionRedis.save(sessionId, session);
            log.info("[QCClaim] userId={} claimed session={} receivingId={}",
                    session.getCreatedBy(), sessionId, session.getReceivingId());

            // Atomic claim trên DB để block QC thứ 2 dùng session khác
            if (session.getReceivingId() != null) {
                int claimed = receivingOrderRepo.claimQcAssignment(
                        session.getReceivingId(), session.getCreatedBy());
                if (claimed == 0) {
                    ReceivingOrderEntity ord = receivingOrderRepo
                            .findById(session.getReceivingId()).orElse(null);
                    if (ord != null && ord.getAssignedQcId() != null
                            && !ord.getAssignedQcId().equals(session.getCreatedBy())) {
                        session.setAssignedQcId(null);
                        sessionRedis.save(sessionId, session);
                        log.warn("[QCClaim] Đơn #{} đã bị userId={} claim. Từ chối userId={}.",
                                session.getReceivingId(), ord.getAssignedQcId(), session.getCreatedBy());
                        return ApiResponse.error("Phiếu #" + session.getReceivingId()
                                + " đang được QC khác kiểm định. Vui lòng liên hệ quản lý.");
                    }
                }

                // Push WS → QC khác reload danh sách ngay, thấy nút bị lock
                try {
                    notificationService.notifyRoles(
                            new String[]{"QC", "MANAGER"},
                            "qc_claimed",
                            session.getReceivingId(),
                            "Phiếu #" + session.getReceivingId(),
                            "QC userId=" + session.getCreatedBy() + " bắt đầu kiểm định"
                    );
                } catch (Exception ignored) { /* WS failure không block scan */ }
            }

        } else if (!session.getAssignedQcId().equals(session.getCreatedBy())) {
            log.warn("[Security] Session {} bị QC userId={} claim. Từ chối userId={}.",
                    sessionId, session.getAssignedQcId(), session.getCreatedBy());
            return ApiResponse.error("Phiên scan này đang được QC khác sử dụng.");
        }
        return null;
    }

    /** Atomic UPDATE receivedQty — thread-safe, không read-modify-write. */
    private void atomicIncrementReceivingItem(ScanEventRequest request, SkuEntity sku, String condition) {
        try {
            Long receivingId = request.getReceivingId();
            ReceivingOrderEntity order = receivingOrderRepo.findById(receivingId)
                    .orElseThrow(() -> new RuntimeException("Order not found: " + receivingId));
            String status = order.getStatus() != null ? order.getStatus().toUpperCase() : "DRAFT";
            if ("POSTED".equals(status) || "PUTAWAY_DONE".equals(status)
                    || "CANCELLED".equals(status) || "REJECTED".equals(status)) return;

            BigDecimal inc = request.getQty() != null ? request.getQty() : BigDecimal.ONE;
            int affected = receivingItemRepo.incrementReceivedQty(receivingId, sku.getSkuId(), inc, request.getLotNumber());

            if (affected > 0) {
                log.info("[ATOMIC] +{} receivedQty receivingId={} SKU={}", inc, receivingId, sku.getSkuCode());
                if ("FAIL".equals(condition)) {
                    receivingItemRepo.findByReceivingOrderReceivingIdAndSkuId(receivingId, sku.getSkuId())
                            .stream().filter(item -> 
                                (request.getLotNumber() == null ? item.getLotNumber() == null : request.getLotNumber().equals(item.getLotNumber()))
                            ).findFirst()
                            .ifPresent(item -> {
                                item.setCondition("FAIL");
                                item.setQcRequired(true);
                                if (request.getReasonCode() != null && !request.getReasonCode().isBlank())
                                    item.setReasonCode(request.getReasonCode());
                                receivingItemRepo.save(item);
                            });
                }
            } else {
                log.warn("ReceivingItem không tìm thấy: receivingId={} skuId={}", receivingId, sku.getSkuId());
            }
        } catch (Exception e) {
            log.warn("atomicIncrementReceivingItem failed receivingId={} sku={}: {}",
                    request.getReceivingId(), sku.getSkuCode(), e.getMessage());
        }
    }

    /** Cập nhật lines trong ScanSessionData. Trả về newQty. */
    private BigDecimal updateSessionLines(ScanSessionData session, SkuEntity sku,
                                          String condition, ScanEventRequest request) {
        List<ScanLineItem> lines = session.getLines();
        Optional<ScanLineItem> existing = lines.stream()
                .filter(l -> l.getSkuId().equals(sku.getSkuId()) && condition.equals(l.getCondition()))
                .findFirst();

        BigDecimal inc = request.getQty() != null ? request.getQty() : BigDecimal.ONE;
        BigDecimal newQty;
        if (existing.isPresent()) {
            ScanLineItem line = existing.get();
            newQty = line.getQty().add(inc);
            line.setQty(newQty);
            if (request.getReasonCode() != null) line.setReasonCode(request.getReasonCode());
            if (request.getAttachmentUrl() != null && !request.getAttachmentUrl().isBlank())
                line.setAttachmentUrl(mergePhotoUrls(line.getAttachmentUrl(), request.getAttachmentUrl()));
        } else {
            newQty = inc;
            lines.add(ScanLineItem.builder()
                    .skuId(sku.getSkuId()).skuCode(sku.getSkuCode())
                    .skuName(sku.getSkuName()).barcode(sku.getBarcode())
                    .qty(newQty).condition(condition)
                    .reasonCode(request.getReasonCode())
                    .attachmentUrl(request.getAttachmentUrl())
                    .build());
        }
        return newQty;
    }

    /** Push SSE cho outbound QC. */
    private void pushOutboundQcSse(String sessionId, Long taskId, SkuEntity sku,
                                   String condition, PickingTaskItemEntity taskItem) {
        if (sessionId == null) return;
        try {
            if (sessionRedis.findById(sessionId).isEmpty()) return;
            var all = pickingTaskItemRepository.findByPickingTaskId(taskId);
            long pending = all.stream().filter(i ->
                    safeQty(i.getQcPassQty()).add(safeQty(i.getQcFailQty()))
                            .compareTo(i.getRequiredQty()) < 0).count();
            long passCnt = all.stream().filter(i ->
                    safeQty(i.getQcFailQty()).compareTo(BigDecimal.ZERO) == 0
                            && safeQty(i.getQcPassQty()).compareTo(BigDecimal.ZERO) > 0).count();
            long failCnt = all.stream().filter(i ->
                    safeQty(i.getQcFailQty()).compareTo(BigDecimal.ZERO) > 0).count();

            Map<String, Object> p = new HashMap<>();
            p.put("type",         "qc_scan");
            p.put("skuCode",      sku.getSkuCode() != null ? sku.getSkuCode() : "");
            p.put("skuName",      sku.getSkuName() != null ? sku.getSkuName() : "");
            p.put("result",       condition);
            p.put("taskItemId",   taskItem.getPickingTaskItemId());
            p.put("passQty",      safeQty(taskItem.getQcPassQty()));
            p.put("failQty",      safeQty(taskItem.getQcFailQty()));
            p.put("allScanned",   pending == 0 && !all.isEmpty());
            p.put("passCount",    passCnt);
            p.put("failCount",    failCnt);
            p.put("pendingCount", pending);
            sseRegistry.send(sessionId, p);
        } catch (Exception ignored) {}
    }

    private BigDecimal safeQty(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    private String mergePhotoUrls(String existing, String incoming) {
        List<String> urls = new ArrayList<>();
        for (String src : new String[]{existing, incoming}) {
            if (src == null || src.isBlank()) continue;
            if (src.trim().startsWith("[")) {
                try { urls.addAll(objectMapper.readValue(src, new TypeReference<List<String>>() {})); }
                catch (Exception e) { urls.add(src); }
            } else { urls.add(src); }
        }
        List<String> deduped = urls.stream().distinct().limit(5).toList();
        try { return objectMapper.writeValueAsString(deduped); }
        catch (Exception e) { return deduped.isEmpty() ? null : deduped.get(0); }
    }
}