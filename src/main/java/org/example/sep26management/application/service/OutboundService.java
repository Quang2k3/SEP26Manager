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
    // SCRUM-505: Create
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
        CustomerEntity customer = customerRepository.findByCustomerCode(req.getCustomerCode())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Customer not found with code: " + req.getCustomerCode()));

        if (req.getDeliveryDate() != null && req.getDeliveryDate().isBefore(LocalDate.now())) {
            throw new BusinessException(MessageConstants.OUTBOUND_DATE_MUST_BE_FUTURE);
        }

        // checkStockAvailability chỉ tạo WARNING, không block
        List<OutboundResponse.StockWarning> warnings = checkStockAvailability(
                req.getWarehouseId(), req.getItems());

        String code = generateDocCode("EXP-SAL", req.getWarehouseId(), true);

        SalesOrderEntity so = SalesOrderEntity.builder()
                .warehouseId(req.getWarehouseId())
                .customerId(customer.getCustomerId())
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
        WarehouseEntity destWarehouse = warehouseRepository.findByWarehouseCode(req.getDestinationWarehouseCode())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Destination warehouse not found with code: " + req.getDestinationWarehouseCode()));

        if (destWarehouse.getWarehouseId().equals(req.getWarehouseId())) {
            throw new BusinessException(MessageConstants.OUTBOUND_SAME_WAREHOUSE);
        }

        List<OutboundResponse.StockWarning> warnings = checkStockAvailability(
                req.getWarehouseId(), req.getItems());

        String code = generateDocCode("EXP-INT", req.getWarehouseId(), false);

        TransferEntity transfer = TransferEntity.builder()
                .fromWarehouseId(req.getWarehouseId())
                .toWarehouseId(destWarehouse.getWarehouseId())
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
    // SCRUM-506: Update
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

        if (req.getCustomerCode() != null) {
            CustomerEntity newCustomer = customerRepository.findByCustomerCode(req.getCustomerCode())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Customer not found with code: " + req.getCustomerCode()));
            so.setCustomerId(newCustomer.getCustomerId());
        }
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

        if (req.getDestinationWarehouseCode() != null) {
            WarehouseEntity destWarehouse = warehouseRepository.findByWarehouseCode(req.getDestinationWarehouseCode())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Destination warehouse not found with code: " + req.getDestinationWarehouseCode()));
            transfer.setToWarehouseId(destWarehouse.getWarehouseId());
        }
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
    // SCRUM-507: Submit
    // Chỉ chuyển DRAFT → PENDING_APPROVAL. KHÔNG check tồn.
    // Check tồn xảy ra tại bước Allocate (AllocateStockService).
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

        if (req.getNote() != null) transfer.setNote(req.getNote());

        // Internal transfer tự động duyệt, không cần Manager
        transfer.setStatus("APPROVED");
        transfer.setApprovedAt(LocalDateTime.now());
        transfer.setApprovedBy(0L);
        transferRepository.save(transfer);

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
    // SCRUM-508: Approve (MANAGER)
    // Manager chỉ duyệt về mặt nghiệp vụ — KHÔNG block khi thiếu tồn.
    // Tồn kho sẽ được kiểm tra và khoá tại bước Allocate (AllocateStockService).
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<OutboundResponse> approveSalesOrder(
            Long soId,
            ApproveOutboundRequest request,
            Long managerId, String ip, String ua) {

        log.info("Approving sales order: soId={}, managerId={}", soId, managerId);

        SalesOrderEntity so = soRepository.findById(soId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.OUTBOUND_NOT_FOUND, soId)));

        if (!"PENDING_APPROVAL".equals(so.getStatus())) {
            throw new BusinessException(MessageConstants.OUTBOUND_NOT_PENDING_APPROVAL);
        }

        List<SalesOrderItemEntity> items = soItemRepository.findBySoId(soId);

        // Chỉ build stock snapshot để hiển thị thông tin cho Manager — KHÔNG block
        List<OutboundResponse.StockWarning> stockSnapshot = buildStockSnapshot(
                so.getWarehouseId(), items.stream()
                        .map(i -> new AbstractMap.SimpleEntry<>(i.getSkuId(), i.getOrderedQty()))
                        .toList());

        // Log cảnh báo nếu tồn thấp, nhưng KHÔNG throw exception
        for (SalesOrderItemEntity item : items) {
            BigDecimal available = getAvailableQty(so.getWarehouseId(), item.getSkuId());
            if (available.compareTo(item.getOrderedQty()) < 0) {
                String skuCode = skuRepository.findById(item.getSkuId())
                        .map(s -> s.getSkuCode()).orElse("SKU#" + item.getSkuId());
                log.warn("Low stock warning on approve: SO={}, SKU={}, available={}, requested={}",
                        so.getSoCode(), skuCode, available, item.getOrderedQty());
            }
        }

        // Không reserve inventory tại đây — sẽ được reserve tại bước Allocate
        so.setStatus("APPROVED");
        so.setApprovedBy(managerId);
        so.setApprovedAt(LocalDateTime.now());
        if (request != null && request.getNote() != null) so.setNote(request.getNote());
        soRepository.save(so);

        log.info("Sales order {} approved by managerId={}", so.getSoCode(), managerId);

        auditLogService.logAction(managerId, "OUTBOUND_APPROVED", "SALES_ORDER", soId,
                "Sales order " + so.getSoCode() + " approved", ip, ua);

        CustomerEntity customer = customerRepository.findById(so.getCustomerId()).orElse(null);
        return ApiResponse.success(MessageConstants.OUTBOUND_APPROVED_SUCCESS,
                buildSalesOrderResponse(so, items, customer, stockSnapshot));
    }

    // ─────────────────────────────────────────────────────────────
    // SCRUM-508 (Reject): Reject Sales Order (MANAGER)
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<OutboundResponse> rejectOutbound(
            Long soId,
            String rejectionReason,
            Long managerId, String ip, String ua) {

        log.info("Rejecting sales order: soId={}, managerId={}", soId, managerId);

        SalesOrderEntity so = soRepository.findById(soId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.OUTBOUND_NOT_FOUND, soId)));

        if (!"PENDING_APPROVAL".equals(so.getStatus())) {
            throw new BusinessException(MessageConstants.OUTBOUND_NOT_PENDING_APPROVAL);
        }
        if (rejectionReason == null || rejectionReason.trim().length() < 20) {
            throw new BusinessException(MessageConstants.OUTBOUND_REJECTION_REASON_REQUIRED);
        }

        so.setStatus("REJECTED");
        so.setNote((so.getNote() != null ? so.getNote() + " | " : "") + "Rejected: " + rejectionReason);
        so.setApprovedBy(managerId);
        so.setApprovedAt(LocalDateTime.now());
        soRepository.save(so);

        auditLogService.logAction(managerId, "OUTBOUND_REJECTED", "SALES_ORDER", soId,
                "Sales order " + so.getSoCode() + " rejected. Reason: " + rejectionReason, ip, ua);

        List<SalesOrderItemEntity> items = soItemRepository.findBySoId(soId);
        CustomerEntity customer = customerRepository.findById(so.getCustomerId()).orElse(null);
        return ApiResponse.success(MessageConstants.OUTBOUND_REJECTED_SUCCESS,
                buildSalesOrderResponse(so, items, customer, null));
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
            if (req.getCustomerCode() == null || req.getCustomerCode().isBlank())
                throw new BusinessException(MessageConstants.OUTBOUND_CUSTOMER_REQUIRED);
            if (req.getDeliveryDate() == null)
                throw new BusinessException(MessageConstants.OUTBOUND_DELIVERY_DATE_REQUIRED);
        } else {
            if (req.getDestinationWarehouseCode() == null || req.getDestinationWarehouseCode().isBlank())
                throw new BusinessException(MessageConstants.OUTBOUND_DESTINATION_REQUIRED);
        }
    }

    // Chỉ tạo WARNING, không block — dùng khi Create/Update để hiển thị cảnh báo cho user
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

    private BigDecimal getAvailableQty(Long warehouseId, Long skuId) {
        BigDecimal total = snapshotRepository.sumQuantityByWarehouseAndSku(warehouseId, skuId);
        BigDecimal reserved = snapshotRepository.sumReservedByWarehouseAndSku(warehouseId, skuId);
        if (total == null) total = BigDecimal.ZERO;
        if (reserved == null) reserved = BigDecimal.ZERO;
        return total.subtract(reserved).max(BigDecimal.ZERO);
    }

    private void reserveInventory(Long warehouseId,
                                  List<AbstractMap.SimpleEntry<Long, BigDecimal>> items,
                                  String refTable, Long refId, Long userId) {

        Long stagingLocationId = getFirstStagingOrAnyLocationId(warehouseId);

        for (var entry : items) {
            Long skuId = entry.getKey();
            BigDecimal qty = entry.getValue();

            ReservationEntity reservation = ReservationEntity.builder()
                    .warehouseId(warehouseId)
                    .skuId(skuId)
                    .quantity(qty)
                    .referenceTable(refTable)
                    .referenceId(refId)
                    .status("OPEN")
                    .build();
            reservationRepository.save(reservation);

            InventoryTransactionEntity txn = InventoryTransactionEntity.builder()
                    .warehouseId(warehouseId)
                    .skuId(skuId)
                    .locationId(stagingLocationId)
                    .quantity(qty.negate())
                    .txnType("RESERVE")
                    .referenceTable(refTable)
                    .referenceId(refId)
                    .createdBy(userId)
                    .build();
            txnRepository.save(txn);

            // [FIX-BUG-4] Dùng incrementReservedByLocationAndSku thay vì incrementReservedByWarehouseAndSku.
            // incrementReservedByWarehouseAndSku update TẤT CẢ rows của warehouse+sku (không filter location/lot)
            // → reserved_qty bị tăng nhầm ở nhiều location cùng lúc → available = total - reserved bị âm.
            // stagingLocationId là location thực tế hàng đang ở khi chờ xuất (cho Internal Transfer).
            snapshotRepository.incrementReservedByLocationAndSku(stagingLocationId, skuId, null, qty);
        }
    }

    private Long getFirstStagingOrAnyLocationId(Long warehouseId) {
        return locationRepository.findFirstStagingByWarehouse(warehouseId)
                .map(loc -> loc.getLocationId())
                .orElseGet(() ->
                        locationRepository.findFirstByWarehouseId(warehouseId)
                                .map(loc -> loc.getLocationId())
                                .orElseThrow(() -> new BusinessException(
                                        "No location found for warehouse " + warehouseId))
                );
    }

    // ─────────────────────────────────────────────────────────────
    // DELETE: Xóa lệnh xuất kho (chỉ DRAFT)
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<Void> deleteSalesOrder(Long soId, Long userId, String ip, String ua) {
        SalesOrderEntity so = soRepository.findById(soId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.OUTBOUND_NOT_FOUND, soId)));
        if (!"DRAFT".equals(so.getStatus())) {
            throw new BusinessException("Chỉ có thể xóa lệnh xuất đang ở trạng thái DRAFT. Trạng thái hiện tại: " + so.getStatus());
        }
        if (!so.getCreatedBy().equals(userId)) {
            throw new BusinessException(MessageConstants.OUTBOUND_NOT_CREATOR);
        }
        soItemRepository.deleteBySoId(soId);
        soRepository.delete(so);
        auditLogService.logAction(userId, "OUTBOUND_DELETED", "SALES_ORDER", soId,
                "Sales order " + so.getSoCode() + " deleted", ip, ua);
        return ApiResponse.success("Đã xóa lệnh xuất kho thành công", null);
    }

    @Transactional
    public ApiResponse<Void> deleteTransfer(Long transferId, Long userId, String ip, String ua) {
        TransferEntity transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.OUTBOUND_NOT_FOUND, transferId)));
        if (!"DRAFT".equals(transfer.getStatus())) {
            throw new BusinessException("Chỉ có thể xóa lệnh chuyển kho đang ở trạng thái DRAFT. Trạng thái hiện tại: " + transfer.getStatus());
        }
        if (!transfer.getCreatedBy().equals(userId)) {
            throw new BusinessException(MessageConstants.OUTBOUND_NOT_CREATOR);
        }
        transferItemRepository.deleteByTransferId(transferId);
        transferRepository.delete(transfer);
        auditLogService.logAction(userId, "OUTBOUND_DELETED", "TRANSFER", transferId,
                "Transfer " + transfer.getTransferCode() + " deleted", ip, ua);
        return ApiResponse.success("Đã xóa lệnh chuyển kho thành công", null);
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
                .dispatchPdfUrl(so.getDispatchPdfUrl())
                .signedNoteUrl(so.getSignedNoteUrl())
                .signedNoteUploadedAt(so.getSignedNoteUploadedAt() != null ? so.getSignedNoteUploadedAt().toString() : null)
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
    // ─── Get single order detail (with items) ─────────────────────────────────
    @Transactional(readOnly = true)
    public ApiResponse<OutboundResponse> getOutboundDetail(Long documentId, String orderType) {
        if ("INTERNAL_TRANSFER".equals(orderType)) {
            TransferEntity transfer = transferRepository.findById(documentId)
                    .orElseThrow(() -> new RuntimeException("Transfer not found: " + documentId));
            List<TransferItemEntity> items = transferItemRepository.findByTransferId(documentId);
            WarehouseEntity dest = transfer.getToWarehouseId() != null
                    ? warehouseRepository.findById(transfer.getToWarehouseId()).orElse(null) : null;
            return ApiResponse.success("OK", buildTransferResponse(transfer, items, dest, null));
        } else {
            SalesOrderEntity so = soRepository.findById(documentId)
                    .orElseThrow(() -> new RuntimeException("Sales order not found: " + documentId));
            List<SalesOrderItemEntity> items = soItemRepository.findBySoId(documentId);
            CustomerEntity customer = so.getCustomerId() != null
                    ? customerRepository.findById(so.getCustomerId()).orElse(null) : null;
            return ApiResponse.success("OK", buildSalesOrderResponse(so, items, customer, null));
        }
    }


}