package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.application.dto.request.*;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.OutboundResponse;
import org.example.sep26management.application.enums.OutboundType;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.example.sep26management.infrastructure.exception.ResourceNotFoundException;
import org.example.sep26management.infrastructure.persistence.entity.*;
import org.example.sep26management.infrastructure.persistence.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboundService {

    private final SalesOrderJpaRepository soRepository;
    private final SalesOrderItemJpaRepository soItemRepository;
    private final TransferJpaRepository transferRepository;
    private final TransferItemJpaRepository transferItemRepository;
    private final CustomerJpaRepository customerRepository;
    private final WarehouseJpaRepository warehouseRepository;
    private final InventorySnapshotJpaRepository snapshotRepository;
    private final ReservationJpaRepository reservationRepository;
    private final InventoryTransactionJpaRepository txnRepository;
    private final SkuJpaRepository skuRepository;
    private final AuditLogService auditLogService;
    private final LocationJpaRepository locationRepository;

    // ─────────────────────────────────────────────────────────────
    // SCRUM-505: UC-OUT-01 Create Outbound Order
    // BR-OUT-01: order type determines fields
    // BR-OUT-02: delivery/transfer date >= today
    // BR-OUT-03: real-time availability check
    // BR-OUT-04: warn if qty > available
    // BR-OUT-05: document code format
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<OutboundResponse> createOutbound(
            CreateOutboundRequest request,
            Long createdBy, String ip, String ua) {

        log.info("Creating outbound: type={}, warehouse={}", request.getOrderType(), request.getWarehouseId());

        warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.WAREHOUSE_NOT_FOUND, request.getWarehouseId())));

        validateRequiredFieldsByType(request);

        OutboundResponse response;
        if (request.getOrderType() == OutboundType.SALES_ORDER) {
            response = createSalesOrder(request, createdBy, ip, ua);
        } else {
            response = createInternalTransfer(request, createdBy, ip, ua);
        }
        return ApiResponse.success(MessageConstants.OUTBOUND_CREATED_SUCCESS, response);
    }

    private OutboundResponse createSalesOrder(CreateOutboundRequest req, Long createdBy, String ip, String ua) {
        CustomerEntity customer = customerRepository.findById(req.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.CUSTOMER_NOT_FOUND, req.getCustomerId())));

        if (req.getDeliveryDate() != null && req.getDeliveryDate().isBefore(LocalDate.now())) {
            throw new BusinessException(MessageConstants.OUTBOUND_DATE_MUST_BE_FUTURE);
        }

        List<OutboundResponse.StockWarning> warnings = checkStockAvailability(
                req.getWarehouseId(), req.getItems());

        String code = generateDocCode("EXP-SAL", req.getWarehouseId(), true);

        SalesOrderEntity so = SalesOrderEntity.builder()
                .warehouseId(req.getWarehouseId())
                .customerId(req.getCustomerId())
                .soCode(code)
                .status("DRAFT")
                .requiredShipDate(req.getDeliveryDate())
                .note(req.getNote())
                .createdBy(createdBy)
                .build();

        SalesOrderEntity saved = soRepository.save(so);

        List<SalesOrderItemEntity> items = req.getItems().stream()
                .map(i -> SalesOrderItemEntity.builder()
                        .soId(saved.getSoId())
                        .skuId(i.getSkuId())
                        .orderedQty(i.getQuantity())
                        .note(i.getNote())
                        .build())
                .toList();
        soItemRepository.saveAll(items);

        auditLogService.logAction(createdBy, "OUTBOUND_CREATED", "SALES_ORDER", saved.getSoId(),
                "Sales order " + code + " created DRAFT", ip, ua);

        return buildSalesOrderResponse(saved, items, customer, warnings);
    }

    private OutboundResponse createInternalTransfer(CreateOutboundRequest req, Long createdBy, String ip, String ua) {
        WarehouseEntity destWarehouse = warehouseRepository.findById(req.getDestinationWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.WAREHOUSE_NOT_FOUND, req.getDestinationWarehouseId())));

        if (req.getDestinationWarehouseId().equals(req.getWarehouseId())) {
            throw new BusinessException(MessageConstants.OUTBOUND_SAME_WAREHOUSE);
        }

        if (req.getTransferDate() != null && req.getTransferDate().isBefore(LocalDate.now())) {
            throw new BusinessException(MessageConstants.OUTBOUND_DATE_MUST_BE_FUTURE);
        }

        List<OutboundResponse.StockWarning> warnings = checkStockAvailability(
                req.getWarehouseId(), req.getItems());

        String code = generateDocCode("EXP-INT", req.getWarehouseId(), false);

        TransferEntity transfer = TransferEntity.builder()
                .fromWarehouseId(req.getWarehouseId())
                .toWarehouseId(req.getDestinationWarehouseId())
                .transferCode(code)
                .status("DRAFT")
                .note(req.getNote())
                .createdBy(createdBy)
                .build();

        TransferEntity saved = transferRepository.save(transfer);

        List<TransferItemEntity> items = req.getItems().stream()
                .map(i -> TransferItemEntity.builder()
                        .transferId(saved.getTransferId())
                        .skuId(i.getSkuId())
                        .quantity(i.getQuantity())
                        .build())
                .toList();
        transferItemRepository.saveAll(items);

        auditLogService.logAction(createdBy, "OUTBOUND_CREATED", "TRANSFER", saved.getTransferId(),
                "Internal transfer " + code + " created DRAFT", ip, ua);

        return buildTransferResponse(saved, items, destWarehouse, warnings);
    }

    // ─────────────────────────────────────────────────────────────
    // SCRUM-506: UC-OUT-02 Update Outbound Order
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<OutboundResponse> updateOutbound(
            OutboundType type, Long documentId,
            UpdateOutboundRequest request,
            Long userId, String ip, String ua) {

        log.info("Updating outbound: type={}, id={}", type, documentId);

        if (type == OutboundType.SALES_ORDER) {
            return updateSalesOrder(documentId, request, userId, ip, ua);
        } else {
            return updateTransfer(documentId, request, userId, ip, ua);
        }
    }

    private ApiResponse<OutboundResponse> updateSalesOrder(
            Long soId, UpdateOutboundRequest req, Long userId, String ip, String ua) {

        SalesOrderEntity so = soRepository.findById(soId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.OUTBOUND_NOT_FOUND, soId)));

        if (!"DRAFT".equals(so.getStatus())) {
            throw new BusinessException(MessageConstants.OUTBOUND_NOT_EDITABLE);
        }

        if (!so.getCreatedBy().equals(userId)) {
            throw new BusinessException(MessageConstants.OUTBOUND_NOT_CREATOR);
        }

        if (req.getDeliveryDate() != null && req.getDeliveryDate().isBefore(LocalDate.now())) {
            throw new BusinessException(MessageConstants.OUTBOUND_DATE_MUST_BE_FUTURE);
        }

        if (req.getCustomerId() != null) so.setCustomerId(req.getCustomerId());
        if (req.getDeliveryDate() != null) so.setRequiredShipDate(req.getDeliveryDate());
        if (req.getNote() != null) so.setNote(req.getNote());
        soRepository.save(so);

        soItemRepository.deleteBySoId(soId);
        List<SalesOrderItemEntity> newItems = req.getItems().stream()
                .map(i -> SalesOrderItemEntity.builder()
                        .soId(soId).skuId(i.getSkuId())
                        .orderedQty(i.getQuantity()).note(i.getNote())
                        .build())
                .toList();
        soItemRepository.saveAll(newItems);

        List<OutboundResponse.StockWarning> warnings = checkStockAvailability(
                so.getWarehouseId(), req.getItems().stream()
                        .map(i -> new CreateOutboundRequest.OutboundItemRequest(i.getSkuId(), i.getQuantity(), i.getNote()))
                        .toList());

        auditLogService.logAction(userId, "OUTBOUND_UPDATED", "SALES_ORDER", soId,
                "Sales order " + so.getSoCode() + " updated", ip, ua);

        CustomerEntity customer = customerRepository.findById(so.getCustomerId()).orElse(null);
        return ApiResponse.success(MessageConstants.OUTBOUND_UPDATED_SUCCESS,
                buildSalesOrderResponse(so, newItems, customer, warnings));
    }

    private ApiResponse<OutboundResponse> updateTransfer(
            Long transferId, UpdateOutboundRequest req, Long userId, String ip, String ua) {

        TransferEntity transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.OUTBOUND_NOT_FOUND, transferId)));

        if (!"DRAFT".equals(transfer.getStatus())) {
            throw new BusinessException(MessageConstants.OUTBOUND_NOT_EDITABLE);
        }

        if (!transfer.getCreatedBy().equals(userId)) {
            throw new BusinessException(MessageConstants.OUTBOUND_NOT_CREATOR);
        }

        if (req.getTransferDate() != null && req.getTransferDate().isBefore(LocalDate.now())) {
            throw new BusinessException(MessageConstants.OUTBOUND_DATE_MUST_BE_FUTURE);
        }

        if (req.getDestinationWarehouseId() != null) transfer.setToWarehouseId(req.getDestinationWarehouseId());
        if (req.getNote() != null) transfer.setNote(req.getNote());
        transferRepository.save(transfer);

        transferItemRepository.deleteByTransferId(transferId);
        List<TransferItemEntity> newItems = req.getItems().stream()
                .map(i -> TransferItemEntity.builder()
                        .transferId(transferId).skuId(i.getSkuId()).quantity(i.getQuantity())
                        .build())
                .toList();
        transferItemRepository.saveAll(newItems);

        List<OutboundResponse.StockWarning> warnings = checkStockAvailability(
                transfer.getFromWarehouseId(), req.getItems().stream()
                        .map(i -> new CreateOutboundRequest.OutboundItemRequest(i.getSkuId(), i.getQuantity(), i.getNote()))
                        .toList());

        auditLogService.logAction(userId, "OUTBOUND_UPDATED", "TRANSFER", transferId,
                "Transfer " + transfer.getTransferCode() + " updated", ip, ua);

        WarehouseEntity dest = warehouseRepository.findById(transfer.getToWarehouseId()).orElse(null);
        return ApiResponse.success(MessageConstants.OUTBOUND_UPDATED_SUCCESS,
                buildTransferResponse(transfer, newItems, dest, warnings));
    }

    // ─────────────────────────────────────────────────────────────
    // SCRUM-507: UC-OUT-03 Submit Outbound Order
    // BR-OUT-09: hard block if insufficient stock
    // BR-OUT-10: final stock check at submission
    // BR-OUT-11: sales order → PENDING_APPROVAL
    // BR-OUT-12: internal transfer → auto-approve
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<OutboundResponse> submitOutbound(
            OutboundType type, Long documentId,
            SubmitOutboundRequest request,
            Long userId, String ip, String ua) {

        log.info("Submitting outbound: type={}, id={}", type, documentId);

        if (type == OutboundType.SALES_ORDER) {
            return submitSalesOrder(documentId, request, userId, ip, ua);
        } else {
            return submitTransfer(documentId, request, userId, ip, ua);
        }
    }

    private ApiResponse<OutboundResponse> submitSalesOrder(
            Long soId, SubmitOutboundRequest req, Long userId, String ip, String ua) {

        SalesOrderEntity so = soRepository.findById(soId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.OUTBOUND_NOT_FOUND, soId)));

        if (!"DRAFT".equals(so.getStatus())) {
            throw new BusinessException(MessageConstants.OUTBOUND_NOT_DRAFT);
        }

        if (!so.getCreatedBy().equals(userId)) {
            throw new BusinessException(MessageConstants.OUTBOUND_NOT_CREATOR);
        }

        List<SalesOrderItemEntity> items = soItemRepository.findBySoId(soId);

        validateSufficientStock(so.getWarehouseId(), items.stream()
                .map(i -> new AbstractMap.SimpleEntry<>(i.getSkuId(), i.getOrderedQty()))
                .toList());

        if (req.getNote() != null) so.setNote(req.getNote());
        so.setStatus("PENDING_APPROVAL");
        soRepository.save(so);

        auditLogService.logAction(userId, "OUTBOUND_SUBMITTED", "SALES_ORDER", soId,
                "Sales order " + so.getSoCode() + " submitted for approval", ip, ua);

        CustomerEntity customer = customerRepository.findById(so.getCustomerId()).orElse(null);
        return ApiResponse.success(MessageConstants.OUTBOUND_SUBMITTED_SUCCESS,
                buildSalesOrderResponse(so, items, customer, null));
    }

    private ApiResponse<OutboundResponse> submitTransfer(
            Long transferId, SubmitOutboundRequest req, Long userId, String ip, String ua) {

        TransferEntity transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.OUTBOUND_NOT_FOUND, transferId)));

        if (!"DRAFT".equals(transfer.getStatus())) {
            throw new BusinessException(MessageConstants.OUTBOUND_NOT_DRAFT);
        }

        if (!transfer.getCreatedBy().equals(userId)) {
            throw new BusinessException(MessageConstants.OUTBOUND_NOT_CREATOR);
        }

        List<TransferItemEntity> items = transferItemRepository.findByTransferId(transferId);

        validateSufficientStock(transfer.getFromWarehouseId(), items.stream()
                .map(i -> new AbstractMap.SimpleEntry<>(i.getSkuId(), i.getQuantity()))
                .toList());

        if (req.getNote() != null) transfer.setNote(req.getNote());

        // BR-OUT-12: internal transfers auto-approve
        transfer.setStatus("APPROVED");
        transfer.setApprovedAt(LocalDateTime.now());
        transfer.setApprovedBy(0L);
        transferRepository.save(transfer);

        // BR-OUT-17/18: reserve inventory in single transaction
        reserveInventory(transfer.getFromWarehouseId(), items.stream()
                .map(i -> new AbstractMap.SimpleEntry<>(i.getSkuId(), i.getQuantity()))
                .toList(), "transfers", transferId, userId);

        auditLogService.logAction(userId, "OUTBOUND_AUTO_APPROVED", "TRANSFER", transferId,
                "Internal transfer " + transfer.getTransferCode() + " auto-approved", ip, ua);

        WarehouseEntity dest = warehouseRepository.findById(transfer.getToWarehouseId()).orElse(null);
        return ApiResponse.success(MessageConstants.OUTBOUND_TRANSFER_AUTO_APPROVED,
                buildTransferResponse(transfer, items, dest, null));
    }

    // ─────────────────────────────────────────────────────────────
    // SCRUM-508: UC-OUT-04 Approve Outbound Order (MANAGER only)
    // BR-OUT-14: real-time stock shown on approval screen
    // BR-OUT-15: warn if post-approval stock < 0 (no minimum field in SkuEntity)
    // BR-OUT-16: final stock check before committing
    // BR-OUT-17: reserve inventory on approval
    // BR-OUT-18: all operations in single @Transactional
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<OutboundResponse> approveSalesOrder(
            Long soId,
            ApproveOutboundRequest request,
            Long managerId, String ip, String ua) {

        log.info("Approving sales order: soId={}, managerId={}", soId, managerId);

        // ── 1. Load order ──────────────────────────────────────────
        SalesOrderEntity so = soRepository.findById(soId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.OUTBOUND_NOT_FOUND, soId)));

        // Must be PENDING_APPROVAL
        if (!"PENDING_APPROVAL".equals(so.getStatus())) {
            throw new BusinessException(MessageConstants.OUTBOUND_NOT_PENDING_APPROVAL);
        }

        List<SalesOrderItemEntity> items = soItemRepository.findBySoId(soId);

        // ── 2. BR-OUT-14: build real-time stock snapshot for response ──
        List<OutboundResponse.StockWarning> stockSnapshot = buildStockSnapshot(
                so.getWarehouseId(), items.stream()
                        .map(i -> new AbstractMap.SimpleEntry<>(i.getSkuId(), i.getOrderedQty()))
                        .toList());

        // ── 3. BR-OUT-15: warn if any item would drop available stock below 0 ──
        List<String> belowZeroWarnings = new ArrayList<>();
        for (SalesOrderItemEntity item : items) {
            BigDecimal available = getAvailableQty(so.getWarehouseId(), item.getSkuId());
            BigDecimal afterApproval = available.subtract(item.getOrderedQty());
            if (afterApproval.compareTo(BigDecimal.ZERO) < 0) {
                String skuCode = skuRepository.findById(item.getSkuId())
                        .map(s -> s.getSkuCode()).orElse("SKU#" + item.getSkuId());
                belowZeroWarnings.add(String.format(
                        "%s: available=%s, requested=%s, deficit=%s",
                        skuCode, available, item.getOrderedQty(), afterApproval.abs()));
            }
        }
        // Log warnings but DO NOT block — BR-OUT-15 is a warning, BR-OUT-16 is the hard block
        if (!belowZeroWarnings.isEmpty()) {
            log.warn("BR-OUT-15: Stock below zero warning for SO {}: {}", so.getSoCode(), belowZeroWarnings);
        }

        // ── 4. BR-OUT-16: final hard stock check before committing ──
        validateSufficientStock(so.getWarehouseId(), items.stream()
                .map(i -> new AbstractMap.SimpleEntry<>(i.getSkuId(), i.getOrderedQty()))
                .toList());

        // ── 5. BR-OUT-17 + BR-OUT-18: reserve inventory (all in this @Transactional) ──
        reserveInventory(so.getWarehouseId(), items.stream()
                .map(i -> new AbstractMap.SimpleEntry<>(i.getSkuId(), i.getOrderedQty()))
                .toList(), "sales_orders", soId, managerId);

        // ── 6. Update order status → APPROVED ──────────────────────
        so.setStatus("APPROVED");
        so.setApprovedBy(managerId);
        so.setApprovedAt(LocalDateTime.now());
        if (request != null && request.getNote() != null) so.setNote(request.getNote());
        soRepository.save(so);

        log.info("Sales order {} approved by managerId={}", so.getSoCode(), managerId);

        auditLogService.logAction(managerId, "OUTBOUND_APPROVED", "SALES_ORDER", soId,
                "Sales order " + so.getSoCode() + " approved, inventory reserved", ip, ua);

        CustomerEntity customer = customerRepository.findById(so.getCustomerId()).orElse(null);
        return ApiResponse.success(MessageConstants.OUTBOUND_APPROVED_SUCCESS,
                buildSalesOrderResponse(so, items, customer, stockSnapshot));
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private String generateDocCode(String prefix, Long warehouseId, boolean isSales) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        long seq = isSales
                ? soRepository.countTodayByWarehouse(warehouseId, startOfDay, endOfDay) + 1
                : transferRepository.countTodayByWarehouse(warehouseId, startOfDay, endOfDay) + 1;
        return String.format("%s-%s-%04d", prefix, date, seq);
    }

    private void validateRequiredFieldsByType(CreateOutboundRequest req) {
        if (req.getOrderType() == OutboundType.SALES_ORDER) {
            if (req.getCustomerId() == null)
                throw new BusinessException(MessageConstants.OUTBOUND_CUSTOMER_REQUIRED);
            if (req.getDeliveryDate() == null)
                throw new BusinessException(MessageConstants.OUTBOUND_DELIVERY_DATE_REQUIRED);
        } else {
            if (req.getDestinationWarehouseId() == null)
                throw new BusinessException(MessageConstants.OUTBOUND_DESTINATION_REQUIRED);
            if (req.getReceiverName() == null || req.getReceiverName().isBlank())
                throw new BusinessException(MessageConstants.OUTBOUND_RECEIVER_REQUIRED);
        }
    }

    private List<OutboundResponse.StockWarning> checkStockAvailability(
            Long warehouseId, List<CreateOutboundRequest.OutboundItemRequest> items) {

        List<OutboundResponse.StockWarning> warnings = new ArrayList<>();
        for (var item : items) {
            BigDecimal available = getAvailableQty(warehouseId, item.getSkuId());
            if (available.compareTo(item.getQuantity()) < 0) {
                String skuCode = skuRepository.findById(item.getSkuId())
                        .map(s -> s.getSkuCode()).orElse("SKU#" + item.getSkuId());
                warnings.add(OutboundResponse.StockWarning.builder()
                        .skuId(item.getSkuId()).skuCode(skuCode)
                        .requestedQty(item.getQuantity()).availableQty(available)
                        .message(String.format(MessageConstants.OUTBOUND_INSUFFICIENT_STOCK_WARNING,
                                skuCode, available, item.getQuantity()))
                        .build());
            }
        }
        return warnings;
    }

    /**
     * BR-OUT-14: Build real-time stock snapshot for approval screen
     * Shows current available qty for each item at time of approval
     */
    private List<OutboundResponse.StockWarning> buildStockSnapshot(
            Long warehouseId, List<AbstractMap.SimpleEntry<Long, BigDecimal>> items) {

        return items.stream().map(entry -> {
            Long skuId = entry.getKey();
            BigDecimal requestedQty = entry.getValue();
            BigDecimal available = getAvailableQty(warehouseId, skuId);
            String skuCode = skuRepository.findById(skuId)
                    .map(s -> s.getSkuCode()).orElse("SKU#" + skuId);

            return OutboundResponse.StockWarning.builder()
                    .skuId(skuId).skuCode(skuCode)
                    .requestedQty(requestedQty).availableQty(available)
                    .message(available.compareTo(requestedQty) < 0
                            ? String.format(MessageConstants.OUTBOUND_INSUFFICIENT_STOCK_WARNING,
                            skuCode, available, requestedQty)
                            : null)
                    .build();
        }).toList();
    }

    private void validateSufficientStock(Long warehouseId,
                                         List<AbstractMap.SimpleEntry<Long, BigDecimal>> items) {

        List<String> shortages = new ArrayList<>();
        for (var entry : items) {
            BigDecimal available = getAvailableQty(warehouseId, entry.getKey());
            if (available.compareTo(entry.getValue()) < 0) {
                String skuCode = skuRepository.findById(entry.getKey())
                        .map(s -> s.getSkuCode()).orElse("SKU#" + entry.getKey());
                shortages.add(String.format("%s: available=%s, requested=%s",
                        skuCode, available, entry.getValue()));
            }
        }
        if (!shortages.isEmpty()) {
            throw new BusinessException(
                    MessageConstants.OUTBOUND_INSUFFICIENT_STOCK_BLOCK + ": " + String.join("; ", shortages));
        }
    }

    /**
     * Available = snapshot.quantity - snapshot.reserved_qty (across all lots/locations)
     */
    private BigDecimal getAvailableQty(Long warehouseId, Long skuId) {
        BigDecimal total = snapshotRepository.sumQuantityByWarehouseAndSku(warehouseId, skuId);
        BigDecimal reserved = snapshotRepository.sumReservedByWarehouseAndSku(warehouseId, skuId);
        if (total == null) total = BigDecimal.ZERO;
        if (reserved == null) reserved = BigDecimal.ZERO;
        return total.subtract(reserved).max(BigDecimal.ZERO);
    }

    /**
     * BR-OUT-17/18: Reserve inventory — single transaction
     * - Tạo reservation record
     * - Tạo inventory_transaction type=RESERVE với location_id = staging location của warehouse
     * - Update snapshot.reserved_qty
     *
     * NOTE: inventory_transactions.location_id có FK → locations, không cho phép 0 hay NULL.
     *       Dùng staging location đầu tiên của warehouse làm "virtual" location cho RESERVE txn.
     */
    private void reserveInventory(Long warehouseId,
                                  List<AbstractMap.SimpleEntry<Long, BigDecimal>> items,
                                  String refTable, Long refId, Long userId) {

        // Lấy staging location id cho warehouse — dùng làm locationId cho RESERVE transaction
        Long stagingLocationId = getFirstStagingOrAnyLocationId(warehouseId);

        for (var entry : items) {
            Long skuId = entry.getKey();
            BigDecimal qty = entry.getValue();

            // 1. Reservation record (không cần locationId)
            ReservationEntity reservation = ReservationEntity.builder()
                    .warehouseId(warehouseId)
                    .skuId(skuId)
                    .quantity(qty)
                    .referenceTable(refTable)
                    .referenceId(refId)
                    .status("OPEN")
                    .build();
            reservationRepository.save(reservation);

            // 2. Inventory transaction — dùng staging location thực tế
            InventoryTransactionEntity txn = InventoryTransactionEntity.builder()
                    .warehouseId(warehouseId)
                    .skuId(skuId)
                    .locationId(stagingLocationId) // FIX: dùng location thực tế
                    .quantity(qty.negate())         // negative = reserved out
                    .txnType("RESERVE")
                    .referenceTable(refTable)
                    .referenceId(refId)
                    .createdBy(userId)
                    .build();
            txnRepository.save(txn);

            // 3. Increment reserved_qty in snapshot
            snapshotRepository.incrementReservedByWarehouseAndSku(warehouseId, skuId, qty);
        }
    }

    /**
     * Lấy staging location đầu tiên của warehouse.
     * Fallback: lấy bất kỳ location nào của warehouse nếu không có staging.
     * Dùng cho inventory_transactions.location_id khi không có location cụ thể (RESERVE txn).
     */
    private Long getFirstStagingOrAnyLocationId(Long warehouseId) {
        // Tìm staging location trước
        return locationRepository.findFirstStagingByWarehouse(warehouseId)
                .map(loc -> loc.getLocationId())
                .orElseGet(() ->
                        locationRepository.findFirstByWarehouseId(warehouseId)
                                .map(loc -> loc.getLocationId())
                                .orElseThrow(() -> new BusinessException(
                                        "No location found for warehouse " + warehouseId + ". Cannot create RESERVE transaction."))
                );
    }

    // ─── Response builders ───────────────────────────────────────

    private OutboundResponse buildSalesOrderResponse(
            SalesOrderEntity so, List<SalesOrderItemEntity> items,
            CustomerEntity customer, List<OutboundResponse.StockWarning> warnings) {

        List<OutboundResponse.OutboundItemResponse> itemResponses = items.stream()
                .map(i -> {
                    BigDecimal available = getAvailableQty(so.getWarehouseId(), i.getSkuId());
                    String skuCode = skuRepository.findById(i.getSkuId()).map(s -> s.getSkuCode()).orElse(null);
                    String skuName = skuRepository.findById(i.getSkuId()).map(s -> s.getSkuName()).orElse(null);
                    return OutboundResponse.OutboundItemResponse.builder()
                            .itemId(i.getSoItemId()).skuId(i.getSkuId())
                            .skuCode(skuCode).skuName(skuName)
                            .requestedQty(i.getOrderedQty()).availableQty(available)
                            .insufficientStock(available.compareTo(i.getOrderedQty()) < 0)
                            .note(i.getNote()).build();
                }).toList();

        return OutboundResponse.builder()
                .documentId(so.getSoId()).documentCode(so.getSoCode())
                .orderType(OutboundType.SALES_ORDER).status(so.getStatus())
                .warehouseId(so.getWarehouseId())
                .customerId(so.getCustomerId())
                .customerName(customer != null ? customer.getCustomerName() : null)
                .deliveryDate(so.getRequiredShipDate())
                .items(itemResponses).note(so.getNote())
                .createdBy(so.getCreatedBy()).approvedBy(so.getApprovedBy())
                .approvedAt(so.getApprovedAt())
                .createdAt(so.getCreatedAt()).updatedAt(so.getUpdatedAt())
                .stockWarnings(warnings != null && !warnings.isEmpty() ? warnings : null)
                .build();
    }

    private OutboundResponse buildTransferResponse(
            TransferEntity transfer, List<TransferItemEntity> items,
            WarehouseEntity dest, List<OutboundResponse.StockWarning> warnings) {

        List<OutboundResponse.OutboundItemResponse> itemResponses = items.stream()
                .map(i -> {
                    BigDecimal available = getAvailableQty(transfer.getFromWarehouseId(), i.getSkuId());
                    String skuCode = skuRepository.findById(i.getSkuId()).map(s -> s.getSkuCode()).orElse(null);
                    String skuName = skuRepository.findById(i.getSkuId()).map(s -> s.getSkuName()).orElse(null);
                    return OutboundResponse.OutboundItemResponse.builder()
                            .itemId(i.getTransferItemId()).skuId(i.getSkuId())
                            .skuCode(skuCode).skuName(skuName)
                            .requestedQty(i.getQuantity()).availableQty(available)
                            .insufficientStock(available.compareTo(i.getQuantity()) < 0)
                            .build();
                }).toList();

        return OutboundResponse.builder()
                .documentId(transfer.getTransferId()).documentCode(transfer.getTransferCode())
                .orderType(OutboundType.INTERNAL_TRANSFER).status(transfer.getStatus())
                .warehouseId(transfer.getFromWarehouseId())
                .destinationWarehouseId(transfer.getToWarehouseId())
                .destinationWarehouseName(dest != null ? dest.getWarehouseName() : null)
                .items(itemResponses).note(transfer.getNote())
                .createdBy(transfer.getCreatedBy()).approvedBy(transfer.getApprovedBy())
                .approvedAt(transfer.getApprovedAt())
                .createdAt(transfer.getCreatedAt())
                .stockWarnings(warnings != null && !warnings.isEmpty() ? warnings : null)
                .build();
    }
}