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

        // Validate warehouse
        warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.WAREHOUSE_NOT_FOUND, request.getWarehouseId())));

        // BR-OUT-01: validate required fields per type
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
        // Validate customer
        CustomerEntity customer = customerRepository.findById(req.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.CUSTOMER_NOT_FOUND, req.getCustomerId())));

        // BR-OUT-02: delivery date >= today
        if (req.getDeliveryDate() != null && req.getDeliveryDate().isBefore(LocalDate.now())) {
            throw new BusinessException(MessageConstants.OUTBOUND_DATE_MUST_BE_FUTURE);
        }

        // Check stock availability for all items
        List<OutboundResponse.StockWarning> warnings = checkStockAvailability(
                req.getWarehouseId(), req.getItems());

        // Generate document code — BR-OUT-05: EXP-SAL-YYYYMMDD-NNNN
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

        // Save items
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
        // Validate destination warehouse
        WarehouseEntity destWarehouse = warehouseRepository.findById(req.getDestinationWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.WAREHOUSE_NOT_FOUND, req.getDestinationWarehouseId())));

        if (req.getDestinationWarehouseId().equals(req.getWarehouseId())) {
            throw new BusinessException(MessageConstants.OUTBOUND_SAME_WAREHOUSE);
        }

        // BR-OUT-02
        if (req.getTransferDate() != null && req.getTransferDate().isBefore(LocalDate.now())) {
            throw new BusinessException(MessageConstants.OUTBOUND_DATE_MUST_BE_FUTURE);
        }

        List<OutboundResponse.StockWarning> warnings = checkStockAvailability(
                req.getWarehouseId(), req.getItems());

        // Generate doc code — BR-OUT-05: EXP-INT-YYYYMMDD-NNNN
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
    // BR-OUT-06: only creator can edit DRAFT
    // BR-OUT-07: all changes logged
    // BR-OUT-08: stock rechecked on edit
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

        // Must be DRAFT
        if (!"DRAFT".equals(so.getStatus())) {
            throw new BusinessException(MessageConstants.OUTBOUND_NOT_EDITABLE);
        }

        // BR-OUT-06: only creator can edit
        if (!so.getCreatedBy().equals(userId)) {
            throw new BusinessException(MessageConstants.OUTBOUND_NOT_CREATOR);
        }

        // BR-OUT-02
        if (req.getDeliveryDate() != null && req.getDeliveryDate().isBefore(LocalDate.now())) {
            throw new BusinessException(MessageConstants.OUTBOUND_DATE_MUST_BE_FUTURE);
        }

        // Update header
        if (req.getCustomerId() != null) so.setCustomerId(req.getCustomerId());
        if (req.getDeliveryDate() != null) so.setRequiredShipDate(req.getDeliveryDate());
        if (req.getNote() != null) so.setNote(req.getNote());
        soRepository.save(so);

        // Replace items — BR-OUT-08: recheck stock
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
    // BR-OUT-12: internal transfer → auto-approve if configured
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

        // BR-OUT-09/10: hard block if any item insufficient
        validateSufficientStock(so.getWarehouseId(), items.stream()
                .map(i -> new AbstractMap.SimpleEntry<>(i.getSkuId(), i.getOrderedQty()))
                .toList());

        // BR-OUT-11: sales orders always require approval
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

        // BR-OUT-09/10: hard block
        validateSufficientStock(transfer.getFromWarehouseId(), items.stream()
                .map(i -> new AbstractMap.SimpleEntry<>(i.getSkuId(), i.getQuantity()))
                .toList());

        if (req.getNote() != null) transfer.setNote(req.getNote());

        // BR-OUT-12: internal transfers auto-approve
        transfer.setStatus("APPROVED");
        transfer.setApprovedAt(java.time.LocalDateTime.now());
        transfer.setApprovedBy(0L); // 0 = system auto-approved
        transferRepository.save(transfer);

        // Reserve inventory for auto-approved transfer
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
    // Helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * BR-OUT-05: Generate doc code EXP-SAL-YYYYMMDD-NNNN or EXP-INT-YYYYMMDD-NNNN
     */
    private String generateDocCode(String prefix, Long warehouseId, boolean isSales) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        long seq = isSales
                ? soRepository.countTodayByWarehouse(warehouseId, startOfDay, endOfDay) + 1
                : transferRepository.countTodayByWarehouse(warehouseId, startOfDay, endOfDay) + 1;
        return String.format("%s-%s-%04d", prefix, date, seq);
    }

    /**
     * BR-OUT-01: validate required fields based on order type
     */
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

    /**
     * BR-OUT-03/04: Check real-time stock availability, collect warnings
     */
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
     * BR-OUT-09/10: Hard block — throws exception if any item has insufficient stock
     */
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
     * Available = snapshot.quantity - snapshot.reserved_qty (aggregated across all lots/locations)
     */
    private BigDecimal getAvailableQty(Long warehouseId, Long skuId) {
        BigDecimal total = snapshotRepository.sumQuantityByWarehouseAndSku(warehouseId, skuId);
        BigDecimal reserved = snapshotRepository.sumReservedByWarehouseAndSku(warehouseId, skuId);
        if (total == null) total = BigDecimal.ZERO;
        if (reserved == null) reserved = BigDecimal.ZERO;
        return total.subtract(reserved).max(BigDecimal.ZERO);
    }

    /**
     * BR-OUT-17/18: Reserve inventory — update snapshot.reserved_qty + create reservation record
     */
    private void reserveInventory(Long warehouseId,
                                  List<AbstractMap.SimpleEntry<Long, BigDecimal>> items,
                                  String refTable, Long refId, Long userId) {

        for (var entry : items) {
            Long skuId = entry.getKey();
            BigDecimal qty = entry.getValue();

            // Create reservation record
            ReservationEntity reservation = ReservationEntity.builder()
                    .warehouseId(warehouseId).skuId(skuId)
                    .quantity(qty).referenceTable(refTable).referenceId(refId)
                    .status("OPEN")
                    .build();
            reservationRepository.save(reservation);

            // Create inventory transaction (type: RESERVE)
            InventoryTransactionEntity txn = InventoryTransactionEntity.builder()
                    .warehouseId(warehouseId).skuId(skuId)
                    .quantity(qty.negate())  // negative = reserved out
                    .txnType("RESERVE")
                    .referenceTable(refTable).referenceId(refId)
                    .createdBy(userId)
                    .build();
            txnRepository.save(txn);

            // Update snapshot reserved_qty
            snapshotRepository.incrementReservedByWarehouseAndSku(warehouseId, skuId, qty);
        }
    }

    // ─── Response builders ───

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