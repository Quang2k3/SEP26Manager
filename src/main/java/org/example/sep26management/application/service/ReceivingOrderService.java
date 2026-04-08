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
import java.util.Objects;
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
        private final NotificationService notificationService;

        // ─── List ──────────────────────────────────────────────────────────────────

        @Transactional(readOnly = true)
        public ApiResponse<PageResponse<ReceivingOrderResponse>> listOrders(
                        String status, int page, int size, Long createdBy) {
                Pageable pageable = PageRequest.of(page, size);

                // createdBy != null → Keeper: chỉ thấy đơn do mình tạo
                // createdBy == null → Manager/QC: thấy tất cả
                Page<ReceivingOrderEntity> ordersPage;
                if (createdBy != null) {
                        ordersPage = status != null && !status.isBlank()
                                        ? receivingOrderRepo.findByStatusAndCreatedByOrderByCreatedAtDesc(status,
                                                        createdBy, pageable)
                                        : receivingOrderRepo.findByCreatedByOrderByCreatedAtDesc(createdBy, pageable);
                } else {
                        ordersPage = status != null && !status.isBlank()
                                        ? receivingOrderRepo.findByStatusOrderByCreatedAtDesc(status, pageable)
                                        : receivingOrderRepo.findAllByOrderByCreatedAtDesc(pageable);
                }

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

                // ── Realtime: notify QC có đơn inbound mới được tạo ─────
                String supplierNameNew = supplierId != null
                                ? supplierRepo.findById(supplierId).map(s -> s.getSupplierName()).orElse("—")
                                : "—";
                notificationService.notifyRole("QC", "receiving_pending_qc",
                                savedOrder.getReceivingId(), receivingCode,
                                supplierNameNew + " — Đơn inbound mới");

                return getOrder(savedOrder.getReceivingId());
        }

        // ─── Update Draft Order ───────────────────────────────────────────────────

        @Transactional
        public ApiResponse<ReceivingOrderResponse> updateDraftOrder(Long id,
                        org.example.sep26management.application.dto.request.ReceivingOrderRequest request,
                        Long userId) {
                ReceivingOrderEntity order = findOrder(id);
                validateOwnership(order, userId, "sửa");
                if (!"DRAFT".equals(order.getStatus())) {
                        throw new org.example.sep26management.infrastructure.exception.BusinessException(
                                        "Cannot update: only allowed in DRAFT status. Current status: '"
                                                        + order.getStatus() + "'");
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

                // ── Realtime: notify QC đơn inbound vừa được cập nhật ──
                notificationService.notifyRole("QC", "receiving_pending_qc",
                                order.getReceivingId(), order.getReceivingCode(),
                                "Đơn inbound đã cập nhật");

                return getOrder(id);
        }

        // ─── Delete Draft Order ───────────────────────────────────────────────────

        @Transactional
        public ApiResponse<Void> deleteDraftOrder(Long id, Long userId) {
                ReceivingOrderEntity order = findOrderForUpdate(id); // SELECT FOR UPDATE
                validateOwnership(order, userId, "xóa");
                if (!"DRAFT".equals(order.getStatus())) {
                        throw new org.example.sep26management.infrastructure.exception.BusinessException(
                                        "Cannot delete: only allowed in DRAFT status. Current status: '"
                                                        + order.getStatus() + "'");
                }

                // Delete items first
                List<ReceivingItemEntity> items = receivingItemRepo.findByReceivingOrderReceivingId(id);
                receivingItemRepo.deleteAll(items);

                // Lưu thông tin trước khi xóa để notify
                String deletedCode = order.getReceivingCode();
                Long deletedId = order.getReceivingId();

                // Delete order
                receivingOrderRepo.delete(order);

                log.info("Draft GRN {} deleted by userId={}", deletedCode, userId);

                // ── Realtime: notify QC đơn inbound vừa bị xóa ──────────
                notificationService.notifyRole("QC", "receiving_pending_qc",
                                deletedId, deletedCode,
                                "Đơn inbound đã bị xóa");

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
                                        "Cannot update lines: only allowed in DRAFT status. Current status: '"
                                                        + order.getStatus() + "'");
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

                // Fetch incidents to accurately split PASS and FAIL quantities
                List<IncidentEntity> incidents = incidentRepo.findByReceivingIdOrderByCreatedAtDesc(id);
                List<IncidentItemEntity> allIncidentItems = new ArrayList<>();
                for (IncidentEntity inc : incidents) {
                        allIncidentItems.addAll(incidentItemRepo.findByIncidentIncidentId(inc.getIncidentId()));
                }

                // [FIX] Gộp receiving items theo khóa (skuId_lotNumber) trước khi build
                // response
                // Cho phép hiển thị từng lô (Lot) riêng biệt cho cùng 1 SKU
                java.util.Map<String, BigDecimal> aggExpectedQty = new java.util.LinkedHashMap<>();
                java.util.Map<String, BigDecimal> aggReceivedQty = new java.util.LinkedHashMap<>();
                java.util.Map<String, ReceivingItemEntity> aggBestItem = new java.util.LinkedHashMap<>();

                for (ReceivingItemEntity item : items) {
                        String key = item.getSkuId() + "_" + (item.getLotNumber() != null ? item.getLotNumber() : "");

                        BigDecimal exp = item.getExpectedQty() != null ? item.getExpectedQty() : BigDecimal.ZERO;
                        BigDecimal rcv = item.getReceivedQty() != null ? item.getReceivedQty() : BigDecimal.ZERO;
                        aggExpectedQty.merge(key, exp, BigDecimal::add);
                        aggReceivedQty.merge(key, rcv, BigDecimal::add);

                        if (!aggBestItem.containsKey(key)) {
                                aggBestItem.put(key, item);
                        }
                }

                // Map aggregated items
                List<ReceivingItemResponse> itemResponses = new ArrayList<>();
                for (java.util.Map.Entry<String, ReceivingItemEntity> entry : aggBestItem.entrySet()) {
                        String key = entry.getKey();
                        Long skuId = Long.parseLong(key.split("_")[0]);
                        ReceivingItemEntity bestItem = entry.getValue();
                        BigDecimal totalExpected = aggExpectedQty.getOrDefault(key, BigDecimal.ZERO);
                        BigDecimal totalQty = aggReceivedQty.getOrDefault(key, BigDecimal.ZERO);

                        // Lọc bỏ hàng ngoài phiếu đã bị hoàn trả toàn bộ (Expected=0, Received=0)
                        if (totalExpected.compareTo(BigDecimal.ZERO) == 0 && totalQty.compareTo(BigDecimal.ZERO) == 0) {
                                continue;
                        }

                        // [FIX] Tính damagedQty từ incident data thay vì dùng condition trên DB row
                        // (Tính riêng theo từng Lot)
                        String expectedNoteSuffix = "(Lot: "
                                        + (bestItem.getLotNumber() == null ? "" : bestItem.getLotNumber()) + ")";
                        BigDecimal failQty = allIncidentItems.stream()
                                        .filter(i -> ("DAMAGE".equals(i.getReasonCode()) || "UNEXPECTED_ITEM".equals(i.getReasonCode()))
                                                        && skuId.equals(i.getSkuId())
                                                        && i.getNote() != null
                                                        && i.getNote().contains(expectedNoteSuffix))
                                        .map(IncidentItemEntity::getDamagedQty)
                                        .filter(java.util.Objects::nonNull)
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                        // Lấy attachmentUrl từ incident DAMAGE items
                        String attachmentUrl = failQty.compareTo(BigDecimal.ZERO) > 0
                                        ? allIncidentItems.stream()
                                                        .filter(i -> ("DAMAGE".equals(i.getReasonCode()) || "UNEXPECTED_ITEM".equals(i.getReasonCode()))
                                                                        && skuId.equals(i.getSkuId())
                                                                        && i.getNote() != null
                                                                        && i.getNote().contains(expectedNoteSuffix))
                                                        .map(IncidentItemEntity::getAttachmentUrl)
                                                        .filter(u -> u != null && !u.isBlank())
                                                        .findFirst().orElse(null)
                                        : null;

                        ReceivingItemResponse resp = toItemResponse(bestItem, skuMap);
                        resp.setExpectedQty(totalExpected);
                        resp.setReceivedQty(totalQty);
                        resp.setDamagedQty(failQty);
                        resp.setAttachmentUrl(attachmentUrl);
                        // Lot is preserved correctly via bestItem
                        resp.setLotNumber(bestItem.getLotNumber());
                        resp.setManufactureDate(bestItem.getManufactureDate());
                        resp.setExpiryDate(bestItem.getExpiryDate());

                        // [FIX] Set condition chính xác: chỉ FAIL nếu toàn bộ là hỏng
                        if (failQty.compareTo(BigDecimal.ZERO) > 0) {
                                BigDecimal passQty = totalQty.subtract(failQty);
                                if (passQty.compareTo(BigDecimal.ZERO) <= 0) {
                                        resp.setCondition("FAIL"); // Toàn bộ hỏng
                                } else {
                                        resp.setCondition("PASS"); // Có cả pass lẫn fail → để PASS, frontend dùng
                                                                   // damagedQty để tách
                                }
                        } else {
                                resp.setCondition("PASS");
                        }

                        itemResponses.add(resp);
                }

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
                ReceivingOrderEntity order = findOrderForUpdate(id); // SELECT FOR UPDATE: chống double-submit
                validateOwnership(order, userId, "submit");
                validateStatus(order, "submit", "DRAFT");

                order.setStatus("SUBMITTED");
                order.setUpdatedAt(LocalDateTime.now());
                receivingOrderRepo.save(order);

                // ── Realtime: notify QC có phiếu nhận hàng mới chờ kiểm ────────────
                String supplierName = order.getSupplierId() != null
                                ? supplierRepo.findById(order.getSupplierId()).map(s -> s.getSupplierName()).orElse("—")
                                : "—";
                notificationService.notifyRole("QC", "receiving_pending_qc",
                                order.getReceivingId(), order.getReceivingCode(), supplierName);

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
                ReceivingOrderEntity order = findOrderForUpdate(id); // SELECT FOR UPDATE
                validateOwnership(order, userId, "finalize-count");

                validateStatus(order, "finalize-count", "SUBMITTED", "KEEPER_RESCAN");

                // --- Sync from active scan session if exists ---
                Optional<String> activeSessionId = sessionRedis.findActiveSession(order.getWarehouseId(), userId);
                if (activeSessionId.isPresent()) {
                        sessionRedis.findById(activeSessionId.get()).ifPresent(sessionData -> {
                                List<ScanLineItem> sessionLines = sessionData.getLines();
                                if (sessionLines != null && !sessionLines.isEmpty()) {
                                        log.info("Syncing {} lines from session {} into order {} before finalize",
                                                        sessionLines.size(), activeSessionId.get(), id);

                                        // Aggregate total qty per (skuId, lotNumber)
                                        Map<String, BigDecimal> skuLotTotalQty = new java.util.HashMap<>();
                                        Map<String, Long> keyToSkuId = new java.util.HashMap<>();
                                        Map<String, String> keyToLot = new java.util.HashMap<>();

                                        for (ScanLineItem sLine : sessionLines) {
                                                if (sLine.getSkuId() != null && sLine.getQty() != null) {
                                                        String key = sLine.getSkuId() + "_"
                                                                        + (sLine.getLotNumber() == null ? ""
                                                                                        : sLine.getLotNumber());
                                                        skuLotTotalQty.merge(key, sLine.getQty(), BigDecimal::add);
                                                        keyToSkuId.putIfAbsent(key, sLine.getSkuId());
                                                        keyToLot.putIfAbsent(key, sLine.getLotNumber());
                                                }
                                        }

                                        for (Map.Entry<String, BigDecimal> entry : skuLotTotalQty.entrySet()) {
                                                String key = entry.getKey();
                                                Long skuId = keyToSkuId.get(key);
                                                String lot = keyToLot.get(key);
                                                BigDecimal qty = entry.getValue();

                                                java.util.Optional<ReceivingItemEntity> opt = receivingItemRepo
                                                                .findByReceivingOrderReceivingIdAndSkuId(id, skuId)
                                                                .stream()
                                                                .filter(item -> (lot == null
                                                                                ? item.getLotNumber() == null
                                                                                : lot.equals(item.getLotNumber())))
                                                                .findFirst();

                                                if (opt.isPresent()) {
                                                        ReceivingItemEntity ri = opt.get();
                                                        ri.setReceivedQty(qty);
                                                        receivingItemRepo.save(ri);
                                                        log.info("Session sync: SKU {} Lot {} → receivedQty={}", skuId,
                                                                        lot, qty);
                                                } else {
                                                        ReceivingItemEntity newRi = ReceivingItemEntity.builder()
                                                                        .receivingOrder(order)
                                                                        .skuId(skuId)
                                                                        .lotNumber(lot)
                                                                        .expectedQty(BigDecimal.ZERO)
                                                                        .receivedQty(qty)
                                                                        .build();
                                                        receivingItemRepo.save(newRi);
                                                        log.info("Session sync (extra item): SKU {} Lot {} → receivedQty={}",
                                                                        skuId, lot, qty);
                                                }
                                        }
                                }
                        });
                }

                // ── KEEPER_RESCAN: So sánh Keeper mới vs QC đã lưu ──────────────
                if ("KEEPER_RESCAN".equals(order.getStatus())) {
                        String qcSessionId = order.getQcSessionId();

                        if (qcSessionId == null) {
                                order.setStatus("PENDING_COUNT");
                                order.setUpdatedAt(LocalDateTime.now());
                                order.setNote((order.getNote() != null ? order.getNote() + "\n" : "")
                                                + "[System] Không có QC session để đối chiếu — chuyển PENDING_COUNT.");
                                receivingOrderRepo.save(order);
                                log.warn("KEEPER_RESCAN: No qcSessionId for order {}. Fallback to PENDING_COUNT.",
                                                order.getReceivingCode());
                                return ApiResponse.success(
                                                "Không có dữ liệu QC để đối chiếu. Chờ QC quét kiểm tra.",
                                                getOrder(id).getData());
                        }

                        ScanSessionData qcSession = sessionRedis.findById(qcSessionId).orElse(null);
                        if (qcSession == null || qcSession.getLines() == null) {
                                order.setStatus("PENDING_COUNT");
                                order.setQcSessionId(null);
                                order.setUpdatedAt(LocalDateTime.now());
                                order.setNote((order.getNote() != null ? order.getNote() + "\n" : "")
                                                + "[System] QC session hết hạn — cần QC quét lại từ đầu.");
                                receivingOrderRepo.save(order);
                                log.warn("KEEPER_RESCAN: QC session {} expired for order {}",
                                                qcSessionId, order.getReceivingCode());
                                return ApiResponse.success(
                                                "QC session hết hạn. Chuyển PENDING_COUNT — chờ QC quét lại.",
                                                getOrder(id).getData());
                        }

                        // Build QC scan totals per SKU
                        Map<Long, BigDecimal> qcTotals = qcSession.getLines().stream()
                                        .filter(l -> l.getSkuId() != null && l.getQty() != null)
                                        .collect(Collectors.groupingBy(ScanLineItem::getSkuId,
                                                        Collectors.reducing(BigDecimal.ZERO, ScanLineItem::getQty,
                                                                        BigDecimal::add)));

                        // Build Keeper scan totals per SKU (from just-synced data)
                        List<ReceivingItemEntity> freshItems = receivingItemRepo.findByReceivingOrderReceivingId(id);
                        Map<Long, BigDecimal> keeperTotals = freshItems.stream()
                                        .collect(Collectors.toMap(
                                                        ReceivingItemEntity::getSkuId,
                                                        it -> it.getReceivedQty() != null ? it.getReceivedQty()
                                                                        : BigDecimal.ZERO,
                                                        BigDecimal::add));

                        // So sánh TẤT CẢ SKU giữa QC và Keeper (bao gồm cả hàng ngoài phiếu)
                        java.util.Set<Long> allSkuIds = new java.util.HashSet<>(qcTotals.keySet());
                        allSkuIds.addAll(keeperTotals.keySet());

                        boolean keeperMatchesQc = true;
                        List<String> rescanMismatches = new ArrayList<>();
                        for (Long skuId : allSkuIds) {
                                BigDecimal qcQty = qcTotals.getOrDefault(skuId, BigDecimal.ZERO);
                                BigDecimal kQty = keeperTotals.getOrDefault(skuId, BigDecimal.ZERO);
                                if (qcQty.compareTo(kQty) != 0) {
                                        keeperMatchesQc = false;
                                        String skuCode = skuRepo.findById(skuId)
                                                        .map(SkuEntity::getSkuCode).orElse("SKU-" + skuId);
                                        rescanMismatches.add(skuCode + " (QC=" + qcQty + ", Keeper=" + kQty + ")");
                                }
                        }

                        if (keeperMatchesQc) {
                                // ✅ Keeper khớp QC → auto-process qua qcSubmitSession
                                log.info("Keeper rescan matches QC for GRN {}. Auto-processing QC session {}",
                                                order.getReceivingCode(), qcSessionId);

                                order.setStatus("PENDING_COUNT");
                                order.setUpdatedAt(LocalDateTime.now());
                                receivingOrderRepo.save(order);

                                Long qcUserId = order.getRejectedBy() != null ? order.getRejectedBy() : userId;
                                qcSubmitSession(id, qcSessionId, qcUserId);

                                ReceivingOrderEntity updatedOrder = findOrder(id);
                                updatedOrder.setQcSessionId(null);
                                receivingOrderRepo.save(updatedOrder);

                                log.info("Keeper rescan matched QC → auto-processed order {}",
                                                order.getReceivingCode());
                                return ApiResponse.success(
                                                "Keeper rescan khớp QC! Hệ thống tự xử lý.", getOrder(id).getData());
                        } else {
                                // ❌ Keeper vẫn lệch QC → CO_INSPECT_PENDING → Chờ đồng kiểm
                                // [CO_INSPECT_CHANGE]: Đổi trạng thái từ QC_RESCAN sang CO_INSPECT_PENDING
                                order.setStatus("CO_INSPECT_PENDING");
                                // GIỮ NGUYÊN qcSessionId
                                // GIỮ NGUYÊN receivedQty từ Keeper rescan — không reset
                                order.setUpdatedAt(LocalDateTime.now());
                                String note = "[Keeper rescan vẫn lệch QC → QC_RESCAN] "
                                                + String.join(", ", rescanMismatches);
                                order.setNote((order.getNote() != null ? order.getNote() + "\n" : "") + note);
                                receivingOrderRepo.save(order);

                                // ── Realtime: notify QC cần kiểm đếm lại lần 2 ───────
                                String supNameMismatch = order.getSupplierId() != null
                                                ? supplierRepo.findById(order.getSupplierId())
                                                                .map(s -> s.getSupplierName()).orElse("—")
                                                : "—";
                                notificationService.notifyRole("QC", "qc_rescan_required",
                                                order.getReceivingId(), order.getReceivingCode(),
                                                supNameMismatch + " — Keeper rescan vẫn lệch, QC cần quét lại lần 2 ("
                                                                + rescanMismatches.size() + " SKU)");

                                log.info("Keeper rescan STILL mismatches QC for GRN {}. → QC_RESCAN. {}",
                                                order.getReceivingCode(), note);
                                return ApiResponse.success(
                                                "Keeper rescan vẫn lệch QC (" + rescanMismatches.size()
                                                                + " SKU). Chờ QC quét lại lần 2.",
                                                getOrder(id).getData());
                        }
                }

                // ── Normal flow: SUBMITTED → PENDING_COUNT ──
                order.setStatus("PENDING_COUNT");
                order.setUpdatedAt(LocalDateTime.now());
                receivingOrderRepo.save(order);

                // [FIX] KHÔNG cộng tồn vào staging ở bước này.
                // Hàng chỉ được cộng vào inventory khi Manager post GRN (GrnService.post()).
                // Trước đây gọi addInboundStockToStaging() ở đây → hàng bị cộng 2 lần
                // (lần 1 ở đây, lần 2 ở GrnService.post()).

                // ── Realtime: notify QC phiếu đã scan xong, chờ QC kiểm đếm ─────────
                String supplierNamePc = order.getSupplierId() != null
                                ? supplierRepo.findById(order.getSupplierId()).map(s -> s.getSupplierName()).orElse("—")
                                : "—";
                notificationService.notifyRole("QC", "receiving_pending_qc",
                                order.getReceivingId(), order.getReceivingCode(),
                                supplierNamePc + " — sẵn sàng kiểm đếm");

                log.info("Receiving Order {} finalized (SUBMITTED → PENDING_COUNT) by userId={}",
                                order.getReceivingCode(), userId);
                return ApiResponse.success("Count finalized. Status: PENDING_COUNT. Ready for QC review.",
                                getOrder(id).getData());
        }

        // ─── QC Approve ──────────────────────────────────────────────────────────

        @Transactional
        public ApiResponse<ReceivingOrderResponse> qcApprove(Long id, Long qcUserId) {
                ReceivingOrderEntity order = findOrderForUpdate(id); // SELECT FOR UPDATE
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

                // ── Realtime: notify KEEPER (người tạo đơn) + broadcast tới role KEEPER
                // ──────────────
                String supplierName = order.getSupplierId() != null
                                ? supplierRepo.findById(order.getSupplierId())
                                                .map(s -> s.getSupplierName()).orElse("—")
                                : "—";
                final String grnReadySubtitle1 = supplierName + " — QC đã kiểm đếm xong";
                // 1. Notify user cụ thể (creator)
                userRepo.findById(order.getCreatedBy())
                                .ifPresent(u -> notificationService.notifyUser(u.getEmail(), "grn_create_ready",
                                                order.getReceivingId(), order.getReceivingCode(), grnReadySubtitle1));
                // 2. Broadcast tới toàn bộ KEEPER để gate-check list tự refresh
                notificationService.notifyRole("KEEPER", "grn_create_ready",
                                order.getReceivingId(), order.getReceivingCode(), grnReadySubtitle1);

                return ApiResponse.success("QC approved successfully", getOrder(id).getData());
        }

        // ─── QC Submit Session ───────────────────────────────────────────────────

        @Transactional
        public ApiResponse<Map<String, Object>> qcSubmitSession(Long id, String sessionId, Long qcUserId) {
                return qcSubmitSession(id, sessionId, qcUserId, false);
        }

        @Transactional
        public ApiResponse<Map<String, Object>> qcSubmitSession(Long id, String sessionId, Long qcUserId,
                        boolean isCoInspection) {
                ReceivingOrderEntity order = findOrderForUpdate(id);
                validateStatus(order, "qc-submit-session", "PENDING_COUNT", "PENDING_INCIDENT", "QC_RESCAN",
                                "CO_INSPECT_READY");

                ScanSessionData session = sessionRedis.findById(sessionId)
                                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

                List<ScanLineItem> rawLines = session.getLines();
                if (rawLines == null || rawLines.isEmpty()) {
                        return ApiResponse.error("No items scanned in this session");
                }

                // ── QC_RESCAN: merge old QC data for non-rescanned SKUs ──────────────
                List<ScanLineItem> lines = rawLines;
                if ("QC_RESCAN".equals(order.getStatus()) && order.getQcSessionId() != null) {
                        java.util.Set<Long> newScanSkuIds = rawLines.stream()
                                        .map(ScanLineItem::getSkuId)
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.toSet());

                        ScanSessionData oldQcSession = sessionRedis.findById(order.getQcSessionId()).orElse(null);
                        if (oldQcSession != null && oldQcSession.getLines() != null) {
                                List<ScanLineItem> merged = new ArrayList<>(rawLines);
                                for (ScanLineItem ol : oldQcSession.getLines()) {
                                        if (ol.getSkuId() != null && !newScanSkuIds.contains(ol.getSkuId())) {
                                                merged.add(ol); // Keep old PASS/FAIL data for non-rescanned SKUs
                                        }
                                }
                                lines = merged;
                                log.info("QC_RESCAN: merged {} old + {} new = {} lines for order {}",
                                                oldQcSession.getLines().size(), rawLines.size(), lines.size(),
                                                order.getReceivingCode());
                        }
                        // Clean up old QC session
                        sessionRedis.delete(order.getQcSessionId());
                        order.setQcSessionId(null);
                }

                // Map để gộp số lượng scan theo (key, condition) với key = skuId_lotNumber
                Map<String, Map<String, BigDecimal>> scannedData = lines.stream()
                                .collect(Collectors.groupingBy(
                                                item -> item.getSkuId() + "_"
                                                                + (item.getLotNumber() == null ? ""
                                                                                : item.getLotNumber()),
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

                // ══════════════════════════════════════════════════════════════════════
                // [FIX] Gộp dbItems theo skuId TRƯỚC KHI so sánh / xử lý.
                // Cùng 1 SKU có thể có nhiều rows (Keeper tạo extra, placeholder...)
                // Nếu không gộp: (1) so sánh sai, (2) set receivedQty trùng, (3) tạo thêm row.
                // ══════════════════════════════════════════════════════════════════════
                // Tổng receivedQty theo key (= Keeper qty)
                java.util.Map<String, BigDecimal> keeperQtyByKey = new java.util.LinkedHashMap<>();
                for (ReceivingItemEntity item : dbItems) {
                        BigDecimal rcv = item.getReceivedQty() != null ? item.getReceivedQty() : BigDecimal.ZERO;
                        String key = item.getSkuId() + "_" + (item.getLotNumber() == null ? "" : item.getLotNumber());
                        keeperQtyByKey.merge(key, rcv, BigDecimal::add);
                }
                // Tập hợp TẤT CẢ key đã tồn tại trong DB (kể cả expectedQty=0)
                java.util.Set<String> allDbKeys = keeperQtyByKey.keySet();

                // ── STEP 0: So sánh QC total vs Keeper receivedQty (GỘP theo Key) ────
                List<String> mismatchDetails = new ArrayList<>();
                java.util.Set<String> mismatchedKeys = new java.util.HashSet<>();
                List<String> mismatchedSkuCodes = new ArrayList<>();

                if (!isCoInspection) {
                        for (java.util.Map.Entry<String, BigDecimal> entry : keeperQtyByKey.entrySet()) {
                                String key = entry.getKey();
                                Long skuId = Long.parseLong(key.split("_")[0]);
                                String lot = key.substring(key.indexOf("_") + 1);

                                BigDecimal keeperQty = entry.getValue();
                                Map<String, BigDecimal> scanDataMap = scannedData.getOrDefault(key, Map.of());
                                BigDecimal qcTotal = scanDataMap.values().stream()
                                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                                if (qcTotal.compareTo(keeperQty) != 0) {
                                        String skuCode = skuRepo.findById(skuId)
                                                        .map(SkuEntity::getSkuCode).orElse("SKU-" + skuId);
                                        String suffix = lot.isEmpty() ? "" : " (Lot: " + lot + ")";
                                        mismatchDetails.add(skuCode + suffix + " (Keeper=" + keeperQty + ", QC="
                                                        + qcTotal + ")");
                                        mismatchedKeys.add(key);
                                        mismatchedSkuCodes.add(skuCode);
                                } else {
                                        // [GUARD] Bắt lỗi nếu KEEPER & QC đều quét 0 nhưng đây là hàng ngoài phiếu đã
                                        // cắm cờ
                                        ReceivingItemEntity item = dbItems.stream()
                                                        .filter(i -> {
                                                                String iKey = i.getSkuId() + "_"
                                                                                + (i.getLotNumber() == null ? ""
                                                                                                : i.getLotNumber());
                                                                return iKey.equals(key);
                                                        })
                                                        .findFirst().orElse(null);
                                        if (item != null && Boolean.TRUE.equals(item.getQcRequired())
                                                        && (item.getExpectedQty() == null || item.getExpectedQty()
                                                                        .compareTo(BigDecimal.ZERO) == 0)
                                                        && qcTotal.compareTo(BigDecimal.ZERO) == 0) {
                                                String skuCode = skuRepo.findById(skuId).map(SkuEntity::getSkuCode)
                                                                .orElse("SKU-" + skuId);
                                                String suffix = lot.isEmpty() ? "" : " (Lot: " + lot + ")";
                                                mismatchDetails.add(skuCode + suffix
                                                                + " (Kho phát hiện ngoài lô nhưng bị bỏ sót: Keeper=0, QC=0)");
                                                mismatchedKeys.add(key);
                                                mismatchedSkuCodes.add(skuCode);
                                        }
                                }
                        }

                        // ── STEP 0b: QC quét Key mà Keeper chưa quét (thùng lạc/lô lạ) ────────────
                        java.util.Set<String> dbKeys = allDbKeys;
                        for (Map.Entry<String, Map<String, BigDecimal>> entry : scannedData.entrySet()) {
                                String key = entry.getKey();
                                if (dbKeys.contains(key))
                                        continue; // Đã check trong Step 0a

                                BigDecimal qcTotal = entry.getValue().values().stream()
                                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                                if (qcTotal.compareTo(BigDecimal.ZERO) > 0) {
                                        Long skuId = Long.parseLong(key.split("_")[0]);
                                        String lot = key.substring(key.indexOf("_") + 1);
                                        String skuCode = skuRepo.findById(skuId)
                                                        .map(SkuEntity::getSkuCode).orElse("SKU-" + skuId);
                                        String suffix = lot.isEmpty() ? "" : " (Lot: " + lot + ")";
                                        mismatchDetails.add(skuCode + suffix + " (Keeper=0, QC=" + qcTotal
                                                        + ") [ngoài phiếu/lạc lô]");
                                        mismatchedKeys.add(key);
                                        mismatchedSkuCodes.add(skuCode);
                                }
                        }
                } // End of !isCoInspection condition

                if (!isCoInspection && !mismatchDetails.isEmpty()) {
                        // Chênh lệch → lưu QC session → yêu cầu Keeper scan lại
                        order.setStatus("KEEPER_RESCAN");
                        order.setQcSessionId(sessionId);
                        order.setUpdatedAt(LocalDateTime.now());
                        String mismatchNote = "[QC vs Keeper mismatch] " + String.join(", ", mismatchDetails);
                        order.setNote((order.getNote() != null ? order.getNote() + "\n" : "") + mismatchNote);
                        receivingOrderRepo.save(order);

                        // Re-save QC session với TTL mới để Keeper có thời gian rescan
                        sessionRedis.save(sessionId, session);
                        sessionRedis.deleteActiveSession(session.getWarehouseId(), session.getCreatedBy());

                        // [FIX] CHỈ xóa extra items (expectedQty=0) CỦA SKU BỊ MISMATCH
                        // Trước đây xóa TẤT CẢ extra items → Keeper rescan thấy lệch dù QC đã khớp
                        List<ReceivingItemEntity> extraItems = dbItems.stream()
                                        .filter(i -> {
                                                String iKey = i.getSkuId() + "_"
                                                                + (i.getLotNumber() == null ? "" : i.getLotNumber());
                                                return (i.getExpectedQty() == null
                                                                || i.getExpectedQty().compareTo(BigDecimal.ZERO) == 0)
                                                                && mismatchedKeys.contains(iKey);
                                        })
                                        .collect(Collectors.toList());
                        if (!extraItems.isEmpty()) {
                                receivingItemRepo.deleteAll(extraItems);
                                dbItems.removeAll(extraItems);
                        }
                        for (ReceivingItemEntity dbItem : dbItems) {
                                String iKey = dbItem.getSkuId() + "_"
                                                + (dbItem.getLotNumber() == null ? "" : dbItem.getLotNumber());
                                if (mismatchedKeys.contains(iKey)) {
                                        dbItem.setReceivedQty(BigDecimal.ZERO);
                                        dbItem.setCondition(null);
                                        dbItem.setReasonCode(null);
                                        receivingItemRepo.save(dbItem);
                                }
                                // Key khớp: giữ nguyên receivedQty
                        }

                        // Tạo placeholder ReceivingItemEntity cho extra Key (QC quét nhưng Keeper miss)
                        for (String mismatchKey : mismatchedKeys) {
                                Long mismatchSkuId = Long.parseLong(mismatchKey.split("_")[0]);
                                String mismatchLot = mismatchKey.substring(mismatchKey.indexOf("_") + 1);

                                boolean existsInDb = dbItems.stream()
                                                .anyMatch(i -> {
                                                        String iKey = i.getSkuId() + "_"
                                                                        + (i.getLotNumber() == null ? ""
                                                                                        : i.getLotNumber());
                                                        return iKey.equals(mismatchKey);
                                                });
                                if (!existsInDb) {
                                        BigDecimal qcQty = scannedData.containsKey(mismatchKey)
                                                        ? scannedData.get(mismatchKey).values().stream()
                                                                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                                                        : BigDecimal.ZERO;

                                        String qcNote;
                                        if (qcQty.compareTo(BigDecimal.ZERO) == 0) {
                                                qcNote = "[QC Flagged] QC xác nhận KHÔNG CÓ thùng nào của lô này (0 thùng). Nếu bạn quét nhầm, hãy điều chỉnh (bấm -) xoá đi!";
                                        } else {
                                                qcNote = "[QC Flagged] QC phát hiện " + qcQty
                                                                + " thùng ngoài phiếu rác lô. Yêu cầu Keeper bắt buộc scan xác nhận!";
                                        }

                                        ReceivingItemEntity placeholder = ReceivingItemEntity.builder()
                                                        .receivingOrder(order)
                                                        .skuId(mismatchSkuId)
                                                        .lotNumber(mismatchLot.isEmpty() ? null : mismatchLot)
                                                        .expectedQty(BigDecimal.ZERO)
                                                        .receivedQty(BigDecimal.ZERO)
                                                        .qcRequired(true)
                                                        .note(qcNote)
                                                        .build();
                                        receivingItemRepo.save(placeholder);
                                        log.info("Created flagged placeholder ReceivingItem for extra Key {} on order {} (qcQty={})",
                                                        mismatchKey, order.getReceivingCode(), qcQty);
                                } else {
                                        // Update existing item nếu cần thiết (phòng hờ)
                                        dbItems.stream()
                                                        .filter(i -> {
                                                                String iKey = i.getSkuId() + "_"
                                                                                + (i.getLotNumber() == null ? ""
                                                                                                : i.getLotNumber());
                                                                return iKey.equals(mismatchKey);
                                                        })
                                                        .forEach(item -> {
                                                                if (item.getExpectedQty() == null
                                                                                || item.getExpectedQty().compareTo(
                                                                                                BigDecimal.ZERO) == 0) {
                                                                        item.setQcRequired(true);
                                                                        item.setNote("[QC Flagged] Hàng ngoài phiếu/lô. Bắt buộc scan xác nhận!");
                                                                        receivingItemRepo.save(item);
                                                                }
                                                        });
                                }
                        }

                        // ── Realtime: notify KEEPER (người tạo đơn) cần quét lại SKU lệch ─────────
                        String supNameRescan = order.getSupplierId() != null
                                        ? supplierRepo.findById(order.getSupplierId()).map(s -> s.getSupplierName())
                                                        .orElse("—")
                                        : "—";
                        final String rescanSubtitle = supNameRescan + " — QC phát hiện lệch SL, Keeper cần quét lại ("
                                        + mismatchedSkuCodes.size() + " SKU: " + String.join(", ", mismatchedSkuCodes)
                                        + ")";
                        userRepo.findById(order.getCreatedBy())
                                        .ifPresent(u -> notificationService.notifyUser(u.getEmail(),
                                                        "keeper_rescan_required",
                                                        order.getReceivingId(), order.getReceivingCode(),
                                                        rescanSubtitle));

                        log.info("QC scan for GRN {} — {} SKU(s) mismatch with Keeper. KEEPER_RESCAN. {}",
                                        order.getReceivingCode(), mismatchDetails.size(), mismatchNote);

                        Map<String, Object> result = new java.util.LinkedHashMap<>();
                        result.put("receivingId", id);
                        result.put("status", "KEEPER_RESCAN");
                        result.put("matched", false);
                        result.put("mismatchCount", mismatchDetails.size());
                        result.put("mismatches", mismatchDetails);
                        result.put("mismatchedSkuCodes", mismatchedSkuCodes);
                        result.put("message", "Số lượng QC không khớp Keeper — đã yêu cầu Keeper quét lại ("
                                        + mismatchedSkuCodes.size() + " SKU: " + String.join(", ", mismatchedSkuCodes)
                                        + ")");
                        return ApiResponse.success("QC/Keeper mismatch — rescan requested", result);
                }

                // ── STEP 1: QC khớp Keeper → kiểm tra FAIL items ──
                boolean hasIssues = false;
                List<IncidentItemEntity> incidentItems = new ArrayList<>();

                java.util.Set<String> orderKeys = new java.util.HashSet<>(allDbKeys);

                // Tổng expectedQty theo SkuId (bỏ qua lot vì PO không có lot)
                java.util.Map<Long, BigDecimal> aggExpectedBySkuId = new java.util.LinkedHashMap<>();
                for (ReceivingItemEntity item : dbItems) {
                        BigDecimal exp = item.getExpectedQty() != null ? item.getExpectedQty() : BigDecimal.ZERO;
                        aggExpectedBySkuId.merge(item.getSkuId(), exp, BigDecimal::add);
                }

                // Tổng scannedQty theo SkuId (CHỈ cộng nếu KHÔNG phải UNEXPECTED_ITEM)
                java.util.Map<Long, BigDecimal> aggScannedBySkuId = new java.util.LinkedHashMap<>();

                // Xử lý nhận hàng và phân loại Damage cho TỪNG LOT (Key)
                java.util.Set<String> processedKeys = new java.util.HashSet<>();
                for (ReceivingItemEntity dbItem : dbItems) {
                        String key = dbItem.getSkuId() + "_"
                                        + (dbItem.getLotNumber() == null ? "" : dbItem.getLotNumber());

                        // Chỉ xử lý 1 lần cho mỗi Lot
                        if (processedKeys.contains(key)) {
                                dbItem.setReceivedQty(BigDecimal.ZERO);
                                receivingItemRepo.save(dbItem);
                                continue;
                        }
                        processedKeys.add(key);
                        
                        Long skuId = dbItem.getSkuId();
                        boolean isUnexpected = false;
                        if (dbItem.getExpectedQty() == null || dbItem.getExpectedQty().compareTo(BigDecimal.ZERO) == 0) {
                                boolean hasLotAgnostic = dbItems.stream()
                                                .anyMatch(i -> i.getSkuId().equals(skuId)
                                                                && (i.getLotNumber() == null
                                                                                || i.getLotNumber().isEmpty())
                                                                && i.getExpectedQty() != null
                                                                && i.getExpectedQty().compareTo(BigDecimal.ZERO) > 0);
                                if (!hasLotAgnostic) {
                                        isUnexpected = true;
                                }
                        }

                        Map<String, BigDecimal> scanDataMap = scannedData.getOrDefault(key, Map.of());

                        BigDecimal passQty = scanDataMap.getOrDefault("PASS", BigDecimal.ZERO);
                        BigDecimal failQty = scanDataMap.getOrDefault("FAIL", BigDecimal.ZERO);

                        BigDecimal totalScanned = passQty.add(failQty);
                        dbItem.setReceivedQty(totalScanned);
                        
                        // [FIX] Chỉ cộng vào tổng SKU nếu KHÔNG phải là hàng ngoài phiếu
                        // Để không làm sai lệch tính toán SHORTAGE/OVERAGE của những lô hàng đúng
                        if (!isUnexpected) {
                                aggScannedBySkuId.merge(skuId, totalScanned, BigDecimal::add);
                        }

                        // Fetch attachmentUrl
                        String lotNumber = dbItem.getLotNumber();
                        String attachmentUrl = failQty.compareTo(BigDecimal.ZERO) > 0 ? lines.stream()
                                        .filter(l -> l.getSkuId() != null && l.getSkuId().equals(skuId)
                                                        && "FAIL".equals(l.getCondition())
                                                        && (lotNumber == null ? l.getLotNumber() == null
                                                                        : lotNumber.equals(l.getLotNumber())))
                                        .map(ScanLineItem::getAttachmentUrl)
                                        .filter(u -> u != null && !u.isBlank())
                                        .findFirst()
                                        .orElse(null) : null;

                        if (failQty.compareTo(BigDecimal.ZERO) > 0) {
                                dbItem.setCondition("FAIL");
                        } else {
                                dbItem.setCondition("PASS");
                        }

                        if (isUnexpected) {
                                // Nếu là hàng lạ lô, luôn là UNEXPECTED_ITEM
                                if (totalScanned.compareTo(BigDecimal.ZERO) > 0) {
                                        hasIssues = true;
                                        IncidentItemEntity extraItem = IncidentItemEntity.builder()
                                                        .skuId(skuId)
                                                        .damagedQty(failQty)
                                                        .expectedQty(BigDecimal.ZERO)
                                                        .actualQty(totalScanned)
                                                        .reasonCode("UNEXPECTED_ITEM")
                                                        .note("Hàng lạ lô/ngoài phiếu (Lot: " + lotNumber
                                                                        + ") — QC quét được " + totalScanned)
                                                        .attachmentUrl(attachmentUrl)
                                                        .lotNumber(lotNumber)
                                                        .expiryDate(dbItem.getExpiryDate())
                                                        .actionPassQty(BigDecimal.ZERO)
                                                        .actionReturnQty(BigDecimal.ZERO)
                                                        .actionScrapQty(BigDecimal.ZERO)
                                                        .build();
                                        incidentItems.add(extraItem);
                                }
                        } else {
                                // Xử lý hàng hỏng (Tính riêng theo từng Lot)
                                if (failQty.compareTo(BigDecimal.ZERO) > 0) {
                                        hasIssues = true;
                                        BigDecimal skuExpectedQty = aggExpectedBySkuId.getOrDefault(skuId, BigDecimal.ZERO);
                                        IncidentItemEntity dmgItem = IncidentItemEntity.builder()
                                                        .skuId(skuId)
                                                        .damagedQty(failQty)
                                                        .expectedQty(skuExpectedQty)
                                                        .actualQty(totalScanned)
                                                        .reasonCode("DAMAGE")
                                                        .note("Hàng hỏng phát hiện khi QC (Lot: "
                                                                        + (lotNumber == null ? "" : lotNumber) + ")")
                                                        .attachmentUrl(attachmentUrl)
                                                        .lotNumber(lotNumber)
                                                        .expiryDate(dbItem.getExpiryDate())
                                                        .actionPassQty(BigDecimal.ZERO)
                                                        .actionReturnQty(BigDecimal.ZERO)
                                                        .actionScrapQty(BigDecimal.ZERO)
                                                        .build();
                                        incidentItems.add(dmgItem);
                                }
                        }

                        receivingItemRepo.save(dbItem);
                }

                // ── STEP 1b: Phát hiện thiếu/thừa so với giấy tờ (expectedQty vs scannedQty) ──
                // Case: Keeper tạo phiếu expectedQty=3 nhưng thực tế chỉ scan được 2,
                // QC cũng chỉ đếm được 2 → QC khớp Keeper (cả 2 = 2) → STEP 0 pass.
                // Nhưng thực nhận (2) ≠ giấy tờ (3) → cần tạo/gộp incident SHORTAGE.
                for (Map.Entry<Long, BigDecimal> entry : aggExpectedBySkuId.entrySet()) {
                        Long skuId = entry.getKey();
                        BigDecimal expectedQty = entry.getValue();
                        BigDecimal scannedQty = aggScannedBySkuId.getOrDefault(skuId, BigDecimal.ZERO);

                        // Chỉ check khi expectedQty > 0 (bỏ qua hàng ngoài phiếu có expected=0)
                        if (expectedQty.compareTo(BigDecimal.ZERO) <= 0) continue;

                        // So sánh expectedQty vs scannedQty
                        if (expectedQty.compareTo(scannedQty) != 0) {
                                BigDecimal diff = scannedQty.subtract(expectedQty);
                                String skuCode = skuRepo.findById(skuId)
                                                .map(SkuEntity::getSkuCode).orElse("SKU-" + skuId);

                                String shortageNote;
                                if (diff.compareTo(BigDecimal.ZERO) < 0) {
                                        shortageNote = "Thiếu hàng: giấy tờ=" + expectedQty
                                                        + ", thực nhận=" + scannedQty
                                                        + " (thiếu " + diff.abs() + ")";
                                } else {
                                        shortageNote = "Thừa hàng: giấy tờ=" + expectedQty
                                                        + ", thực nhận=" + scannedQty
                                                        + " (thừa " + diff.abs() + ")";
                                }

                                // Không gộp với DAMAGE → tạo item SHORTAGE/OVERAGE mới
                                String reasonCode = diff.compareTo(BigDecimal.ZERO) < 0
                                                ? "SHORTAGE" : "OVERAGE";
                                hasIssues = true;
                                
                                final Long finalSkuIdDisc = skuId;
                                String representativeLot = dbItems.stream()
                                                .filter(i -> finalSkuIdDisc.equals(i.getSkuId()) && i.getLotNumber() != null && !i.getLotNumber().isEmpty())
                                                .map(ReceivingItemEntity::getLotNumber)
                                                .findFirst().orElse(null);
                                java.time.LocalDate representativeExpiry = dbItems.stream()
                                                .filter(i -> finalSkuIdDisc.equals(i.getSkuId()) && i.getExpiryDate() != null)
                                                .map(ReceivingItemEntity::getExpiryDate)
                                                .findFirst().orElse(null);

                                IncidentItemEntity discItem = IncidentItemEntity.builder()
                                                .skuId(skuId)
                                                .damagedQty(BigDecimal.ZERO)
                                                .expectedQty(expectedQty)
                                                .actualQty(scannedQty)
                                                .reasonCode(reasonCode)
                                                .note(shortageNote)
                                                .lotNumber(representativeLot)
                                                .expiryDate(representativeExpiry)
                                                .actionPassQty(BigDecimal.ZERO)
                                                .actionReturnQty(BigDecimal.ZERO)
                                                .actionScrapQty(BigDecimal.ZERO)
                                                .build();
                                incidentItems.add(discItem);

                                log.info("Discrepancy detected: SKU {} — expected={}, actual={}, diff={}",
                                                skuCode, expectedQty, scannedQty, diff);
                        }
                }

                // ── STEP 2: Phát hiện hàng ngoài lô (QC quét Key không có trên đơn) ──
                for (Map.Entry<String, Map<String, BigDecimal>> entry : scannedData.entrySet()) {
                        String key = entry.getKey();
                        if (orderKeys.contains(key))
                                continue;

                        Long skuId = Long.parseLong(key.split("_")[0]);
                        String mismatchLot = key.substring(key.indexOf("_") + 1);

                        Map<String, BigDecimal> scanDataMap = entry.getValue();
                        BigDecimal passQty = scanDataMap.getOrDefault("PASS", BigDecimal.ZERO);
                        BigDecimal failQty = scanDataMap.getOrDefault("FAIL", BigDecimal.ZERO);
                        BigDecimal totalExtra = passQty.add(failQty);

                        if (totalExtra.compareTo(BigDecimal.ZERO) <= 0)
                                continue;

                        java.util.Optional<ReceivingItemEntity> existingItem = receivingItemRepo
                                        .findByReceivingOrderReceivingIdAndSkuId(id, skuId).stream()
                                        .filter(i -> (mismatchLot.isEmpty() ? i.getLotNumber() == null
                                                        : mismatchLot.equals(i.getLotNumber())))
                                        .findFirst();

                        java.time.LocalDate extraExpiryDate = existingItem.map(ReceivingItemEntity::getExpiryDate).orElse(null);

                        hasIssues = true;

                        IncidentItemEntity extraItem = IncidentItemEntity.builder()
                                        .skuId(skuId)
                                        .damagedQty(failQty)
                                        .expectedQty(BigDecimal.ZERO)
                                        .actualQty(totalExtra)
                                        .reasonCode("UNEXPECTED_ITEM")
                                        .note("Hàng lạ lô/ngoài phiếu (Lot: " + mismatchLot + ") — QC quét được "
                                                        + totalExtra)
                                        .lotNumber(mismatchLot.isEmpty() ? null : mismatchLot)
                                        .expiryDate(extraExpiryDate)
                                        .actionPassQty(BigDecimal.ZERO)
                                        .actionReturnQty(BigDecimal.ZERO)
                                        .actionScrapQty(BigDecimal.ZERO)
                                        .build();
                        incidentItems.add(extraItem);

                        if (existingItem.isPresent()) {
                                ReceivingItemEntity rcItem = existingItem.get();
                                rcItem.setReceivedQty(totalExtra);
                                rcItem.setQcRequired(true);
                                rcItem.setCondition(failQty.compareTo(BigDecimal.ZERO) > 0 ? "FAIL" : "PASS");
                                rcItem.setReasonCode(failQty.compareTo(BigDecimal.ZERO) > 0 ? "DAMAGE" : null);
                                receivingItemRepo.save(rcItem);
                        } else {
                                ReceivingItemEntity newRcItem = ReceivingItemEntity.builder()
                                                .receivingOrder(order)
                                                .skuId(skuId)
                                                .lotNumber(mismatchLot.isEmpty() ? null : mismatchLot)
                                                .expectedQty(BigDecimal.ZERO)
                                                .receivedQty(totalExtra)
                                                .qcRequired(true)
                                                .condition(failQty.compareTo(BigDecimal.ZERO) > 0 ? "FAIL" : "PASS")
                                                .reasonCode(failQty.compareTo(BigDecimal.ZERO) > 0 ? "DAMAGE" : null)
                                                .build();
                                receivingItemRepo.save(newRcItem);
                        }

                        log.info("Extra SKU/Lot detected in QC scan: key={}, qty={}", key, totalExtra);
                }

                if (hasIssues) {
                        List<IncidentItemEntity> damageItems = incidentItems.stream()
                                        .filter(i -> "DAMAGE".equals(i.getReasonCode())).collect(Collectors.toList());
                        List<IncidentItemEntity> discrepancyItems = incidentItems.stream()
                                        .filter(i -> !"DAMAGE".equals(i.getReasonCode())).collect(Collectors.toList());

                        // [FIX] Nếu item DAMAGE cũng có shortage/overage (expectedQty ≠ actualQty)
                        // thì chuyển sang discrepancy để FE hiển thị đúng phán quyết
                        // VD: expected=3, actual=2, fail=1 → DAMAGE + SHORTAGE → gộp thành DISCREPANCY
                        boolean damageHasQtyMismatch = damageItems.stream().anyMatch(i ->
                                        i.getExpectedQty() != null && i.getActualQty() != null
                                        && i.getExpectedQty().compareTo(BigDecimal.ZERO) > 0
                                        && i.getExpectedQty().compareTo(i.getActualQty()) != 0);
                        if (damageHasQtyMismatch && discrepancyItems.isEmpty()) {
                                // Chuyển tất cả DAMAGE items sang discrepancy
                                discrepancyItems = new ArrayList<>(damageItems);
                                damageItems = new ArrayList<>();
                        }

                        List<IncidentEntity> createdIncidents = new ArrayList<>();

                        // ════════════════════════════════════════════════════════════════════
                        // [FIX] Mỗi đơn chỉ tạo TỐI ĐA 1 incident:
                        // - Chỉ DAMAGE → 1 incident DAMAGE (Hư hỏng)
                        // - Có DISCREPANCY → 1 incident DISCREPANCY (Tổng hợp),
                        // gộp cả DAMAGE items vào nếu có
                        // ════════════════════════════════════════════════════════════════════

                        if (!discrepancyItems.isEmpty()) {
                                // Có thừa/thiếu/ngoài phiếu → Tạo 1 incident DISCREPANCY tổng hợp
                                // GỘP cả damage items vào incident này
                                List<IncidentItemEntity> allItems = new ArrayList<>(discrepancyItems);
                                allItems.addAll(damageItems); // Gộp hư hỏng vào cùng phiếu tổng hợp

                                String incCode = "INC-QC-RCV-" + id + "-" + (System.currentTimeMillis() % 100_000);
                                long overageCount = discrepancyItems.stream()
                                                .filter(i -> "OVERAGE".equals(i.getReasonCode())).count();
                                long shortageCount = discrepancyItems.stream()
                                                .filter(i -> "SHORTAGE".equals(i.getReasonCode())).count();
                                long extraCount = discrepancyItems.stream()
                                                .filter(i -> "UNEXPECTED_ITEM".equals(i.getReasonCode())).count();
                                long dmgCount = damageItems.size();

                                String desc = "Phát hiện thừa/thiếu qua bước kiểm định QC (Scanner)"
                                                + (overageCount > 0 ? " — " + overageCount + " SKU thừa" : "")
                                                + (shortageCount > 0 ? " — " + shortageCount + " SKU thiếu" : "")
                                                + (extraCount > 0 ? " — " + extraCount + " SKU ngoài phiếu" : "")
                                                + (dmgCount > 0 ? " — " + dmgCount + " SKU hỏng" : "");

                                IncidentEntity inc = IncidentEntity.builder()
                                                .warehouseId(order.getWarehouseId())
                                                .incidentCode(incCode)
                                                .incidentType(org.example.sep26management.application.enums.IncidentType.DISCREPANCY)
                                                .category(org.example.sep26management.application.enums.IncidentCategory.QUALITY)
                                                .severity(dmgCount > 0 ? "HIGH" : "MEDIUM")
                                                .occurredAt(java.time.LocalDateTime.now())
                                                .description(desc)
                                                .receivingId(id)
                                                .status("OPEN")
                                                .reportedBy(qcUserId)
                                                .build();
                                IncidentEntity savedInc = incidentRepo.save(inc);
                                createdIncidents.add(savedInc);
                                for (IncidentItemEntity incItem : allItems) {
                                        incItem.setIncident(savedInc);
                                        incidentItemRepo.save(incItem);
                                }
                        } else if (!damageItems.isEmpty()) {
                                // CHỈ có hư hỏng (không thừa/thiếu) → Tạo 1 incident DAMAGE riêng
                                String damageCode = "INC-QC-RCV-" + id + "-" + (System.currentTimeMillis() % 100_000);
                                IncidentEntity damageInc = IncidentEntity.builder()
                                                .warehouseId(order.getWarehouseId())
                                                .incidentCode(damageCode)
                                                .incidentType(org.example.sep26management.application.enums.IncidentType.DAMAGE)
                                                .category(org.example.sep26management.application.enums.IncidentCategory.QUALITY)
                                                .severity("HIGH")
                                                .occurredAt(java.time.LocalDateTime.now())
                                                .description("Phát hiện bất thường qua bước kiểm định QC (Scanner) — "
                                                                + damageItems.size() + " SKU hỏng")
                                                .receivingId(id)
                                                .status("OPEN")
                                                .reportedBy(qcUserId)
                                                .build();
                                IncidentEntity savedDamageInc = incidentRepo.save(damageInc);
                                createdIncidents.add(savedDamageInc);
                                for (IncidentItemEntity incItem : damageItems) {
                                        incItem.setIncident(savedDamageInc);
                                        incidentItemRepo.save(incItem);
                                }
                        }

                        // Cập nhật trạng thái phiếu
                        order.setStatus("PENDING_INCIDENT");
                        order.setRejectedBy(qcUserId);
                        order.setRejectedAt(LocalDateTime.now());

                        String rejectReason = createdIncidents.stream()
                                        .map(inc -> inc.getIncidentType() + " (" + inc.getIncidentId() + ")")
                                        .collect(Collectors.joining(", "));
                        order.setRejectReason(
                                        "Hàng lỗi/thừa/thiếu phát hiện qua QC Scanner. Incidents: " + rejectReason);
                        order.setUpdatedAt(LocalDateTime.now());
                        receivingOrderRepo.save(order);

                        // ── Realtime: notify MANAGER (broadcast) + KEEPER (người tạo đơn)
                        for (IncidentEntity inc : createdIncidents) {
                                String incSubtitle = order.getReceivingCode() + " — QC phát hiện "
                                                + (inc.getIncidentType().name().equals("DAMAGE") ? "hàng lỗi"
                                                                : "thừa/thiếu");
                                notificationService.notifyRole("MANAGER", "incident_open",
                                                inc.getIncidentId(), inc.getIncidentCode(), incSubtitle);
                                userRepo.findById(order.getCreatedBy())
                                                .ifPresent(u -> notificationService.notifyUser(u.getEmail(),
                                                                "incident_open",
                                                                inc.getIncidentId(), inc.getIncidentCode(),
                                                                incSubtitle));
                        }

                        // Audit log: QC rejected (fail items found)
                        String incidentRefs = createdIncidents.stream()
                                        .map(inc -> inc.getIncidentId().toString())
                                        .collect(Collectors.joining(","));
                        auditLogService.logAction(
                                        qcUserId,
                                        "RECEIVING_QC_REJECTED",
                                        "RECEIVING_ORDER",
                                        order.getReceivingId(),
                                        "Receiving Order " + order.getReceivingCode()
                                                        + " QC rejected — issues detected. Incident IDs: "
                                                        + incidentRefs,
                                        null, null);

                        log.info("QC scan completed with errors for GRN {}. Created Incidents {}.",
                                        order.getReceivingCode(), incidentRefs);
                } else {
                        // Toàn bộ PASS
                        order.setStatus("QC_APPROVED");
                        order.setApprovedBy(qcUserId);
                        order.setApprovedAt(LocalDateTime.now());
                        order.setUpdatedAt(LocalDateTime.now());
                        receivingOrderRepo.save(order);

                        // ── Realtime: notify KEEPER (người tạo đơn) + broadcast KEEPER QC xong 100%
                        // pass → cần tạo GRN ──
                        String supNameQc = order.getSupplierId() != null
                                        ? supplierRepo.findById(order.getSupplierId()).map(s -> s.getSupplierName())
                                                        .orElse("—")
                                        : "—";
                        final String grnReadySubtitle2 = supNameQc + " — QC 100% PASS";
                        userRepo.findById(order.getCreatedBy())
                                        .ifPresent(u -> notificationService.notifyUser(u.getEmail(), "grn_create_ready",
                                                        order.getReceivingId(), order.getReceivingCode(),
                                                        grnReadySubtitle2));
                        notificationService.notifyRole("KEEPER", "grn_create_ready",
                                        order.getReceivingId(), order.getReceivingCode(), grnReadySubtitle2);

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

                // Release QC claim trên ReceivingOrder — cho phép QC khác xử lý tiếp nếu cần
                receivingOrderRepo.releaseQcAssignment(id, qcUserId);
                log.info("[QCClaim] Released QC claim: receivingId={} qcUserId={}", id, qcUserId);

                // Push WS → QC khác biết phiếu này available lại (hoặc chuyển sang status mới)
                try {
                        notificationService.notifyRole("QC", "qc_released",
                                        id, order.getReceivingCode(), "QC hoàn thành kiểm định");
                } catch (Exception ignored) {
                }

                return ApiResponse.success("QC scan session submitted successfully", Map.of(
                                "receivingId", order.getReceivingId(),
                                "status", order.getStatus(),
                                "hasIssues", hasIssues));
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

        // ─── Đồng Kiểm (Co-Inspection) Confirm ────────────────────────────────────

        @Transactional
        public ApiResponse<ReceivingOrderResponse> confirmCoInspect(Long id, Long userId, String role) {
                ReceivingOrderEntity order = findOrderForUpdate(id);

                if ("KEEPER".equals(role)) {
                        if (!"CO_INSPECT_PENDING".equals(order.getStatus())) {
                                throw new org.example.sep26management.infrastructure.exception.BusinessException(
                                                "Chỉ có thể xác nhận khi trạng thái là CO_INSPECT_PENDING (hiện tại: "
                                                                + order.getStatus() + ")");
                        }
                        validateOwnership(order, userId, "xác nhận đồng kiểm");
                        order.setStatus("CO_INSPECT_WAIT_QC");
                        order.setNote((order.getNote() != null ? order.getNote() + "\n" : "") + "[Keeper " + userId
                                        + "] Đã đồng ý Đồng kiểm (" + LocalDateTime.now() + ")");

                        // Notify QC
                        try {
                                notificationService.notifyRole("QC", "co_inspect_wait_qc",
                                                id, order.getReceivingCode(), "Keeper đã xác nhận, chờ QC đồng kiểm");
                        } catch (Exception ignored) {
                        }
                } else if ("QC".equals(role)) {
                        if (!"CO_INSPECT_WAIT_QC".equals(order.getStatus())) {
                                throw new org.example.sep26management.infrastructure.exception.BusinessException(
                                                "Chỉ có thể xác nhận khi trạng thái là CO_INSPECT_WAIT_QC (hiện tại: "
                                                                + order.getStatus() + ")");
                        }
                        order.setStatus("CO_INSPECT_READY");
                        order.setNote((order.getNote() != null ? order.getNote() + "\n" : "") + "[QC " + userId
                                        + "] Đã đồng ý Đồng kiểm (" + LocalDateTime.now() + ")");
                        order.setAssignedQcId(userId); // Lock to this QC explicitly

                        // Notify Keeper
                        try {
                                userRepo.findById(order.getCreatedBy())
                                                .ifPresent(u -> notificationService.notifyUser(u.getEmail(),
                                                                "co_inspect_ready",
                                                                id, order.getReceivingCode(),
                                                                "Đã sẵn sàng. Hai bên vui lòng gặp mặt để bắt đầu"));
                        } catch (Exception ignored) {
                        }
                } else {
                        throw new org.example.sep26management.infrastructure.exception.BusinessException(
                                        "Vai trò không hợp lệ cho thao tác này: " + role);
                }

                order.setUpdatedAt(LocalDateTime.now());
                receivingOrderRepo.save(order);
                return getOrder(id);
        }

        @Transactional
        public ApiResponse<org.example.sep26management.application.dto.response.GrnResponse> generateGrn(Long id,
                        org.example.sep26management.application.dto.request.GrnGenerateRequest request,
                        Long userId) {
                ReceivingOrderEntity order = findOrderForUpdate(id); // SELECT FOR UPDATE

                // Update dates if provided in request
                if (request != null && request.getItemDates() != null) {
                        for (org.example.sep26management.application.dto.request.GrnGenerateRequest.ItemDateSync dateSync : request
                                        .getItemDates()) {
                                order.getItems().stream()
                                                .filter(item -> item.getReceivingItemId()
                                                                .equals(dateSync.getReceivingItemId()))
                                                .findFirst()
                                                .ifPresent(item -> {
                                                        item.setManufactureDate(dateSync.getManufactureDate());
                                                        item.setExpiryDate(dateSync.getExpiryDate());
                                                });
                        }
                }

                if (!"QC_APPROVED".equals(order.getStatus())) {
                        throw new org.example.sep26management.infrastructure.exception.BusinessException(
                                        "Chỉ có thể tạo GRN từ Phiếu nhận hàng đã QC_APPROVED. Trạng thái hiện tại: "
                                                        + order.getStatus());
                }

                // BE-C2 FIX: Guard chống tạo GRN trùng — mỗi receivingId chỉ được có 1 GRN
                // active
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

                // Only collect returnQty from QC QUALITY incidents (actual physical damage),
                // NOT from SHORTAGE/OVERAGE/DISCREPANCY discrepancy incidents (those are
                // already resolved
                // by adjusting receivedQty in resolveDiscrepancy)
                List<IncidentItemEntity> qcDamageItems = new ArrayList<>();
                for (IncidentEntity inc : incidents) {
                        if ("RESOLVED".equals(inc.getStatus())
                                        && inc.getIncidentType() != null
                                        && !org.example.sep26management.application.enums.IncidentType.SHORTAGE
                                                        .equals(inc.getIncidentType())
                                        && !org.example.sep26management.application.enums.IncidentType.OVERAGE
                                                        .equals(inc.getIncidentType())
                                        && !org.example.sep26management.application.enums.IncidentType.DISCREPANCY
                                                        .equals(inc.getIncidentType())) {
                                qcDamageItems.addAll(incidentItemRepo.findByIncidentIncidentId(inc.getIncidentId()));
                        }
                }

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
                                // Trước đây set PENDING_APPROVAL ngay → submitToManager() luôn fail (BUG-04
                                // context).
                                .status("GRN_CREATED")
                                .createdBy(userId)
                                .build();
                GrnEntity savedGrn = grnRepo.save(grn);

                // ═══════════════════════════════════════════════════════════════════════
                // Bước 1: Gộp theo (skuId, lotNumber) — tổng qty
                java.util.Map<String, BigDecimal> skuLotQtyMap = new java.util.LinkedHashMap<>();
                java.util.Map<String, ReceivingItemEntity> skuLotBestItemMap = new java.util.LinkedHashMap<>();

                for (ReceivingItemEntity item : items) {
                        if ("RETURNED".equals(item.getCondition())) {
                                log.info("generateGrn: skipping RETURNED item skuId={}", item.getSkuId());
                                continue;
                        }
                        Long skuId = item.getSkuId();
                        String lot = item.getLotNumber();
                        BigDecimal receivedQty = item.getReceivedQty() != null ? item.getReceivedQty()
                                        : BigDecimal.ZERO;
                        if (receivedQty.compareTo(BigDecimal.ZERO) <= 0) {
                                log.info("generateGrn: skipping zero-qty item skuId={}, lot={}", skuId, lot);
                                continue;
                        }

                        // Key = skuId_lotNumber (lot có thể null/blank)
                        String key = skuId + "_" + (lot == null ? "" : lot.trim());

                        // Cộng dồn quantity
                        skuLotQtyMap.merge(key, receivedQty, BigDecimal::add);

                        // Giữ row đầu tiên để lấy expiryDate/manufactureDate nếu có
                        ReceivingItemEntity current = skuLotBestItemMap.get(key);
                        if (current == null
                                        || (current.getExpiryDate() == null && item.getExpiryDate() != null)) {
                                skuLotBestItemMap.put(key, item);
                        }
                }

                // Bước 2: Tạo GRN item riêng cho mỗi (SKU + Lot)
                for (java.util.Map.Entry<String, BigDecimal> entry : skuLotQtyMap.entrySet()) {
                        String key = entry.getKey();
                        Long skuId = Long.parseLong(key.split("_")[0]);
                        BigDecimal finalPassQty = entry.getValue();

                        if (finalPassQty.compareTo(BigDecimal.ZERO) <= 0)
                                continue;

                        ReceivingItemEntity bestItem = skuLotBestItemMap.get(key);

                        // Auto-calculate lot/date if missing
                        String lotNumber = bestItem.getLotNumber();
                        LocalDate manufactureDate = bestItem.getManufactureDate();
                        LocalDate expiryDate = bestItem.getExpiryDate();

                        SkuEntity sku = skuRepo.findById(skuId).orElse(null);

                        if (lotNumber == null || lotNumber.isBlank()) {
                                String skuCode = sku != null ? sku.getSkuCode() : String.valueOf(skuId);
                                lotNumber = "LOT-" + grnCode + "-" + skuCode;
                        }

                        if (manufactureDate == null) {
                                manufactureDate = LocalDate.now();
                        }

                        if (expiryDate == null && sku != null && sku.getShelfLifeDays() != null
                                        && sku.getShelfLifeDays() > 0) {
                                expiryDate = manufactureDate.plusDays(sku.getShelfLifeDays());
                        }

                        // Cập nhật tất cả receiving items của (SKU + Lot) này với lot/date
                        // (record-keeping)
                        final String finalLotNumber = lotNumber;
                        final LocalDate finalMfgDate = manufactureDate;
                        final LocalDate finalExpDate = expiryDate;

                        String originalLot = bestItem.getLotNumber();

                        items.stream()
                                        .filter(i -> i.getSkuId().equals(skuId)
                                                        && (originalLot == null ? i.getLotNumber() == null
                                                                        : originalLot.equals(i.getLotNumber()))
                                                        && !"RETURNED".equals(i.getCondition()))
                                        .forEach(i -> {
                                                if (i.getLotNumber() == null || i.getLotNumber().isBlank())
                                                        i.setLotNumber(finalLotNumber);
                                                if (i.getManufactureDate() == null)
                                                        i.setManufactureDate(finalMfgDate);
                                                if (i.getExpiryDate() == null)
                                                        i.setExpiryDate(finalExpDate);
                                                receivingItemRepo.save(i);
                                        });

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

                        log.info("generateGrn: SKU {} Lot {} → qty={}", skuId, lotNumber, finalPassQty);
                }

                order.setStatus("GRN_CREATED");
                receivingOrderRepo.save(order);

                // ── Realtime: notify KEEPER (người tạo đơn) + broadcast KEEPER GRN đã tạo
                // xong, cần gửi Manager ─
                String supGrn = order.getSupplierId() != null
                                ? supplierRepo.findById(order.getSupplierId()).map(s -> s.getSupplierName()).orElse("—")
                                : "—";
                final String generateGrnSubtitle = supGrn + " — GRN đã tạo, cần gửi Manager";
                userRepo.findById(order.getCreatedBy())
                                .ifPresent(u -> notificationService.notifyUser(u.getEmail(), "grn_create_ready",
                                                order.getReceivingId(), order.getReceivingCode(), generateGrnSubtitle));
                notificationService.notifyRole("KEEPER", "grn_create_ready",
                                order.getReceivingId(), order.getReceivingCode(), generateGrnSubtitle);

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

        /**
         * findOrder với SELECT FOR UPDATE — dùng trong cảc phương thức thay đổi status.
         * Giú: đảm bảo chỉ 1 transaction có thể đọc và commit thay đổi ở một thời điểm.
         * Nếu 2 request cùng submit/finalize đồng thời: T2 bị block cho đến khi T1
         * commit
         * → T2 thấy status mới → validateStatus fail → BusinessException (không silent
         * nếa).
         */
        private ReceivingOrderEntity findOrderForUpdate(Long id) {
                return receivingOrderRepo.findByIdForUpdate(id)
                                .orElseThrow(() -> new org.example.sep26management.infrastructure.exception.BusinessException(
                                                "Receiving order not found: " + id));
        }

        /**
         * Kiểm tra quyền sở hữu: Keeper chỉ được thao tác đơn do mình tạo.
         * userId = null → bỏ qua kiểm tra (Manager/QC không bị giới hạn).
         * userId = 0L → scanner token không có userId → bỏ qua.
         */
        private void validateOwnership(ReceivingOrderEntity order, Long userId, String action) {
                if (userId == null || userId == 0L)
                        return; // Manager/QC hoặc scanner → pass
                if (!userId.equals(order.getCreatedBy())) {
                        throw new org.example.sep26management.infrastructure.exception.BusinessException(
                                        "Không có quyền " + action + " phiếu #" + order.getReceivingId()
                                                        + " — phiếu n㣊y do Keeper khác tạo.");
                }
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
                                .assignedQcId(o.getAssignedQcId())
                                .assignedQcName(o.getAssignedQcId() != null
                                                ? userRepo.findById(o.getAssignedQcId())
                                                                .map(UserEntity::getFullName).orElse(null)
                                                : null)
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
                                .qcRequired(item.getQcRequired())
                                .build();
        }

        // ─── Z-INB helper
        // ─────────────────────────────────────────────────────────────
        /**
         * Khi đơn hàng inbound chuyển sang PENDING_COUNT:
         * Cộng tồn kho vào staging location (Z-INB) cho từng mặt hàng trong đơn.
         * Tồn này sẽ được trừ khỏi staging và cộng vào BIN đích khi Keeper confirm
         * putaway.
         *
         * Nghiệp vụ:
         * PENDING_COUNT → upsertInventory(stagingLocation, +qty) [Z-INB]
         * confirmPutaway → decrementQuantity(stagingLocation, -qty) [trừ Z-INB]
         * + upsertInventory(binLocation, +qty) [cộng vào BIN]
         */
        private void addInboundStockToStaging(ReceivingOrderEntity order, Long userId) {
                try {
                        Long stagingLocationId = getFirstStagingLocationId(order.getWarehouseId());
                        if (stagingLocationId == null) {
                                log.warn("Z-INB: No staging location found for warehouse {}. Skipping Z-INB stock addition.",
                                                order.getWarehouseId());
                                return;
                        }

                        List<ReceivingItemEntity> items = receivingItemRepo
                                        .findByReceivingOrderReceivingId(order.getReceivingId());
                        if (items == null || items.isEmpty())
                                return;

                        for (ReceivingItemEntity item : items) {
                                if (item.getExpectedQty() == null
                                                || item.getExpectedQty().compareTo(BigDecimal.ZERO) <= 0)
                                        continue;

                                // Upsert vào inventory_snapshot tại staging location
                                jdbcTemplate.update(
                                                "INSERT INTO inventory_snapshot (warehouse_id, sku_id, lot_id, location_id, quantity, reserved_qty) "
                                                                +
                                                                "VALUES (?, ?, NULL, ?, ?, 0) " +
                                                                // FIX: phải dùng generated column lot_id_safe thay vì
                                                                // expression COALESCE(lot_id,0)
                                                                // vì ON CONFLICT phải khớp đúng tên cột trong PRIMARY
                                                                // KEY constraint
                                                                "ON CONFLICT (warehouse_id, sku_id, lot_id_safe, location_id) "
                                                                +
                                                                "DO UPDATE SET quantity = inventory_snapshot.quantity + EXCLUDED.quantity, "
                                                                +
                                                                "last_updated = NOW()",
                                                order.getWarehouseId(), item.getSkuId(), stagingLocationId,
                                                item.getExpectedQty());

                                // Ghi inventory transaction type = RECEIVING_PENDING
                                jdbcTemplate.update(
                                                "INSERT INTO inventory_transactions " +
                                                                "(warehouse_id, sku_id, lot_id, location_id, quantity, txn_type, reference_table, reference_id, created_by) "
                                                                +
                                                                "VALUES (?, ?, NULL, ?, ?, 'RECEIVING_PENDING', 'receiving_orders', ?, ?)",
                                                order.getWarehouseId(), item.getSkuId(), stagingLocationId,
                                                item.getExpectedQty(), order.getReceivingId(), userId);
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
                        if (!ids.isEmpty())
                                return ids.get(0);
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