package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.request.CreateGrnRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.ScanSessionResponse;
import org.example.sep26management.application.dto.scan.ScanLineItem;
import org.example.sep26management.application.dto.scan.ScanSessionData;
import org.example.sep26management.infrastructure.SseEmitterRegistry;
import org.example.sep26management.infrastructure.persistence.entity.ReceivingItemEntity;
import org.example.sep26management.infrastructure.persistence.entity.ReceivingOrderEntity;
import org.example.sep26management.infrastructure.persistence.entity.SupplierEntity;
import org.example.sep26management.infrastructure.persistence.redis.ScanSessionRedisRepository;
import org.example.sep26management.infrastructure.persistence.repository.ReceivingItemJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.ReceivingOrderJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.SupplierJpaRepository;
import org.example.sep26management.application.service.NotificationService;
import org.example.sep26management.infrastructure.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceivingSessionService {

        private final ScanSessionRedisRepository    sessionRedis;
        private final JwtTokenProvider              jwtTokenProvider;
        private final SseEmitterRegistry            sseRegistry;
        private final ReceivingOrderJpaRepository   receivingOrderRepo;
        private final ReceivingItemJpaRepository    receivingItemRepo;
        private final SupplierJpaRepository         supplierRepo;
        private final NotificationService           notificationService;

        @Value("${app.base-url:http://localhost:8080/api}")
        private String baseUrl;

        // ─── Create session ───────────────────────────────────────────────────────
        //
        // receivingId : phiếu nhận hàng mà QR này phục vụ (null = outbound/legacy)
        // role        : "KEEPER" hoặc "QC" — lưu vào session để ScanEventService validate
        //
        // Hai caller:
        //   1. ReceivingSessionController  — inbound Keeper/QC, truyền receivingId thật
        //   2. OutboundController          — outbound picking,  truyền null

        public ApiResponse<ScanSessionResponse> createSession(
                Long warehouseId, Long userId, Long receivingId, String role) {

                // Kiểm tra session đang hoạt động cho user này
                Optional<String> activeOpt = sessionRedis.findActiveSession(warehouseId, userId);
                if (activeOpt.isPresent()) {
                        String existingId = activeOpt.get();
                        Optional<ScanSessionData> existingDataOpt = sessionRedis.findById(existingId);
                        if (existingDataOpt.isPresent()) {
                                log.info("Reusing scan session: {} userId={} warehouseId={} — reset lines, rebind receivingId={}",
                                        existingId, userId, warehouseId, receivingId);
                                ScanSessionData data = existingDataOpt.get();
                                // Reset lines + rebind phiếu + role + xóa QC claim cũ
                                Long oldReceivingId = data.getReceivingId();
                                data.setLines(new ArrayList<>());
                                data.setReceivingId(receivingId);
                                data.setRole(role);
                                data.setAssignedQcId(null);
                                // Release claim cũ nếu đang giữ phiếu khác
                                if ("QC".equals(role) && oldReceivingId != null
                                        && !oldReceivingId.equals(receivingId)) {
                                        receivingOrderRepo.releaseQcAssignment(oldReceivingId, userId);
                                        log.info("[QCClaim] Released old claim receivingId={} userId={}", oldReceivingId, userId);
                                }
                                sessionRedis.save(existingId, data);
                                return ApiResponse.success("Reused existing session (lines cleared)",
                                        toResponse(data));
                        } else {
                                // Key active tồn tại nhưng data đã hết hạn → dọn key
                                sessionRedis.deleteActiveSession(warehouseId, userId);
                        }
                }

                // Tạo session mới
                String sessionId = "RS_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                ScanSessionData data = ScanSessionData.builder()
                        .sessionId(sessionId)
                        .warehouseId(warehouseId)
                        .createdBy(userId)
                        .receivingId(receivingId)   // null nếu outbound
                        .role(role)                  // "KEEPER" | "QC" | null (legacy)
                        .lines(new ArrayList<>())
                        .build();

                sessionRedis.save(sessionId, data);
                sessionRedis.saveActiveSession(warehouseId, userId, sessionId);
                log.info("Scan session created: {} userId={} warehouseId={} receivingId={} role={}",
                        sessionId, userId, warehouseId, receivingId, role);

                // QC claim ngay khi tạo session — không đợi scan barcode đầu tiên.
                // Như vậy QC B thấy nút bị lock ngay sau khi QC A bấm "QC Scan" và tạo QR.
                if ("QC".equals(role) && receivingId != null) {
                        int claimed = receivingOrderRepo.claimQcAssignment(receivingId, userId);
                        if (claimed > 0) {
                                data.setAssignedQcId(userId);
                                sessionRedis.save(sessionId, data);
                                log.info("[QCClaim] QC userId={} pre-claimed receivingId={} at session create", userId, receivingId);
                                // Push WS → QC khác reload danh sách ngay, thấy nút lock
                                try {
                                        notificationService.notifyRoles(
                                                new String[]{"QC", "MANAGER"},
                                                "qc_claimed",
                                                receivingId,
                                                "Phiếu #" + receivingId,
                                                "QC userId=" + userId + " bắt đầu kiểm định"
                                        );
                                } catch (Exception ignored) {}
                        } else {
                                // Ai đó đã claim trước — từ chối tạo session
                                sessionRedis.deleteActiveSession(warehouseId, userId);
                                sessionRedis.delete(sessionId);
                                log.warn("[QCClaim] Phiếu #{} đã bị QC khác claim. Từ chối userId={}", receivingId, userId);
                                return ApiResponse.error(
                                        "Phiếu #" + receivingId + " đang được QC khác kiểm định. Vui lòng chờ hoặc liên hệ quản lý.");
                        }
                }

                return ApiResponse.success("Session created", toResponse(data));
        }

        // ─── Generate scan token ──────────────────────────────────────────────────

        public ApiResponse<Map<String, String>> generateScanToken(
                String sessionId, Long userId, String role) {

                ScanSessionData session = sessionRedis.findById(sessionId)
                        .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

                String token   = jwtTokenProvider.generateScanToken(sessionId, session.getWarehouseId(), role, userId);
                String scanUrl = baseUrl + "/v1/scan?token=" + token;

                log.info("Scan token generated: session={} userId={} role={}", sessionId, userId, role);

                return ApiResponse.success("Scan token generated", Map.of(
                        "sessionId", sessionId,
                        "scanToken", token,
                        "scanUrl",   scanUrl));
        }

        // ─── Get session snapshot ─────────────────────────────────────────────────

        public ApiResponse<ScanSessionResponse> getSession(String sessionId) {
                ScanSessionData data = sessionRedis.findById(sessionId)
                        .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));
                return ApiResponse.success("OK", toResponse(data));
        }

        // ─── SSE stream ───────────────────────────────────────────────────────────

        public SseEmitter stream(String sessionId) {
                sessionRedis.findById(sessionId)
                        .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

                SseEmitter emitter = new SseEmitter(600_000L); // 10 phút
                sseRegistry.register(sessionId, emitter);

                // Gửi snapshot hiện tại ngay khi connect
                sessionRedis.findById(sessionId).ifPresent(data -> sseRegistry.send(sessionId, data));

                return emitter;
        }

        // ─── Delete session ───────────────────────────────────────────────────────

        public ApiResponse<Void> deleteSession(String sessionId) {
                sessionRedis.findById(sessionId).ifPresent(session ->
                        sessionRedis.deleteActiveSession(session.getWarehouseId(), session.getCreatedBy()));
                sessionRedis.delete(sessionId);
                sseRegistry.remove(sessionId);
                log.info("Scan session deleted: {}", sessionId);
                return ApiResponse.success("Session deleted", null);
        }

        // ─── Create GRN from session ──────────────────────────────────────────────

        @Transactional
        public ApiResponse<Map<String, Object>> createGrn(
                String sessionId, CreateGrnRequest request, Long userId) {

                ScanSessionData session = sessionRedis.findById(sessionId)
                        .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

                List<ScanLineItem> lines = session.getLines();
                if (lines == null || lines.isEmpty()) {
                        return ApiResponse.error("Chưa có sản phẩm nào được scan trong phiên này");
                }

                // Resolve supplierId
                Long supplierId = null;
                if (request.getSupplierCode() != null && !request.getSupplierCode().isBlank()) {
                        supplierId = supplierRepo.findBySupplierCode(request.getSupplierCode())
                                .map(SupplierEntity::getSupplierId)
                                .orElseThrow(() -> new RuntimeException(
                                        "Supplier not found: " + request.getSupplierCode()));
                }

                Long effectiveUserId = (userId != null) ? userId : session.getCreatedBy();
                String receivingCode = "GRN" + System.currentTimeMillis() % 1_000_000;

                ReceivingOrderEntity order = ReceivingOrderEntity.builder()
                        .warehouseId(session.getWarehouseId())
                        .receivingCode(receivingCode)
                        .status("DRAFT")
                        .sourceType(request.getSourceType())
                        .supplierId(supplierId)
                        .sourceReferenceCode(request.getSourceReferenceCode())
                        .note(request.getNote())
                        .createdBy(effectiveUserId)
                        .build();

                ReceivingOrderEntity saved = receivingOrderRepo.save(order);

                List<ReceivingItemEntity> items = new ArrayList<>();
                for (ScanLineItem line : lines) {
                        boolean isFail = "FAIL".equalsIgnoreCase(line.getCondition());
                        items.add(ReceivingItemEntity.builder()
                                .receivingOrder(saved)
                                .skuId(line.getSkuId())
                                .receivedQty(line.getQty())
                                .lotNumber(request.getLotNumber())
                                .expiryDate(request.getExpiryDate())
                                .manufactureDate(request.getManufactureDate())
                                .condition(line.getCondition() != null ? line.getCondition() : "PASS")
                                .reasonCode(line.getReasonCode())
                                .qcRequired(isFail)
                                .build());
                }
                receivingItemRepo.saveAll(items);

                // Dọn dẹp session
                sessionRedis.deleteActiveSession(session.getWarehouseId(), session.getCreatedBy());
                sessionRedis.delete(sessionId);
                sseRegistry.remove(sessionId);

                log.info("GRN created: {} (receivingId={}) from session {}", receivingCode, saved.getReceivingId(), sessionId);

                return ApiResponse.success("GRN created successfully", Map.of(
                        "receivingId",   saved.getReceivingId(),
                        "receivingCode", saved.getReceivingCode(),
                        "status",        saved.getStatus(),
                        "itemCount",     items.size()));
        }

        // ─── Helper ───────────────────────────────────────────────────────────────

        private ScanSessionResponse toResponse(ScanSessionData data) {
                return ScanSessionResponse.builder()
                        .sessionId(data.getSessionId())
                        .warehouseId(data.getWarehouseId())
                        .lines(data.getLines() != null ? data.getLines() : new ArrayList<>())
                        .build();
        }
}