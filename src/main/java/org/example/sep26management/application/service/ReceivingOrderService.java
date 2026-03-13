package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.PageResponse;
import org.example.sep26management.application.dto.response.PutawaySuggestion;
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

        // ─── Update Lines ──────────────────────────────────────────────────────────

        @Transactional
        public ApiResponse<ReceivingOrderResponse> updateLines(Long id,
                        org.example.sep26management.application.dto.request.UpdateReceivingLinesRequest request,
                        Long userId) {
                ReceivingOrderEntity order = findOrder(id);

                if (!"DRAFT".equals(order.getStatus()) && !"RECEIVED".equals(order.getStatus())
                                && !"VERIFIED".equals(order.getStatus())
                                && !"PENDING_INCIDENT".equals(order.getStatus())) {
                        throw new RuntimeException("Cannot update lines for GRN in status '" + order.getStatus() + "'");
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

        // ─── Submit ────────────────────────────────────────────────────────────────

        @Transactional
        public ApiResponse<ReceivingOrderResponse> submit(Long id, Long userId) {
                ReceivingOrderEntity order = findOrder(id);
                validateStatus(order, "submit", "DRAFT");

                // --- Sync from active session if exists (Failsafe for mobile scans) ---
                Optional<String> activeSessionId = sessionRedis.findActiveSession(order.getWarehouseId(), userId);
                if (activeSessionId.isPresent()) {
                        sessionRedis.findById(activeSessionId.get()).ifPresent(sessionData -> {
                                List<ScanLineItem> sessionLines = sessionData.getLines();
                                if (sessionLines != null && !sessionLines.isEmpty()) {
                                        log.info("Syncing {} lines from session {} into order {} before submit",
                                                        sessionLines.size(), activeSessionId.get(), id);
                                        for (ScanLineItem sLine : sessionLines) {
                                                receivingItemRepo
                                                                .findByReceivingOrderReceivingIdAndSkuId(id,
                                                                                sLine.getSkuId())
                                                                .ifPresent(ri -> {
                                                                        if (sLine.getQty() != null) {
                                                                                ri.setReceivedQty(sLine.getQty());
                                                                                ri.setCondition(sLine
                                                                                                .getCondition() != null
                                                                                                                ? sLine.getCondition()
                                                                                                                : "PASS");
                                                                                ri.setReasonCode(sLine.getReasonCode());
                                                                                ri.setLotNumber(sLine.getLotNumber());
                                                                                ri.setManufactureDate(sLine
                                                                                                .getManufactureDate());
                                                                                ri.setExpiryDate(sLine.getExpiryDate());
                                                                                receivingItemRepo.save(ri);
                                                                        }
                                                                });
                                        }
                                }
                        });
                }
                // ----------------------------------------------------------------------

                List<ReceivingItemEntity> items = receivingItemRepo.findByReceivingOrderReceivingId(id);
                boolean hasDiscrepancy = false;
                List<IncidentItemEntity> incidentItems = new ArrayList<>();

                for (ReceivingItemEntity item : items) {
                        BigDecimal expectedQty = item.getExpectedQty() != null ? item.getExpectedQty()
                                        : BigDecimal.ZERO;
                        BigDecimal receivedQty = item.getReceivedQty() != null ? item.getReceivedQty()
                                        : BigDecimal.ZERO;

                        if (expectedQty.compareTo(receivedQty) != 0) {
                                hasDiscrepancy = true;
                                BigDecimal diffQty = expectedQty.subtract(receivedQty).abs();
                                String typeStr = expectedQty.compareTo(receivedQty) > 0 ? "SHORTAGE" : "OVERAGE";

                                log.warn("Discrepancy detected for SKU {}: Expected={}, Received={}, Type={}",
                                                item.getSkuId(), expectedQty, receivedQty, typeStr);

                                IncidentItemEntity incItem = IncidentItemEntity.builder()
                                                .skuId(item.getSkuId())
                                                .damagedQty(diffQty)
                                                .expectedQty(expectedQty)
                                                .actualQty(receivedQty)
                                                .note("Hệ thống tự động ghi nhận "
                                                                + (typeStr.equals("SHORTAGE") ? "thiếu" : "thừa")
                                                                + " hàng khi Keeper trình duyệt")
                                                .actionPassQty(BigDecimal.ZERO)
                                                .actionReturnQty(BigDecimal.ZERO)
                                                .actionScrapQty(BigDecimal.ZERO)
                                                .reasonCode(typeStr)
                                                .build();
                                incidentItems.add(incItem);
                        }
                }

                if (hasDiscrepancy) {
                        // Check if it's shortage or overage for the main incident type
                        boolean isAllShortage = incidentItems.stream()
                                        .allMatch(i -> "SHORTAGE".equals(i.getReasonCode()));
                        org.example.sep26management.application.enums.IncidentType mainType = isAllShortage
                                        ? org.example.sep26management.application.enums.IncidentType.SHORTAGE
                                        : org.example.sep26management.application.enums.IncidentType.OVERAGE;

                        IncidentEntity incident = IncidentEntity.builder()
                                        .warehouseId(order.getWarehouseId())
                                        .incidentType(mainType)
                                        .category(org.example.sep26management.application.enums.IncidentCategory.GATE)
                                        .incidentCode("INC-GATE-" + System.currentTimeMillis() % 10000)
                                        .description("Hệ thống tự động tạo sự cố do phát hiện sai lệch số lượng (Thiếu/Thừa) khi Keeper trình duyệt.")
                                        .receivingId(id)
                                        .status("OPEN")
                                        .reportedBy(userId)
                                        .build();

                        IncidentEntity savedIncident = incidentRepo.save(incident);

                        for (IncidentItemEntity incItem : incidentItems) {
                                incItem.setIncident(savedIncident);
                                incidentItemRepo.save(incItem);
                        }
                        order.setStatus("PENDING_INCIDENT");

                        // Audit log: submitted with discrepancy
                        auditLogService.logAction(
                                userId,
                                "RECEIVING_SUBMITTED_WITH_INCIDENT",
                                "RECEIVING_ORDER",
                                order.getReceivingId(),
                                "Receiving Order " + order.getReceivingCode()
                                        + " submitted with discrepancy. Incident ID: "
                                        + savedIncident.getIncidentId(),
                                null, null);

                        log.info("GRN {} submitted with Discrepancy. Created Incident {} by userId={}",
                                        order.getReceivingCode(), savedIncident.getIncidentId(), userId);
                } else {
                        order.setStatus("SUBMITTED");
                        log.info("GRN {} submitted (Full received) by userId={}", order.getReceivingCode(), userId);
                }

                order.setUpdatedAt(LocalDateTime.now());
                receivingOrderRepo.save(order);

                return ApiResponse.success("GRN submitted successfully", getOrder(id).getData());
        }

        // ─── QC Approve ──────────────────────────────────────────────────────────

        @Transactional
        public ApiResponse<ReceivingOrderResponse> qcApprove(Long id, Long qcUserId) {
                ReceivingOrderEntity order = findOrder(id);
                validateStatus(order, "qc-approve", "SUBMITTED", "PENDING_INCIDENT");

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
                validateStatus(order, "qc-submit-session", "SUBMITTED", "PENDING_INCIDENT");

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

        // ─── Approve ───────────────────────────────────────────────────────────────

        @Transactional
        public ApiResponse<ReceivingOrderResponse> approve(Long id, Long managerId) {
                throw new UnsupportedOperationException("Approve operation has been moved to GRN flow");
        }

        // ─── Reject ────────────────────────────────────────────────────────────────

        @Transactional
        public ApiResponse<ReceivingOrderResponse> reject(Long id, String reason, Long userId) {
                throw new UnsupportedOperationException("Reject operation has been moved to GRN flow");
        }

        // ─── Generate GRN ──────────────────────────────────────────────────────────

        @Transactional
        public ApiResponse<org.example.sep26management.application.dto.response.GrnResponse> generateGrn(Long id,
                        Long userId) {
                ReceivingOrderEntity order = findOrder(id);

                if (!"QC_APPROVED".equals(order.getStatus())) {
                        throw new org.example.sep26management.infrastructure.exception.BusinessException(
                                        "Can only generate GRN from QC_APPROVED Receiving Order. Current status: "
                                                        + order.getStatus());
                }

                // Kiểm tra xem có Incident nào chưa RESOLVED không
                List<IncidentEntity> incidents = incidentRepo.findByReceivingIdOrderByCreatedAtDesc(id);
                boolean hasUnsettled = incidents.stream()
                                .anyMatch(i -> "OPEN".equals(i.getStatus()) || "APPROVED".equals(i.getStatus()));
                if (hasUnsettled) {
                        throw new org.example.sep26management.infrastructure.exception.BusinessException(
                                        "Cannot generate GRN: there are unsettled incidents.");
                }

                // Tính toán số lượng GRN (Pass/Nhập kho) cho từng SKU
                List<ReceivingItemEntity> items = receivingItemRepo.findByReceivingOrderReceivingId(id);
                List<IncidentItemEntity> allIncidentItems = new ArrayList<>();
                for (IncidentEntity inc : incidents) {
                        // Chỉ tính những incident đã RESOLVED
                        if ("RESOLVED".equals(inc.getStatus())) {
                                allIncidentItems.addAll(incidentItemRepo.findByIncidentIncidentId(inc.getIncidentId()));
                        }
                }

                // Map skuId -> tổng damagedQty, actionPassQty
                Map<Long, BigDecimal> skuDamagedMap = allIncidentItems.stream()
                                .collect(Collectors.groupingBy(IncidentItemEntity::getSkuId,
                                                Collectors.reducing(BigDecimal.ZERO,
                                                                i -> i.getDamagedQty() != null ? i.getDamagedQty()
                                                                                : BigDecimal.ZERO,
                                                                BigDecimal::add)));

                Map<Long, BigDecimal> skuManagerPassMap = allIncidentItems.stream()
                                .collect(Collectors.groupingBy(IncidentItemEntity::getSkuId,
                                                Collectors.reducing(BigDecimal.ZERO,
                                                                i -> i.getActionPassQty() != null ? i.getActionPassQty()
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
                                .status("PENDING_APPROVAL")
                                .createdBy(userId)
                                .build();
                GrnEntity savedGrn = grnRepo.save(grn);

                for (ReceivingItemEntity item : items) {
                        Long skuId = item.getSkuId();
                        BigDecimal receivedQty = item.getReceivedQty() != null ? item.getReceivedQty()
                                        : BigDecimal.ZERO;
                        BigDecimal damagedQty = skuDamagedMap.getOrDefault(skuId, BigDecimal.ZERO);
                        BigDecimal managerPassQty = skuManagerPassMap.getOrDefault(skuId, BigDecimal.ZERO);

                        BigDecimal goodQty = receivedQty.subtract(damagedQty);
                        if (goodQty.compareTo(BigDecimal.ZERO) < 0)
                                goodQty = BigDecimal.ZERO;

                        BigDecimal finalPassQty = goodQty.add(managerPassQty);

                        if (finalPassQty.compareTo(BigDecimal.ZERO) > 0) {
                                GrnItemEntity grnItem = GrnItemEntity.builder()
                                                .grn(savedGrn)
                                                .skuId(skuId)
                                                .quantity(finalPassQty)
                                                .lotNumber(item.getLotNumber())
                                                .manufactureDate(item.getManufactureDate())
                                                .expiryDate(item.getExpiryDate())
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

        // ─── Post ──────────────────────────────────────────────────────────────────

        @Transactional
        public ApiResponse<ReceivingOrderResponse> post(Long id, Long accountantId) {
                throw new UnsupportedOperationException("Post operation has been moved to GRN flow");
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

                return ReceivingOrderResponse.builder()
                                .receivingId(o.getReceivingId())
                                .receivingCode(o.getReceivingCode())
                                .status(o.getStatus())
                                .warehouseId(o.getWarehouseId())
                                .supplierId(o.getSupplierId())
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
}
