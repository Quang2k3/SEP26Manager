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

        private final ScanSessionRedisRepository sessionRedis;
        private final JwtTokenProvider jwtTokenProvider;
        private final SseEmitterRegistry sseRegistry;
        private final ReceivingOrderJpaRepository receivingOrderRepo;
        private final ReceivingItemJpaRepository receivingItemRepo;
        private final SupplierJpaRepository supplierRepo;

        @Value("${app.base-url:http://localhost:8080/api}")
        private String baseUrl;

        // ─── Create session ───────────────────────────────────────────────────────

        public ApiResponse<ScanSessionResponse> createSession(Long warehouseId, Long userId) {
                // Check if an active session already exists for this user in this warehouse
                Optional<String> activeSessionOpt = sessionRedis.findActiveSession(warehouseId, userId);
                if (activeSessionOpt.isPresent()) {
                        String existingSessionId = activeSessionOpt.get();
                        // Verify the session data actually still exists in Redis
                        Optional<ScanSessionData> sessionDataOpt = sessionRedis.findById(existingSessionId);
                        if (sessionDataOpt.isPresent()) {
                                log.info("Reusing existing scan session: {} for userId={}, warehouseId={}",
                                                existingSessionId, userId, warehouseId);
                                ScanSessionData data = sessionDataOpt.get();
                                ScanSessionResponse response = ScanSessionResponse.builder()
                                                .sessionId(data.getSessionId())
                                                .warehouseId(data.getWarehouseId())
                                                .lines(data.getLines() != null ? data.getLines() : new ArrayList<>())
                                                .build();
                                return ApiResponse.success("Reused existing session", response);
                        } else {
                                // Active session key exists but data is gone (expired/deleted), clean up the
                                // stale key
                                sessionRedis.deleteActiveSession(warehouseId, userId);
                        }
                }

                String sessionId = "RS_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

                ScanSessionData data = ScanSessionData.builder()
                                .sessionId(sessionId)
                                .warehouseId(warehouseId)
                                .createdBy(userId)
                                .lines(new ArrayList<>())
                                .build();

                sessionRedis.save(sessionId, data);
                sessionRedis.saveActiveSession(warehouseId, userId, sessionId);
                log.info("Scan session created: {} by userId={}, warehouseId={}", sessionId, userId, warehouseId);

                ScanSessionResponse response = ScanSessionResponse.builder()
                                .sessionId(sessionId)
                                .warehouseId(warehouseId)
                                .lines(new ArrayList<>())
                                .build();

                return ApiResponse.success("Session created", response);
        }

        // ─── Generate scan token ──────────────────────────────────────────────────

        public ApiResponse<Map<String, String>> generateScanToken(String sessionId, Long userId, String role) {
                ScanSessionData session = sessionRedis.findById(sessionId)
                                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

                String token = jwtTokenProvider.generateScanToken(sessionId, session.getWarehouseId(), role, userId);
                String scanUrl = baseUrl + "/v1/scan?token=" + token;

                log.info("Scan token generated for session {} by userId={}, role={}", sessionId, userId, role);

                return ApiResponse.success("Scan token generated", Map.of(
                                "sessionId", sessionId,
                                "scanToken", token,
                                "scanUrl", scanUrl));
        }

        // ─── Get session snapshot ─────────────────────────────────────────────────

        public ApiResponse<ScanSessionResponse> getSession(String sessionId) {
                ScanSessionData data = sessionRedis.findById(sessionId)
                                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

                ScanSessionResponse response = ScanSessionResponse.builder()
                                .sessionId(data.getSessionId())
                                .warehouseId(data.getWarehouseId())
                                .lines(data.getLines())
                                .build();

                return ApiResponse.success("OK", response);
        }

        // ─── SSE stream ──────────────────────────────────────────────────────────

        public SseEmitter stream(String sessionId) {
                // Validate session exists
                sessionRedis.findById(sessionId)
                                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

                SseEmitter emitter = new SseEmitter(600_000L); // 10 min timeout
                sseRegistry.register(sessionId, emitter);

                // Send initial snapshot immediately
                ScanSessionData data = sessionRedis.findById(sessionId).orElse(null);
                if (data != null) {
                        sseRegistry.send(sessionId, data);
                }

                return emitter;
        }

        // ─── Delete session ───────────────────────────────────────────────────────

        public ApiResponse<Void> deleteSession(String sessionId) {
                sessionRedis.findById(sessionId).ifPresent(session -> {
                        sessionRedis.deleteActiveSession(session.getWarehouseId(), session.getCreatedBy());
                });
                sessionRedis.delete(sessionId);
                sseRegistry.remove(sessionId);
                log.info("Scan session deleted: {}", sessionId);
                return ApiResponse.success("Session deleted", null);
        }

        // ─── Create GRN from session ──────────────────────────────────────────────

        @Transactional
        public ApiResponse<Map<String, Object>> createGrn(String sessionId, CreateGrnRequest request, Long userId) {
                ScanSessionData session = sessionRedis.findById(sessionId)
                                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

                List<ScanLineItem> lines = session.getLines();
                if (lines == null || lines.isEmpty()) {
                        return ApiResponse.error("No items scanned in this session");
                }

                // Generate receiving code: GRN + timestamp suffix
                String receivingCode = "GRN" + System.currentTimeMillis() % 1_000_000;

                // Resolve supplierId from supplierCode if provided
                Long supplierId = null;
                if (request.getSupplierCode() != null && !request.getSupplierCode().isBlank()) {
                        supplierId = supplierRepo.findBySupplierCode(request.getSupplierCode())
                                        .map(SupplierEntity::getSupplierId)
                                        .orElseThrow(() -> new RuntimeException(
                                                        "Supplier not found with code: " + request.getSupplierCode()));
                }

                // If called from scanner page, userId will be null. Use the session creator.
                Long effectiveUserId = (userId != null) ? userId : session.getCreatedBy();

                ReceivingOrderEntity order = ReceivingOrderEntity.builder()
                                .warehouseId(session.getWarehouseId()) // taken from session (set from JWT at creation)
                                .receivingCode(receivingCode)
                                .status("DRAFT")
                                .sourceType(request.getSourceType())
                                .supplierId(supplierId)
                                .sourceReferenceCode(request.getSourceReferenceCode())
                                .note(request.getNote())
                                .createdBy(effectiveUserId)
                                .build();

                ReceivingOrderEntity saved = receivingOrderRepo.save(order);

                // Create line items from session lines
                List<ReceivingItemEntity> items = new ArrayList<>();
                for (ScanLineItem line : lines) {
                        boolean isFail = "FAIL".equalsIgnoreCase(line.getCondition());
                        ReceivingItemEntity item = ReceivingItemEntity.builder()
                                        .receivingOrder(saved)
                                        .skuId(line.getSkuId())
                                        .receivedQty(line.getQty())
                                        .lotNumber(request.getLotNumber())
                                        .expiryDate(request.getExpiryDate())
                                        .manufactureDate(request.getManufactureDate())
                                        .condition(line.getCondition() != null ? line.getCondition() : "PASS")
                                        .reasonCode(line.getReasonCode())
                                        .qcRequired(isFail) // FAIL items automatically need QC
                                        .build();
                        items.add(item);
                }
                receivingItemRepo.saveAll(items);

                // Clean up session after GRN created
                sessionRedis.deleteActiveSession(session.getWarehouseId(), session.getCreatedBy());
                sessionRedis.delete(sessionId);
                sseRegistry.remove(sessionId);

                log.info("GRN created: {} (receivingId={}) from session {}", receivingCode, saved.getReceivingId(),
                                sessionId);

                return ApiResponse.success("GRN created successfully", Map.of(
                                "receivingId", saved.getReceivingId(),
                                "receivingCode", saved.getReceivingCode(),
                                "status", saved.getStatus(),
                                "itemCount", items.size()));
        }
}