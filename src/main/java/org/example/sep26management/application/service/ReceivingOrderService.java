package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.PageResponse;

import org.example.sep26management.application.dto.response.ReceivingItemResponse;
import org.example.sep26management.application.dto.response.ReceivingOrderResponse;
import org.example.sep26management.infrastructure.persistence.entity.*;
import org.example.sep26management.infrastructure.persistence.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.example.sep26management.application.dto.scan.ScanLineItem;
import org.example.sep26management.application.dto.scan.ScanSessionData;
import org.example.sep26management.infrastructure.persistence.redis.ScanSessionRedisRepository;
import org.example.sep26management.infrastructure.SseEmitterRegistry;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceivingOrderService {

        private final ReceivingOrderJpaRepository receivingOrderRepo;
        private final ReceivingItemJpaRepository receivingItemRepo;
        private final PutawayTaskJpaRepository putawayTaskRepo;
        private final PutawayTaskItemJpaRepository putawayTaskItemRepo;
        private final SkuJpaRepository skuRepo;
        private final WarehouseJpaRepository warehouseRepo;
        private final SupplierJpaRepository supplierRepo;
        private final UserJpaRepository userRepo;
        private final IncidentJpaRepository incidentRepo;
        private final IncidentItemJpaRepository incidentItemRepo;
        private final GrnJpaRepository grnRepo;
        private final GrnItemJpaRepository grnItemRepo;
        private final JdbcTemplate jdbcTemplate; // Chỉ dùng cho các native INSERT/UPDATE phức tạp (inventory)
        private final PutawaySuggestionService putawaySuggestionService;
        private final ScanSessionRedisRepository sessionRedis;
        private final SseEmitterRegistry sseRegistry;
        @org.springframework.context.annotation.Lazy
        private final GrnService grnService;
        private final AuditLogService auditLogService;

        // ─── List ──────────────────────────────────────────────────────────────────

        @Transactional(readOnly = true)
        public ApiResponse<PageResponse<ReceivingOrderResponse>> listOrders(String status, int page, int size) {
                Pageable pageable = PageRequest.of(page, size);

                Page<ReceivingOrderEntity> ordersPage = status != null && !status.isBlank()
                        ? receivingOrderRepo.findByStatusOrderByCreatedAtDesc(status, pageable)
                        : receivingOrderRepo.findAllByOrderByCreatedAtDesc(pageable);

                List<ReceivingOrderResponse> content = ordersPage.getContent().stream()
                        .map(o -> toSummaryResponse(o))
                        .collect(Collectors.toList());

                PageResponse<ReceivingOrderResponse> pageResponse = PageResponse.<ReceivingOrderResponse>builder()
                        .content(content)
                        .page(ordersPage.getNumber())
                        .size(ordersPage.getSize())
                        .totalElements(ordersPage.getTotalElements())
                        .totalPages(ordersPage.getTotalPages())
                        .last(ordersPage.isLast())
                        .build();

                return ApiResponse.success("OK", pageResponse);
        }

        // ─── Create Draft Order ────────────────────────────────────────────────────

        @Transactional
        public ApiResponse<ReceivingOrderResponse> createDraftOrder(
                org.example.sep26management.application.dto.request.ReceivingOrderRequest request,
                Long warehouseId, Long userId) {

                Long supplierId = null;
                if (request.getSupplierCode() != null && !request.getSupplierCode().isBlank()) {
                        supplierId = supplierRepo.findBySupplierCode(request.getSupplierCode())
                                .map(SupplierEntity::getSupplierId)
                                .orElseThrow(() -> new RuntimeException(
                                        "Supplier not found: " + request.getSupplierCode()));
                }

                String receivingCode = "GRN-" + System.currentTimeMillis();

                ReceivingOrderEntity order = ReceivingOrderEntity.builder()
                        .warehouseId(warehouseId)
                        .sourceType(request.getSourceType())
                        .sourceReferenceCode(request.getSourceReferenceCode())
                        .supplierId(supplierId)
                        .note(request.getNote())
                        .status("DRAFT")
                        .createdBy(userId)
                        .receivingCode(receivingCode)
                        .build();

                ReceivingOrderEntity savedOrder = receivingOrderRepo.save(order);

                if (request.getItems() != null && !request.getItems().isEmpty()) {
                        for (var itemReq : request.getItems()) {
                                SkuEntity sku = skuRepo.findBySkuCode(itemReq.getSkuCode())
                                        .orElseThrow(() -> new RuntimeException(
                                                "SKU not found for code: " + itemReq.getSkuCode()));

                                ReceivingItemEntity item = ReceivingItemEntity.builder()
                                        .receivingOrder(savedOrder)
                                        .skuId(sku.getSkuId())
                                        .expectedQty(itemReq.getExpectedQty() != null ? itemReq.getExpectedQty()
                                                : BigDecimal.ZERO)
                                        .receivedQty(BigDecimal.ZERO)
                                        .lotNumber(itemReq.getLotNumber())
                                        .manufactureDate(itemReq.getManufactureDate())
                                        .expiryDate(itemReq.getExpiryDate())
                                        .build();
                                receivingItemRepo.save(item);
                        }
                }

                log.info("Draft GRN {} created by userId={}", receivingCode, userId);
                return getOrder(savedOrder.getReceivingId());
        }

        // ─── Update Draft Order ───────────────────────────────────────────────────

        @Transactional
        public ApiResponse<ReceivingOrderResponse> updateDraftOrder(Long id,
                                                                    org.example.sep26management.application.dto.request.ReceivingOrderRequest request,
                                                                    Long userId) {
                ReceivingOrderEntity order = findOrder(id);
                if (!"DRAFT".equals(order.getStatus())) {
                        throw new org.example.sep26management.infrastructure.exception.BusinessException(
                                "Cannot update: only allowed in DRAFT status. Current status: '" + order.getStatus() + "'");
                }

                // Update header fields
                if (request.getSourceType() != null) {
                        order.setSourceType(request.getSourceType());
                }
                if (request.getSourceReferenceCode() != null) {
                        order.setSourceReferenceCode(request.getSourceReferenceCode());
                }
                if (request.getNote() != null) {
                        order.setNote(request.getNote());
                }
                if (request.getSupplierCode() != null && !request.getSupplierCode().isBlank()) {
                        Long supplierId = supplierRepo.findBySupplierCode(request.getSupplierCode())
                                .map(SupplierEntity::getSupplierId)
                                .orElseThrow(() -> new RuntimeException(
                                        "Supplier not found: " + request.getSupplierCode()));
                        order.setSupplierId(supplierId);
                }

                // Replace items if provided
                if (request.getItems() != null && !request.getItems().isEmpty()) {
                        // Delete existing items
                        List<ReceivingItemEntity> existingItems = receivingItemRepo.findByReceivingOrderReceivingId(id);
                        receivingItemRepo.deleteAll(existingItems);

                        // Create new items
                        for (var itemReq : request.getItems()) {
                                SkuEntity sku = skuRepo.findBySkuCode(itemReq.getSkuCode())
                                        .orElseThrow(() -> new RuntimeException(
                                                "SKU not found for code: " + itemReq.getSkuCode()));

                                ReceivingItemEntity item = ReceivingItemEntity.builder()
                                        .receivingOrder(order)
                                        .skuId(sku.getSkuId())
                                        .expectedQty(itemReq.getExpectedQty() != null ? itemReq.getExpectedQty()
                                                : BigDecimal.ZERO)
                                        .receivedQty(BigDecimal.ZERO)
                                        .lotNumber(itemReq.getLotNumber())
                                        .manufactureDate(itemReq.getManufactureDate())
                                        .expiryDate(itemReq.getExpiryDate())
                                        .build();
                                receivingItemRepo.save(item);
                        }
                }

                order.setUpdatedAt(LocalDateTime.now());
                receivingOrderRepo.save(order);

                log.info("Draft GRN {} updated by userId={}", order.getReceivingCode(), userId);
                return getOrder(id);
        }

        // ─── Delete Draft Order ───────────────────────────────────────────────────

        @Transactional
        public ApiResponse<Void> deleteDraftOrder(Long id, Long userId) {
                ReceivingOrderEntity order = findOrder(id);
                if (!"DRAFT".equals(order.getStatus())) {
                        throw new org.example.sep26management.infrastructure.exception.BusinessException(
                                "Cannot delete: only allowed in DRAFT status. Current status: '" + order.getStatus() + "'");
                }

                // Delete items first
                List<ReceivingItemEntity> items = receivingItemRepo.findByReceivingOrderReceivingId(id);
                receivingItemRepo.deleteAll(items);

                // Delete order
                receivingOrderRepo.delete(order);

                log.info("Draft GRN {} deleted by userId={}", order.getReceivingCode(), userId);
                return ApiResponse.success("Draft order deleted successfully", null);
        }

        // ─── Update Lines ──────────────────────────────────────────────────────────

        @Transactional
        public ApiResponse<ReceivingOrderResponse> updateLines(Long id,
                                                               org.example.sep26management.application.dto.request.UpdateReceivingLinesRequest request,
                                                               Long userId) {
                ReceivingOrderEntity order = findOrder(id);

                if (!"DRAFT".equals(order.getStatus())) {
                        throw new RuntimeException(
                                "Cannot update lines: only allowed in DRAFT status. Current status: '" + order.getStatus() + "'");
                }

                if (request.getLines() != null) {
                        for (var line : request.getLines()) {
                                ReceivingItemEntity item = receivingItemRepo.findById(line.getReceivingItemId())
                                        .orElseThrow(() -> new RuntimeException(
                                                "Item not found: " + line.getReceivingItemId()));

                                if (!item.getReceivingOrder().getReceivingId().equals(id)) {
                                        throw new RuntimeException(
                                                "Item " + line.getReceivingItemId() + " does not belong to GRN "
                                                        + id);
                                }

                                if (line.getReceivedQty() != null)
                                        item.setReceivedQty(line.getReceivedQty());
                                if (line.getNote() != null)
                                        item.setNote(line.getNote());
                                if (line.getLotNumber() != null)
                                        item.setLotNumber(line.getLotNumber());
                                if (line.getManufactureDate() != null)
                                        item.setManufactureDate(line.getManufactureDate());
                                if (line.getExpiryDate() != null)
                                        item.setExpiryDate(line.getExpiryDate());

                                receivingItemRepo.save(item);
                        }
                }

                order.setUpdatedAt(LocalDateTime.now());
                receivingOrderRepo.save(order);

                log.info("GRN {} lines updated by userId={}", order.getReceivingCode(), userId);
                return getOrder(id);
        }

        // ─── Get (với enriched fields đầy đủ) ─────────────────────────────────────

        @Transactional(readOnly = true)
        public ApiResponse<ReceivingOrderResponse> getOrder(Long id) {
                ReceivingOrderEntity order = findOrder(id);
                List<ReceivingItemEntity> items = receivingItemRepo.findByReceivingOrderReceivingId(id);

                // Batch-load SKUs để tránh N+1 query
                List<Long> skuIds = items.stream().map(ReceivingItemEntity::getSkuId).collect(Collectors.toList());
                Map<Long, SkuEntity> skuMap = skuRepo.findAllById(skuIds).stream()
                        .collect(Collectors.toMap(SkuEntity::getSkuId, s -> s));

                // Lookup từ repo — KHÔNG query thẳng trong service
                String warehouseName = warehouseRepo.findById(order.getWarehouseId())
                        .map(WarehouseEntity::getWarehouseName).orElse(null);

                String supplierName = order.getSupplierId() != null
                        ? supplierRepo.findById(order.getSupplierId()).map(SupplierEntity::getSupplierName)
                        .orElse(null)
                        : null;

                String createdByName = order.getCreatedBy() != null
                        ? userRepo.findById(order.getCreatedBy()).map(UserEntity::getFullName).orElse(null)
                        : null;

                // Map items
                List<ReceivingItemResponse> itemResponses = items.stream()
                        .map(item -> toItemResponse(item, skuMap))
                        .collect(Collectors.toList());

                int totalLines = itemResponses.size();
                BigDecimal totalExpectedQty = items.stream()
                        .map(ReceivingItemEntity::getExpectedQty)
                        .filter(qty -> qty != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal totalQty = items.stream()
                        .map(ReceivingItemEntity::getReceivedQty)
                        .filter(qty -> qty != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                String approvedByName = order.getApprovedBy() != null
                        ? userRepo.findById(order.getApprovedBy()).map(UserEntity::getFullName).orElse(null)
                        : null;

                String rejectedByName = order.getRejectedBy() != null
                        ? userRepo.findById(order.getRejectedBy()).map(UserEntity::getFullName).orElse(null)
                        : null;

                ReceivingOrderResponse response = ReceivingOrderResponse.builder()
                        .receivingId(order.getReceivingId())
                        .receivingCode(order.getReceivingCode())
                        .status(order.getStatus())
                        .warehouseId(order.getWarehouseId())
                        .warehouseName(warehouseName)
                        .supplierId(order.getSupplierId())
                        .supplierName(supplierName)
                        .sourceType(order.getSourceType())
                        .sourceReferenceCode(order.getSourceReferenceCode())
                        .note(order.getNote())
                        .createdBy(order.getCreatedBy())
                        .createdByName(createdByName)
                        .createdAt(order.getCreatedAt())
                        .updatedAt(order.getUpdatedAt())
                        .approvedBy(order.getApprovedBy())
                        .approvedByName(approvedByName)
                        .approvedAt(order.getApprovedAt())
                        .rejectedBy(order.getRejectedBy())
                        .rejectedByName(rejectedByName)
                        .rejectedAt(order.getRejectedAt())
                        .rejectReason(order.getRejectReason())
                        .totalLines(totalLines)
                        .totalQty(totalQty)
                        .totalExpectedQty(totalExpectedQty)
                        .items(itemResponses)
                        .build();

                return ApiResponse.success("OK", response);
        }

        // ─── Submit (DRAFT → SUBMITTED) ──────────────────────────────────────────
        // Keeper bấm "Submit" trên desktop → đơn chuyển sang SUBMITTED.
        // Ở trạng thái SUBMITTED, Keeper mở modal Quét QR trên desktop,
        // dùng điện thoại scan barcode hàng hoá, rồi bấm "Xác nhận gửi QC" trên phone
        // → gọi /finalize-count → SUBMITTED → PENDING_COUNT (chờ QC kiểm đếm).

        @Transactional
        public ApiResponse<ReceivingOrderResponse> submit(Long id, Long userId) {
                ReceivingOrderEntity order = findOrder(id);
                validateStatus(order, "submit", "DRAFT");

                order.setStatus("SUBMITTED");
                order.setUpdatedAt(LocalDateTime.now());
                receivingOrderRepo.save(order);

                log.info("Receiving Order {} submitted (DRAFT → SUBMITTED) by userId={}",
                        order.getReceivingCode(), userId);
                return ApiResponse.success("Submitted successfully. Status: SUBMITTED. Keeper can now scan QR.",
                        toSummaryResponse(order));
        }

        // ─── Finalize Count (SUBMITTED → PENDING_COUNT) ──────────────────────────
        // Keeper bấm "Xác nhận gửi QC" trên phone (sau khi scan xong) →
        // đơn chuyển SUBMITTED → PENDING_COUNT (chờ QC kiểm đếm).
        // Đây cũng là lúc cộng tồn vào staging (Z-INB).

        @Transactional
        public ApiResponse<ReceivingOrderResponse> finalizeCount(Long id, Long userId) {
                ReceivingOrderEntity order = findOrder(id);

                validateStatus(order, "finalize-count", "SUBMITTED");

                // --- Sync from active scan session if exists ---
                Optional<String> activeSessionId = sessionRedis.findActiveSession(order.getWarehouseId(), userId);
                if (activeSessionId.isPresent()) {
                        sessionRedis.findById(activeSessionId.get()).ifPresent(sessionData -> {
                                List<ScanLineItem> sessionLines = sessionData.getLines();
                                if (sessionLines != null && !sessionLines.isEmpty()) {
                                        log.info("Syncing {} lines from session {} into order {} before finalize",
                                                sessionLines.size(), activeSessionId.get(), id);

                                        // Aggregate total qty per skuId
                                        Map<Long, BigDecimal> skuTotalQty = new java.util.HashMap<>();
                                        for (ScanLineItem sLine : sessionLines) {
                                                if (sLine.getSkuId() != null && sLine.getQty() != null) {
                                                        skuTotalQty.merge(sLine.getSkuId(), sLine.getQty(),
                                                                BigDecimal::add);
                                                }
                                        }

                                        for (Map.Entry<Long, BigDecimal> entry : skuTotalQty.entrySet()) {
                                                Optional<ReceivingItemEntity> existing = receivingItemRepo
                                                        .findByReceivingOrderReceivingIdAndSkuId(id, entry.getKey());

                                                if (existing.isPresent()) {
                                                        // Update existing item
                                                        existing.get().setReceivedQty(entry.getValue());
                                                        receivingItemRepo.save(existing.get());
                                                        log.info("Session sync: SKU {} → receivedQty={}",
                                                                entry.getKey(), entry.getValue());
                                                } else {
                                                        // ── Extra SKU not on order → insert with expectedQty=0 ──
                                                        ReceivingItemEntity extraItem = ReceivingItemEntity.builder()
                                                                .receivingOrder(order)
                                                                .skuId(entry.getKey())
                                                                .expectedQty(BigDecimal.ZERO)
                                                                .receivedQty(entry.getValue())
                                                                .build();
                                                        receivingItemRepo.save(extraItem);
                                                        log.info("Session sync: EXTRA SKU {} → receivedQty={} (not on order)",
                                                                entry.getKey(), entry.getValue());
                                                }
                                        }
                                }
                        });
                }

                // ── Mismatch detection: compare receivedQty vs expectedQty ──────────
                List<ReceivingItemEntity> allItems = receivingItemRepo.findByReceivingOrderReceivingId(id);
                List<IncidentItemEntity> mismatchItems = new ArrayList<>();
                List<IncidentItemEntity> unexpectedItems = new ArrayList<>();
                org.example.sep26management.application.enums.IncidentType mismatchType = null;

                for (ReceivingItemEntity item : allItems) {
                        BigDecimal expected = item.getExpectedQty() != null ? item.getExpectedQty() : BigDecimal.ZERO;
                        BigDecimal received = item.getReceivedQty() != null ? item.getReceivedQty() : BigDecimal.ZERO;
                        int cmp = received.compareTo(expected);

                        if (cmp != 0) {
                                BigDecimal diff = received.subtract(expected).abs();
                                String skuCode = skuRepo.findById(item.getSkuId())
                                        .map(SkuEntity::getSkuCode).orElse("SKU-" + item.getSkuId());

                                // ── Phân biệt: UNEXPECTED_ITEM (ngoài phiếu) vs SHORTAGE/OVERAGE ──
                                if (expected.compareTo(BigDecimal.ZERO) == 0 && received.compareTo(BigDecimal.ZERO) > 0) {
                                        // Extra SKU không có trong phiếu nhận hàng (expectedQty=0)
                                        IncidentItemEntity incItem = IncidentItemEntity.builder()
                                                .skuId(item.getSkuId())
                                                .expectedQty(BigDecimal.ZERO)
                                                .actualQty(received)
                                                .damagedQty(received)
                                                .reasonCode("UNEXPECTED_ITEM")
                                                .note("Hàng ngoài phiếu: " + skuCode + " — quét được " + received
                                                        + " nhưng không có trong đơn nhận hàng")
                                                .actionPassQty(BigDecimal.ZERO)
                                                .actionReturnQty(BigDecimal.ZERO)
                                                .actionScrapQty(BigDecimal.ZERO)
                                                .build();
                                        unexpectedItems.add(incItem);

                                        log.info("Unexpected item detected: {} — qty={} (not on order)",
                                                skuCode, received);
                                } else {
                                        // SHORTAGE hoặc OVERAGE thông thường
                                        org.example.sep26management.application.enums.IncidentType itemType =
                                                cmp < 0
                                                        ? org.example.sep26management.application.enums.IncidentType.SHORTAGE
                                                        : org.example.sep26management.application.enums.IncidentType.OVERAGE;

                                        if (mismatchType == null) {
                                                mismatchType = itemType;
                                        } else if (!mismatchType.equals(itemType)) {
                                                mismatchType = org.example.sep26management.application.enums.IncidentType.SHORTAGE;
                                        }

                                        String note = cmp < 0
                                                ? "Thiếu " + diff + " so với dự kiến (expected=" + expected + ", received=" + received + ")"
                                                : "Thừa " + diff + " so với dự kiến (expected=" + expected + ", received=" + received + ")";

                                        IncidentItemEntity incItem = IncidentItemEntity.builder()
                                                .skuId(item.getSkuId())
                                                .expectedQty(expected)
                                                .actualQty(received)
                                                .damagedQty(diff)
                                                .reasonCode(cmp < 0 ? "SHORTAGE" : "OVERAGE")
                                                .note(note)
                                                .actionPassQty(BigDecimal.ZERO)
                                                .actionReturnQty(BigDecimal.ZERO)
                                                .actionScrapQty(BigDecimal.ZERO)
                                                .build();
                                        mismatchItems.add(incItem);

                                        log.info("Mismatch detected: {} — {} {} (expected={}, received={})",
                                                skuCode, itemType.name(), diff, expected, received);
                                }
                        }
                }

                boolean hasAnyIncident = !mismatchItems.isEmpty() || !unexpectedItems.isEmpty();

                // ── Gộp tất cả items vào 1 incident duy nhất ──
                if (hasAnyIncident) {
                        // Xác định incidentType dựa trên loại items có mặt
                        org.example.sep26management.application.enums.IncidentType resolvedType;
                        if (!mismatchItems.isEmpty() && !unexpectedItems.isEmpty()) {
                                // Hỗn hợp: vừa chênh lệch số lượng vừa hàng ngoài phiếu
                                resolvedType = org.example.sep26management.application.enums.IncidentType.DISCREPANCY;
                        } else if (!unexpectedItems.isEmpty()) {
                                resolvedType = org.example.sep26management.application.enums.IncidentType.UNEXPECTED_ITEM;
                        } else if (mismatchType != null) {
                                resolvedType = mismatchType;
                        } else {
                                resolvedType = org.example.sep26management.application.enums.IncidentType.OVERAGE;
                        }

                        // Gộp tất cả items
                        List<IncidentItemEntity> allIncidentItems = new ArrayList<>();
                        allIncidentItems.addAll(unexpectedItems);
                        allIncidentItems.addAll(mismatchItems);

                        // Mô tả gộp
                        StringBuilder desc = new StringBuilder();
                        if (!unexpectedItems.isEmpty()) {
                                desc.append("Phát hiện ").append(unexpectedItems.size())
                                        .append(" SKU ngoài phiếu nhận hàng khi Keeper kiểm đếm. ");
                        }
                        if (!mismatchItems.isEmpty()) {
                                desc.append("Phát hiện chênh lệch số lượng khi Keeper kiểm đếm. ")
                                        .append(mismatchItems.size()).append(" SKU không khớp dự kiến.");
                        }

                        String incCode = "INC-" + System.currentTimeMillis() % 1_000_000;
                        IncidentEntity incident = IncidentEntity.builder()
                                .warehouseId(order.getWarehouseId())
                                .incidentCode(incCode)
                                .incidentType(resolvedType)
                                .category(org.example.sep26management.application.enums.IncidentCategory.QUALITY)
                                .severity("HIGH")
                                .occurredAt(LocalDateTime.now())
                                .description(desc.toString().trim())
                                .reportedBy(userId)
                                .status("OPEN")
                                .receivingId(id)
                                .build();
                        IncidentEntity savedIncident = incidentRepo.save(incident);

                        for (IncidentItemEntity incItem : allIncidentItems) {
                                incItem.setIncident(savedIncident);
                                incidentItemRepo.save(incItem);
                        }

                        auditLogService.logAction(
                                userId,
                                "RECEIVING_DISCREPANCY_INCIDENT",
                                "RECEIVING_ORDER",
                                order.getReceivingId(),
                                "Receiving Order " + order.getReceivingCode()
                                        + " — phát hiện " + allIncidentItems.size()
                                        + " SKU bất thường. Incident " + incCode + " gửi Manager.",
                                null, null);

                        log.info("Receiving Order {} — Incident {} created ({} type), {} items.",
                                order.getReceivingCode(), incCode, resolvedType.name(), allIncidentItems.size());

                        order.setStatus("PENDING_INCIDENT");
                        order.setUpdatedAt(LocalDateTime.now());
                        receivingOrderRepo.save(order);

                        log.info("Receiving Order {} → PENDING_INCIDENT. Incident: {}",
                                order.getReceivingCode(), incCode);

                        ReceivingOrderResponse resp = getOrder(id).getData();

                        StringBuilder msg = new StringBuilder();
                        if (!unexpectedItems.isEmpty()) {
                                msg.append("Phát hiện ").append(unexpectedItems.size())
                                        .append(" SKU ngoài phiếu nhận hàng. ");
                        }
                        if (!mismatchItems.isEmpty()) {
                                msg.append("Phát hiện chênh lệch số lượng ")
                                        .append(mismatchItems.size()).append(" SKU. ");
                        }
                        msg.append("Gửi Manager duyệt. Incident: ").append(incCode);
                        return ApiResponse.success(msg.toString(), resp);
                }

                // ── No mismatch → normal flow: PENDING_COUNT ──
                order.setStatus("PENDING_COUNT");
                order.setUpdatedAt(LocalDateTime.now());
                receivingOrderRepo.save(order);

                // ── Z-INB: Cộng tồn vào staging khi PENDING_COUNT ────────────────────
                // Tồn sẽ bị trừ khỏi Z-INB sau khi confirm putaway.
                addInboundStockToStaging(order, userId);

                log.info("Receiving Order {} finalized (SUBMITTED → PENDING_COUNT) by userId={}",
                        order.getReceivingCode(), userId);
                return ApiResponse.success("Count finalized. Status: PENDING_COUNT. Ready for QC review.",
                        getOrder(id).getData());
        }

        // ─── QC Approve ──────────────────────────────────────────────────────────

        @Transactional
        public ApiResponse<ReceivingOrderResponse> qcApprove(Long id, Long qcUserId) {
                ReceivingOrderEntity order = findOrder(id);
                // QC chỉ xử lý đơn ở PENDING_COUNT (Keeper đã scan xong, gửi QC)
                // hoặc PENDING_INCIDENT (xử lý sự cố tiếp theo).
                validateStatus(order, "qc-approve", "PENDING_COUNT", "PENDING_INCIDENT");

                order.setStatus("QC_APPROVED");
                order.setApprovedBy(qcUserId);
                order.setApprovedAt(LocalDateTime.now());
                order.setUpdatedAt(LocalDateTime.now());
                receivingOrderRepo.save(order);

                // Audit log: QC approved
                auditLogService.logAction(
                        qcUserId,
                        "RECEIVING_QC_APPROVED",
                        "RECEIVING_ORDER",
                        order.getReceivingId(),
                        "Receiving Order " + order.getReceivingCode() + " QC approved",
                        null, null);

                log.info("Receiving Order {} QC approved by userId={}", order.getReceivingCode(), qcUserId);
                return ApiResponse.success("QC approved successfully", getOrder(id).getData());
        }

        // ─── QC Submit Session ───────────────────────────────────────────────────

        @Transactional
        public ApiResponse<Map<String, Object>> qcSubmitSession(Long id, String sessionId, Long qcUserId) {
                ReceivingOrderEntity order = findOrder(id);
                validateStatus(order, "qc-submit-session", "PENDING_COUNT", "PENDING_INCIDENT");

                ScanSessionData session = sessionRedis.findById(sessionId)
                        .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

                List<ScanLineItem> lines = session.getLines();
                if (lines == null || lines.isEmpty()) {
                        return ApiResponse.error("No items scanned in this session");
                }

                // Map để gộp số lượng scan theo (skuId, condition)
                Map<Long, Map<String, BigDecimal>> scannedData = lines.stream()
                        .collect(Collectors.groupingBy(ScanLineItem::getSkuId,
                                Collectors.groupingBy(
                                        item -> item.getCondition() != null
                                                ? item.getCondition()
                                                : "PASS",
                                        Collectors.reducing(BigDecimal.ZERO,
                                                item -> item.getQty() != null
                                                        ? item.getQty()
                                                        : BigDecimal.ZERO,
                                                BigDecimal::add))));

                List<ReceivingItemEntity> dbItems = receivingItemRepo.findByReceivingOrderReceivingId(id);

                boolean hasFailItems = false;
                List<IncidentItemEntity> incidentItems = new ArrayList<>();

                for (ReceivingItemEntity dbItem : dbItems) {
                        Long skuId = dbItem.getSkuId();
                        Map<String, BigDecimal> skuScanData = scannedData.getOrDefault(skuId, Map.of());

                        BigDecimal passQty = skuScanData.getOrDefault("PASS", BigDecimal.ZERO);
                        BigDecimal failQty = skuScanData.getOrDefault("FAIL", BigDecimal.ZERO);

                        // Cập nhật lại dbItem. receivedQty = Tổng QC quét
                        BigDecimal totalScanned = passQty.add(failQty);
                        dbItem.setReceivedQty(totalScanned);

                        if (failQty.compareTo(BigDecimal.ZERO) > 0) {
                                hasFailItems = true;
                                IncidentItemEntity incidentItem = IncidentItemEntity.builder()
                                        // incident reference will be set later
                                        .skuId(skuId)
                                        .damagedQty(failQty) // Hàng lỗi QC
                                        .expectedQty(totalScanned) // Tổng QC quét
                                        .actualQty(passQty) // Số lượng đạt
                                        .note("Báo cáo từ QC Scanner")
                                        .actionPassQty(BigDecimal.ZERO)
                                        .actionReturnQty(BigDecimal.ZERO)
                                        .actionScrapQty(BigDecimal.ZERO)
                                        .build();
                                incidentItems.add(incidentItem);

                                dbItem.setCondition("FAIL");
                                dbItem.setQcRequired(true);
                        } else {
                                dbItem.setCondition("PASS");
                        }
                        receivingItemRepo.save(dbItem);
                }

                if (hasFailItems) {
                        // Tạo Quality Incident
                        IncidentEntity incident = IncidentEntity.builder()
                                .warehouseId(order.getWarehouseId())
                                .incidentType(org.example.sep26management.application.enums.IncidentType.DAMAGE)
                                .category(org.example.sep26management.application.enums.IncidentCategory.QUALITY)
                                .description("Hàng lỗi phát hiện qua bước kiểm định QC (Scanner)")
                                .receivingId(id)
                                .status("OPEN")
                                .reportedBy(qcUserId)
                                .build();

                        IncidentEntity savedIncident = incidentRepo.save(incident);

                        for (IncidentItemEntity incItem : incidentItems) {
                                incItem.setIncident(savedIncident);
                                incidentItemRepo.save(incItem);
                        }

                        order.setStatus("PENDING_INCIDENT");
                        order.setRejectedBy(qcUserId);
                        order.setRejectedAt(LocalDateTime.now());
                        order.setRejectReason("Hàng lỗi phát hiện qua QC Scanner. Incident ID: "
                                + savedIncident.getIncidentId());
                        order.setUpdatedAt(LocalDateTime.now());
                        receivingOrderRepo.save(order);

                        // Audit log: QC rejected (fail items found)
                        auditLogService.logAction(
                                qcUserId,
                                "RECEIVING_QC_REJECTED",
                                "RECEIVING_ORDER",
                                order.getReceivingId(),
                                "Receiving Order " + order.getReceivingCode()
                                        + " QC rejected — fail items detected. Incident ID: "
                                        + savedIncident.getIncidentId(),
                                null, null);

                        log.info("QC scan completed with errors for GRN {}. Created Incident {}.",
                                order.getReceivingCode(), savedIncident.getIncidentId());
                } else {
                        // Toàn bộ PASS
                        order.setStatus("QC_APPROVED");
                        order.setApprovedBy(qcUserId);
                        order.setApprovedAt(LocalDateTime.now());
                        order.setUpdatedAt(LocalDateTime.now());
                        receivingOrderRepo.save(order);

                        // Audit log: QC approved (100% pass)
                        auditLogService.logAction(
                                qcUserId,
                                "RECEIVING_QC_APPROVED",
                                "RECEIVING_ORDER",
                                order.getReceivingId(),
                                "Receiving Order " + order.getReceivingCode()
                                        + " QC scan 100% PASS — auto approved",
                                null, null);

                        log.info("QC scan completed 100% PASS for GRN {}.", order.getReceivingCode());
                }

                // Clean up session
                sessionRedis.deleteActiveSession(session.getWarehouseId(), session.getCreatedBy());
                sessionRedis.delete(sessionId);
                sseRegistry.remove(sessionId);

                return ApiResponse.success("QC scan session submitted successfully", Map.of(
                        "receivingId", order.getReceivingId(),
                        "status", order.getStatus(),
                        "hasFailItems", hasFailItems));
        }

        // ─── Approve (deprecated — đã chuyển sang GRN flow) ──────────────────────────

        @Transactional
        public ApiResponse<ReceivingOrderResponse> approve(Long id, Long managerId) {
                // BE-C4 FIX: Đổi UnsupportedOperationException → BusinessException (HTTP 400)
                // để GlobalExceptionHandler trả đúng status code thay vì 500
                throw new org.example.sep26management.infrastructure.exception.BusinessException(
                        "Thao tác approve đã chuyển sang luồng GRN. "
                                + "Vui lòng dùng: POST /v1/grns/{grnId}/approve");
        }

        // ─── Reject (deprecated — đã chuyển sang GRN flow) ───────────────────────────

        @Transactional
        public ApiResponse<ReceivingOrderResponse> reject(Long id, String reason, Long userId) {
                // BE-C4 FIX: Đổi UnsupportedOperationException → BusinessException (HTTP 400)
                throw new org.example.sep26management.infrastructure.exception.BusinessException(
                        "Thao tác reject đã chuyển sang luồng GRN. "
                                + "Vui lòng dùng: POST /v1/grns/{grnId}/reject");
        }

        // ─── Generate GRN ──────────────────────────────────────────────────────────

        @Transactional
        public ApiResponse<org.example.sep26management.application.dto.response.GrnResponse> generateGrn(Long id,
                                                                                                         Long userId) {
                ReceivingOrderEntity order = findOrder(id);

                if (!"QC_APPROVED".equals(order.getStatus())) {
                        throw new org.example.sep26management.infrastructure.exception.BusinessException(
                                "Chỉ có thể tạo GRN từ Phiếu nhận hàng đã QC_APPROVED. Trạng thái hiện tại: "
                                        + order.getStatus());
                }

                // BE-C2 FIX: Guard chống tạo GRN trùng — mỗi receivingId chỉ được có 1 GRN active
                List<GrnEntity> existingGrns = grnRepo.findByReceivingIdOrderByCreatedAtDesc(id);
                boolean hasActiveGrn = existingGrns.stream()
                        .anyMatch(g -> !"REJECTED".equals(g.getStatus()));
                if (hasActiveGrn) {
                        GrnEntity latestGrn = existingGrns.get(0);
                        throw new org.example.sep26management.infrastructure.exception.BusinessException(
                                "Phiếu nhận hàng này đã có GRN (mã: " + latestGrn.getGrnCode()
                                        + ", trạng thái: " + latestGrn.getStatus()
                                        + "). Không thể tạo thêm GRN.");
                }

                // Kiểm tra xem có Incident nào chưa RESOLVED không
                List<IncidentEntity> incidents = incidentRepo.findByReceivingIdOrderByCreatedAtDesc(id);
                boolean hasUnsettled = incidents.stream()
                        .anyMatch(i -> "OPEN".equals(i.getStatus()) || "APPROVED".equals(i.getStatus()));
                if (hasUnsettled) {
                        throw new org.example.sep26management.infrastructure.exception.BusinessException(
                                "Không thể tạo GRN: vẫn còn sự cố chưa được xử lý.");
                }

                // Tính toán số lượng GRN (Pass/Nhập kho) cho từng SKU
                List<ReceivingItemEntity> items = receivingItemRepo.findByReceivingOrderReceivingId(id);

                // Only collect damagedQty from QC QUALITY incidents (actual physical damage),
                // NOT from SHORTAGE/OVERAGE discrepancy incidents (those are already resolved
                // by adjusting receivedQty in resolveDiscrepancy)
                List<IncidentItemEntity> qcDamageItems = new ArrayList<>();
                for (IncidentEntity inc : incidents) {
                        if ("RESOLVED".equals(inc.getStatus())
                                && inc.getIncidentType() != null
                                && !org.example.sep26management.application.enums.IncidentType.SHORTAGE
                                .equals(inc.getIncidentType())
                                && !org.example.sep26management.application.enums.IncidentType.OVERAGE
                                .equals(inc.getIncidentType())) {
                                qcDamageItems
                                        .addAll(incidentItemRepo.findByIncidentIncidentId(inc.getIncidentId()));
                        }
                }

                // Map skuId -> total actual damage from QC (not discrepancy)
                Map<Long, BigDecimal> skuDamagedMap = qcDamageItems.stream()
                        .collect(Collectors.groupingBy(IncidentItemEntity::getSkuId,
                                Collectors.reducing(BigDecimal.ZERO,
                                        i -> i.getDamagedQty() != null ? i.getDamagedQty()
                                                : BigDecimal.ZERO,
                                        BigDecimal::add)));

                Map<Long, BigDecimal> skuManagerPassMap = qcDamageItems.stream()
                        .collect(Collectors.groupingBy(IncidentItemEntity::getSkuId,
                                Collectors.reducing(BigDecimal.ZERO,
                                        i -> i.getActionPassQty() != null
                                                ? i.getActionPassQty()
                                                : BigDecimal.ZERO,
                                        BigDecimal::add)));

                List<GrnItemEntity> validGrnItems = new ArrayList<>();

                String grnCode = "GRN-" + System.currentTimeMillis();
                GrnEntity grn = GrnEntity.builder()
                        .receivingId(id)
                        .grnCode(grnCode)
                        .warehouseId(order.getWarehouseId())
                        .sourceType(order.getSourceType())
                        .supplierId(order.getSupplierId())
                        .sourceReferenceCode(order.getSourceReferenceCode())
                        // BE-C1 FIX: GRN_CREATED là trạng thái ban đầu — Keeper phải gọi
                        // submitToManager() để chuyển lên PENDING_APPROVAL cho Manager duyệt.
                        // Trước đây set PENDING_APPROVAL ngay → submitToManager() luôn fail (BUG-04 context).
                        .status("GRN_CREATED")
                        .createdBy(userId)
                        .build();
                GrnEntity savedGrn = grnRepo.save(grn);

                for (ReceivingItemEntity item : items) {
                        Long skuId = item.getSkuId();
                        // receivedQty already reflects Manager's decision from resolveDiscrepancy()
                        BigDecimal receivedQty = item.getReceivedQty() != null ? item.getReceivedQty()
                                : BigDecimal.ZERO;
                        // Only subtract actual QC damage, not discrepancy amounts
                        BigDecimal damagedQty = skuDamagedMap.getOrDefault(skuId, BigDecimal.ZERO);
                        BigDecimal managerPassQty = skuManagerPassMap.getOrDefault(skuId, BigDecimal.ZERO);

                        BigDecimal goodQty = receivedQty.subtract(damagedQty);
                        if (goodQty.compareTo(BigDecimal.ZERO) < 0)
                                goodQty = BigDecimal.ZERO;

                        BigDecimal finalPassQty = goodQty.add(managerPassQty);

                        if (finalPassQty.compareTo(BigDecimal.ZERO) > 0) {
                                // Auto-calculate lot/date if missing
                                String lotNumber = item.getLotNumber();
                                LocalDate manufactureDate = item.getManufactureDate();
                                LocalDate expiryDate = item.getExpiryDate();

                                SkuEntity sku = skuRepo.findById(skuId).orElse(null);

                                if (lotNumber == null || lotNumber.isBlank()) {
                                        String skuCode = sku != null ? sku.getSkuCode() : String.valueOf(skuId);
                                        lotNumber = "LOT-" + grnCode + "-" + skuCode; // FIX: dùng grnCode thay vì receivingCode để mỗi GRN có lot riêng
                                        item.setLotNumber(lotNumber);
                                }

                                if (manufactureDate == null) {
                                        manufactureDate = LocalDate.now();
                                        item.setManufactureDate(manufactureDate);
                                }

                                if (expiryDate == null && sku != null && sku.getShelfLifeDays() != null
                                        && sku.getShelfLifeDays() > 0) {
                                        expiryDate = manufactureDate.plusDays(sku.getShelfLifeDays());
                                        item.setExpiryDate(expiryDate);
                                }

                                // Save back to receiving item for record-keeping
                                receivingItemRepo.save(item);

                                GrnItemEntity grnItem = GrnItemEntity.builder()
                                        .grn(savedGrn)
                                        .skuId(skuId)
                                        .quantity(finalPassQty)
                                        .lotNumber(lotNumber)
                                        .manufactureDate(manufactureDate)
                                        .expiryDate(expiryDate)
                                        .build();
                                grnItemRepo.save(grnItem);
                                validGrnItems.add(grnItem);
                        }
                }

                order.setStatus("GRN_CREATED");
                receivingOrderRepo.save(order);

                // DTO mapping for response
                List<org.example.sep26management.application.dto.response.GrnItemResponse> itemResponses = validGrnItems
                        .stream()
                        .map(gi -> {
                                String skuCode = null;
                                String skuName = null;
                                SkuEntity sku = skuRepo.findById(gi.getSkuId()).orElse(null);
                                if (sku != null) {
                                        skuCode = sku.getSkuCode();
                                        skuName = sku.getSkuName();
                                }
                                return org.example.sep26management.application.dto.response.GrnItemResponse
                                        .builder()
                                        .grnItemId(gi.getGrnItemId())
                                        .skuId(gi.getSkuId())
                                        .skuCode(skuCode)
                                        .skuName(skuName)
                                        .quantity(gi.getQuantity())
                                        .lotNumber(gi.getLotNumber())
                                        .manufactureDate(gi.getManufactureDate())
                                        .expiryDate(gi.getExpiryDate())
                                        .build();
                        }).collect(Collectors.toList());

                org.example.sep26management.application.dto.response.GrnResponse grnResponse = org.example.sep26management.application.dto.response.GrnResponse
                        .builder()
                        .grnId(savedGrn.getGrnId())
                        .grnCode(savedGrn.getGrnCode())
                        .receivingId(savedGrn.getReceivingId())
                        .warehouseId(savedGrn.getWarehouseId())
                        .sourceType(savedGrn.getSourceType())
                        .supplierId(savedGrn.getSupplierId())
                        .sourceReferenceCode(savedGrn.getSourceReferenceCode())
                        .status(savedGrn.getStatus())
                        .createdBy(savedGrn.getCreatedBy())
                        .createdAt(savedGrn.getCreatedAt())
                        .updatedAt(savedGrn.getUpdatedAt())
                        .items(itemResponses)
                        .build();

                return ApiResponse.success("GRN generated successfully. Pending Manager approval.",
                        grnResponse);
        }

        // ─── Post (deprecated — đã chuyển sang GRN flow) ──────────────────────────

        @Transactional
        public ApiResponse<ReceivingOrderResponse> post(Long id, Long accountantId) {
                // BE-C4 FIX: Đổi UnsupportedOperationException → BusinessException (HTTP 400)
                throw new org.example.sep26management.infrastructure.exception.BusinessException(
                        "Thao tác post đã chuyển sang luồng GRN. "
                                + "Vui lòng dùng: POST /v1/grns/{grnId}/post");
        }

        // ─── Private helpers ───────────────────────────────────────────────────────

        private ReceivingOrderEntity findOrder(Long id) {
                return receivingOrderRepo.findById(id)
                        .orElseThrow(() -> new org.example.sep26management.infrastructure.exception.BusinessException(
                                "Receiving order not found: " + id));
        }

        private void validateStatus(ReceivingOrderEntity order, String action, String... expectedStatuses) {
                boolean isValid = false;
                for (String expected : expectedStatuses) {
                        if (expected.equals(order.getStatus())) {
                                isValid = true;
                                break;
                        }
                }

                if (!isValid) {
                        throw new org.example.sep26management.infrastructure.exception.BusinessException(
                                "Cannot " + action + " in status '" + order.getStatus() + "'. Expected one of: "
                                        + String.join(", ", expectedStatuses));
                }
        }

        private Long getFirstStagingLocation(Long warehouseId) {
                try {
                        return jdbcTemplate.queryForObject(
                                "SELECT location_id FROM locations WHERE warehouse_id = ? AND is_staging = TRUE AND active = TRUE LIMIT 1",
                                Long.class, warehouseId);
                } catch (Exception e) {
                        return jdbcTemplate.queryForObject(
                                "SELECT location_id FROM locations WHERE warehouse_id = ? AND active = TRUE LIMIT 1",
                                Long.class, warehouseId);
                }
        }

        /**
         * Response tối giản (list, submit, approve, post, reject) — không cần JOIN
         * nặng.
         */
        private ReceivingOrderResponse toSummaryResponse(ReceivingOrderEntity o) {
                String createdByName = o.getCreatedBy() != null
                        ? userRepo.findById(o.getCreatedBy()).map(UserEntity::getFullName).orElse(null)
                        : null;

                // Lookup warehouseName
                String warehouseName = o.getWarehouseId() != null
                        ? warehouseRepo.findById(o.getWarehouseId())
                        .map(w -> w.getWarehouseName()).orElse(null)
                        : null;

                // Lookup supplierName
                String supplierName = o.getSupplierId() != null
                        ? supplierRepo.findById(o.getSupplierId())
                        .map(s -> s.getSupplierName()).orElse(null)
                        : null;

                // Sum expectedQty from items
                List<ReceivingItemEntity> items = receivingItemRepo
                        .findByReceivingOrderReceivingId(o.getReceivingId());
                java.math.BigDecimal totalExpectedQty = items.stream()
                        .map(ReceivingItemEntity::getExpectedQty)
                        .filter(q -> q != null)
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

                return ReceivingOrderResponse.builder()
                        .receivingId(o.getReceivingId())
                        .receivingCode(o.getReceivingCode())
                        .status(o.getStatus())
                        .warehouseId(o.getWarehouseId())
                        .warehouseName(warehouseName)
                        .supplierId(o.getSupplierId())
                        .supplierName(supplierName)
                        .sourceType(o.getSourceType())
                        .sourceReferenceCode(o.getSourceReferenceCode())
                        .note(o.getNote())
                        .createdBy(o.getCreatedBy())
                        .createdByName(createdByName)
                        .createdAt(o.getCreatedAt())
                        .updatedAt(o.getUpdatedAt())
                        .approvedBy(o.getApprovedBy())
                        .approvedAt(o.getApprovedAt())
                        .rejectedBy(o.getRejectedBy())
                        .rejectedAt(o.getRejectedAt())
                        .rejectReason(o.getRejectReason())
                        .totalExpectedQty(totalExpectedQty)
                        .totalLines(items.size())
                        .build();
        }

        private ReceivingItemResponse toItemResponse(ReceivingItemEntity item, Map<Long, SkuEntity> skuMap) {
                SkuEntity sku = skuMap.get(item.getSkuId());
                return ReceivingItemResponse.builder()
                        .receivingItemId(item.getReceivingItemId())
                        .skuId(item.getSkuId())
                        .skuCode(sku != null ? sku.getSkuCode() : null)
                        .skuName(sku != null ? sku.getSkuName() : null)
                        .unit(sku != null ? sku.getUnit() : null)
                        .expectedQty(item.getExpectedQty())
                        .receivedQty(item.getReceivedQty())
                        .lotNumber(item.getLotNumber())
                        .expiryDate(item.getExpiryDate())
                        .manufactureDate(item.getManufactureDate())
                        .note(item.getNote())
                        .condition(item.getCondition())
                        .reasonCode(item.getReasonCode())
                        .build();
        }

        // ─── Z-INB helper ─────────────────────────────────────────────────────────────
        /**
         * Khi đơn hàng inbound chuyển sang PENDING_COUNT:
         * Cộng tồn kho vào staging location (Z-INB) cho từng mặt hàng trong đơn.
         * Tồn này sẽ được trừ khỏi staging và cộng vào BIN đích khi Keeper confirm putaway.
         *
         * Nghiệp vụ:
         *   PENDING_COUNT  → upsertInventory(stagingLocation, +qty)   [Z-INB]
         *   confirmPutaway → decrementQuantity(stagingLocation, -qty)  [trừ Z-INB]
         *                  + upsertInventory(binLocation, +qty)        [cộng vào BIN]
         */
        private void addInboundStockToStaging(ReceivingOrderEntity order, Long userId) {
                try {
                        Long stagingLocationId = getFirstStagingLocationId(order.getWarehouseId());
                        if (stagingLocationId == null) {
                                log.warn("Z-INB: No staging location found for warehouse {}. Skipping Z-INB stock addition.",
                                        order.getWarehouseId());
                                return;
                        }

                        List<ReceivingItemEntity> items = receivingItemRepo.findByReceivingOrderReceivingId(order.getReceivingId());
                        if (items == null || items.isEmpty()) return;

                        for (ReceivingItemEntity item : items) {
                                if (item.getExpectedQty() == null || item.getExpectedQty().compareTo(BigDecimal.ZERO) <= 0) continue;

                                // Upsert vào inventory_snapshot tại staging location
                                jdbcTemplate.update(
                                        "INSERT INTO inventory_snapshot (warehouse_id, sku_id, lot_id, location_id, quantity, reserved_qty) " +
                                                "VALUES (?, ?, NULL, ?, ?, 0) " +
                                                // FIX: phải dùng generated column lot_id_safe thay vì expression COALESCE(lot_id,0)
                                                // vì ON CONFLICT phải khớp đúng tên cột trong PRIMARY KEY constraint
                                                "ON CONFLICT (warehouse_id, sku_id, lot_id_safe, location_id) " +
                                                "DO UPDATE SET quantity = inventory_snapshot.quantity + EXCLUDED.quantity, " +
                                                "last_updated = NOW()",
                                        order.getWarehouseId(), item.getSkuId(), stagingLocationId, item.getExpectedQty()
                                );

                                // Ghi inventory transaction type = RECEIVING_PENDING
                                jdbcTemplate.update(
                                        "INSERT INTO inventory_transactions " +
                                                "(warehouse_id, sku_id, lot_id, location_id, quantity, txn_type, reference_table, reference_id, created_by) " +
                                                "VALUES (?, ?, NULL, ?, ?, 'RECEIVING_PENDING', 'receiving_orders', ?, ?)",
                                        order.getWarehouseId(), item.getSkuId(), stagingLocationId,
                                        item.getExpectedQty(), order.getReceivingId(), userId
                                );
                        }
                        log.info("Z-INB: Added {} items to staging location {} for receiving order {}",
                                items.size(), stagingLocationId, order.getReceivingCode());
                } catch (Exception e) {
                        log.error("Z-INB: Failed to add inbound stock to staging for order {}: {}",
                                order.getReceivingCode(), e.getMessage());
                        // Không throw — lỗi Z-INB không nên block submit
                }
        }

        private Long getFirstStagingLocationId(Long warehouseId) {
                try {
                        List<Long> ids = jdbcTemplate.queryForList(
                                "SELECT location_id FROM locations WHERE warehouse_id = ? AND is_staging = TRUE AND active = TRUE LIMIT 1",
                                Long.class, warehouseId);
                        if (!ids.isEmpty()) return ids.get(0);
                        ids = jdbcTemplate.queryForList(
                                "SELECT location_id FROM locations WHERE warehouse_id = ? AND active = TRUE LIMIT 1",
                                Long.class, warehouseId);
                        return ids.isEmpty() ? null : ids.get(0);
                } catch (Exception e) {
                        log.error("getFirstStagingLocationId error: {}", e.getMessage());
                        return null;
                }
        }

}