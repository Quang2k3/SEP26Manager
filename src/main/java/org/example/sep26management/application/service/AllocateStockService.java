package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.application.dto.request.AllocateStockRequest;
import org.example.sep26management.application.dto.request.CreateIncidentRequest;
import org.example.sep26management.application.dto.response.AllocateStockResponse;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.IncidentResponse;
import org.example.sep26management.application.enums.IncidentCategory;
import org.example.sep26management.application.enums.IncidentType;
import org.example.sep26management.infrastructure.persistence.entity.SalesOrderEntity;
import org.example.sep26management.application.enums.OutboundType;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.example.sep26management.infrastructure.exception.ResourceNotFoundException;
import org.example.sep26management.infrastructure.persistence.entity.*;
import org.example.sep26management.infrastructure.persistence.repository.*;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * SCRUM-510: UC-WXE-05 Allocate / Reserve Stock
 * BR-WXE-18: FEFO — First Expiry First Out
 * BR-WXE-19: only available (unallocated) stock
 * BR-WXE-20: allocated stock locked from other tasks
 * BR-WXE-21: retain lot + expiry traceability
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AllocateStockService {

    private final SalesOrderJpaRepository soRepository;
    private final SalesOrderItemJpaRepository soItemRepository;
    private final TransferJpaRepository transferRepository;
    private final TransferItemJpaRepository transferItemRepository;
    private final InventoryAllocationRepository allocationRepository;
    private final InventorySnapshotJpaRepository snapshotRepository;
    private final ReservationJpaRepository reservationRepository;
    private final SkuJpaRepository skuRepository;
    private final AuditLogService auditLogService;
    private final IncidentService incidentService;
    private final WarehouseJpaRepository warehouseRepository;

    @Transactional
    public ApiResponse<AllocateStockResponse> allocateStock(
            AllocateStockRequest request,
            Long userId, String ip, String ua) {

        log.info("Allocating stock for documentId={}, type={}", request.getDocumentId(), request.getOrderType());

        Long warehouseId;
        String documentCode;
        List<SkuQtyPair> required = new ArrayList<>();

        // Resolve document and items
        if (request.getOrderType() == OutboundType.SALES_ORDER) {
            SalesOrderEntity so = soRepository.findById(request.getDocumentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            String.format(MessageConstants.OUTBOUND_NOT_FOUND, request.getDocumentId())));

            if (!"APPROVED".equals(so.getStatus())) {
                throw new BusinessException(MessageConstants.ALLOCATE_MUST_BE_APPROVED);
            }

            warehouseId = so.getWarehouseId();
            documentCode = so.getSoCode();

            soItemRepository.findBySoId(so.getSoId())
                    .forEach(i -> required.add(new SkuQtyPair(i.getSkuId(), i.getOrderedQty())));

        } else {
            TransferEntity transfer = transferRepository.findById(request.getDocumentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            String.format(MessageConstants.OUTBOUND_NOT_FOUND, request.getDocumentId())));

            if (!"APPROVED".equals(transfer.getStatus())) {
                throw new BusinessException(MessageConstants.ALLOCATE_MUST_BE_APPROVED);
            }

            warehouseId = transfer.getFromWarehouseId();
            documentCode = transfer.getTransferCode();

            transferItemRepository.findByTransferId(transfer.getTransferId())
                    .forEach(i -> required.add(new SkuQtyPair(i.getSkuId(), i.getQuantity())));
        }

        if (required.isEmpty()) {
            throw new BusinessException(MessageConstants.ALLOCATE_NO_ITEMS);
        }

        List<AllocateStockResponse.AllocationLine> allocations = new ArrayList<>();
        List<AllocateStockResponse.ShortageItem> shortages = new ArrayList<>();

        // Process each SKU
        for (SkuQtyPair pair : required) {
            BigDecimal remaining = pair.qty;
            String skuCode = skuRepository.findById(pair.skuId).map(s -> s.getSkuCode()).orElse("SKU#" + pair.skuId);
            String skuName = skuRepository.findById(pair.skuId).map(s -> s.getSkuName()).orElse(null);

            // BR-WXE-18: FEFO — get stock sorted by expiry date ASC
            List<InventoryAllocationRepository.FEFOAllocationProjection> stocks =
                    allocationRepository.findAvailableStockFEFO(warehouseId, pair.skuId);

            if (stocks.isEmpty()) {
                // Try without lot filter
                stocks = allocationRepository.findAvailableStockFEFONoLot(warehouseId, pair.skuId);
            }

            for (InventoryAllocationRepository.FEFOAllocationProjection stock : stocks) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

                // BR-WXE-19: only unallocated stock
                BigDecimal canAllocate = stock.getAvailableQty().min(remaining);
                if (canAllocate.compareTo(BigDecimal.ZERO) <= 0) continue;

                // BR-WXE-20: lock stock — update reserved_qty in snapshot
                snapshotRepository.incrementReservedByLocationAndSku(
                        stock.getLocationId(), pair.skuId, stock.getLotId(), canAllocate);

                // Create reservation record — BR-WXE-21: with lot info
                reservationRepository.save(ReservationEntity.builder()
                        .warehouseId(warehouseId).skuId(pair.skuId).lotId(stock.getLotId())
                        .quantity(canAllocate)
                        .referenceTable(request.getOrderType() == OutboundType.SALES_ORDER
                                ? "sales_orders" : "transfers")
                        .referenceId(request.getDocumentId())
                        .status("OPEN")
                        .build());

                // BR-WXE-21: record allocation line with lot + expiry
                allocations.add(AllocateStockResponse.AllocationLine.builder()
                        .skuId(pair.skuId).skuCode(skuCode).skuName(skuName)
                        .lotId(stock.getLotId()).lotNumber(null) // resolved from lot entity
                        .expiryDate(stock.getExpiryDate())
                        .locationId(stock.getLocationId()).locationCode(stock.getLocationCode())
                        .zoneCode(stock.getZoneCode())
                        .allocatedQty(canAllocate).requestedQty(pair.qty)
                        .build());

                remaining = remaining.subtract(canAllocate);
            }

            // Record shortage if not fully allocated
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal totalAvail = pair.qty.subtract(remaining);
                shortages.add(AllocateStockResponse.ShortageItem.builder()
                        .skuId(pair.skuId).skuCode(skuCode)
                        .requestedQty(pair.qty).availableQty(totalAvail).shortageQty(remaining)
                        .build());
            }
        }

        // Determine overall status
        boolean fullyAllocated = shortages.isEmpty();
        String allocStatus = fullyAllocated ? "ALLOCATED" : "PARTIALLY_ALLOCATED";

        // ── Update document status → ALLOCATED (only if fully allocated) ──────────
        if (fullyAllocated) {
            if (request.getOrderType() == OutboundType.SALES_ORDER) {
                soRepository.findById(request.getDocumentId()).ifPresent(so -> {
                    so.setStatus("ALLOCATED");
                    soRepository.save(so);
                    log.info("SO {} status → ALLOCATED", so.getSoCode());
                });
            } else {
                transferRepository.findById(request.getDocumentId()).ifPresent(t -> {
                    t.setStatus("ALLOCATED");
                    transferRepository.save(t);
                });
            }
        }

        auditLogService.logAction(userId,
                fullyAllocated ? "STOCK_ALLOCATED" : "STOCK_PARTIALLY_ALLOCATED",
                request.getOrderType() == OutboundType.SALES_ORDER ? "SALES_ORDER" : "TRANSFER",
                request.getDocumentId(),
                documentCode + " stock allocation: " + allocStatus, ip, ua);

        String message = fullyAllocated
                ? MessageConstants.ALLOCATE_SUCCESS
                : MessageConstants.ALLOCATE_PARTIAL;

        return ApiResponse.success(message, AllocateStockResponse.builder()
                .documentId(request.getDocumentId())
                .documentCode(documentCode)
                .status(allocStatus)
                .totalSkus(required.size())
                .allocatedSkus(required.size() - shortages.size())
                .allocations(allocations)
                .shortages(shortages.isEmpty() ? null : shortages)
                .build());
    }

    private record SkuQtyPair(Long skuId, BigDecimal qty) {}

    /**
     * Keeper báo thiếu hàng sau khi allocate thất bại.
     * Tạo incident type=SHORTAGE, category=QUALITY, gửi lên Manager xử lý.
     */
    @Transactional
    public ApiResponse<IncidentResponse> reportShortage(
            Long documentId,
            OutboundType orderType,
            Long userId, String ip, String ua) {

        Long warehouseId;
        String documentCode;
        List<SkuQtyPair> required = new ArrayList<>();

        if (orderType == OutboundType.SALES_ORDER) {
            SalesOrderEntity so = soRepository.findById(documentId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            String.format(MessageConstants.OUTBOUND_NOT_FOUND, documentId)));
            warehouseId = so.getWarehouseId();
            documentCode = so.getSoCode();
            soItemRepository.findBySoId(so.getSoId())
                    .forEach(i -> required.add(new SkuQtyPair(i.getSkuId(), i.getOrderedQty())));
        } else {
            TransferEntity transfer = transferRepository.findById(documentId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            String.format(MessageConstants.OUTBOUND_NOT_FOUND, documentId)));
            warehouseId = transfer.getFromWarehouseId();
            documentCode = transfer.getTransferCode();
            transferItemRepository.findByTransferId(transfer.getTransferId())
                    .forEach(i -> required.add(new SkuQtyPair(i.getSkuId(), i.getQuantity())));
        }

        // Build incident items — chỉ ghi các SKU thực sự thiếu
        List<CreateIncidentRequest.IncidentItemDto> incidentItems = new ArrayList<>();
        StringBuilder desc = new StringBuilder("Thiếu tồn kho khi phân bổ lệnh xuất " + documentCode + ": ");
        for (SkuQtyPair pair : required) {
            BigDecimal available = getAvailableQty(warehouseId, pair.skuId);
            if (available.compareTo(pair.qty) < 0) {
                BigDecimal shortage = pair.qty.subtract(available);
                String skuCode = skuRepository.findById(pair.skuId)
                        .map(s -> s.getSkuCode()).orElse("SKU#" + pair.skuId);
                incidentItems.add(new CreateIncidentRequest.IncidentItemDto(
                        pair.skuId, shortage, pair.qty, available, "SHORTAGE",
                        skuCode + ": cần " + pair.qty + ", còn " + available));
                desc.append(skuCode).append(" thiếu ").append(shortage).append("; ");
            }
        }

        if (incidentItems.isEmpty()) {
            throw new BusinessException("Không có SKU nào thiếu hàng để báo cáo.");
        }

        CreateIncidentRequest req = CreateIncidentRequest.builder()
                .warehouseId(warehouseId)
                .category(IncidentCategory.QUALITY)
                .incidentType(IncidentType.SHORTAGE)
                .description(desc.toString().trim())
                .items(incidentItems)
                .build();

        log.info("Reporting shortage incident for document {} ({})", documentCode, orderType);
        auditLogService.logAction(userId, "SHORTAGE_REPORTED",
                orderType == OutboundType.SALES_ORDER ? "SALES_ORDER" : "TRANSFER",
                documentId, "Shortage reported for " + documentCode, ip, ua);

        return incidentService.createIncident(req, userId);
    }

    private BigDecimal getAvailableQty(Long warehouseId, Long skuId) {
        BigDecimal total = snapshotRepository.sumQuantityByWarehouseAndSku(warehouseId, skuId);
        BigDecimal reserved = snapshotRepository.sumReservedByWarehouseAndSku(warehouseId, skuId);
        if (total == null) total = BigDecimal.ZERO;
        if (reserved == null) reserved = BigDecimal.ZERO;
        return total.subtract(reserved).max(BigDecimal.ZERO);
    }
}