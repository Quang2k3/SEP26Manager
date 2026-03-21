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
import org.example.sep26management.application.enums.OutboundType;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.example.sep26management.infrastructure.exception.ResourceNotFoundException;
import org.example.sep26management.infrastructure.persistence.entity.*;
import org.example.sep26management.infrastructure.persistence.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    private final InventoryLotJpaRepository lotRepository;
    private final AuditLogService auditLogService;
    private final IncidentService incidentService;
    private final WarehouseJpaRepository warehouseRepository;
    // [V20] Inject trực tiếp để set soId khi tạo Incident
    private final IncidentJpaRepository incidentJpaRepository;
    private final IncidentItemJpaRepository incidentItemJpaRepository;

    @Transactional
    public ApiResponse<AllocateStockResponse> allocateStock(
            AllocateStockRequest request,
            Long userId, String ip, String ua) {

        log.info("Allocating stock for documentId={}, type={}", request.getDocumentId(), request.getOrderType());

        Long warehouseId;
        String documentCode;
        List<SkuQtyPair> required = new ArrayList<>();

        if (request.getOrderType() == OutboundType.SALES_ORDER) {
            SalesOrderEntity so = soRepository.findById(request.getDocumentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            String.format(MessageConstants.OUTBOUND_NOT_FOUND, request.getDocumentId())));

            // Allow re-allocate from APPROVED or WAITING_STOCK (after Manager resolves WAIT_BACKORDER)
            if (!"APPROVED".equals(so.getStatus()) && !"WAITING_STOCK".equals(so.getStatus())) {
                throw new BusinessException(MessageConstants.ALLOCATE_MUST_BE_APPROVED);
            }

            warehouseId = so.getWarehouseId();
            documentCode = so.getSoCode();
            soItemRepository.findBySoId(so.getSoId())
                    .forEach(i -> required.add(new SkuQtyPair(i.getSkuId(), i.getOrderedQty())));

            // [BUG-FIX] WAITING_STOCK guard: chỉ cho phép re-allocate khi tồn kho ĐÃ ĐỦ
            // toàn bộ yêu cầu. Nếu chưa đủ, block và yêu cầu chờ nhập thêm.
            // Không có guard này, Keeper có thể re-allocate bất kỳ lúc nào dù hàng
            // vẫn còn thiếu → tạo PARTIAL allocation mới → lại phải báo thiếu lại.
            if ("WAITING_STOCK".equals(so.getStatus())) {
                List<String> stillShort = new java.util.ArrayList<>();
                for (SkuQtyPair pair : required) {
                    java.math.BigDecimal total    = snapshotRepository.sumQuantityByWarehouseAndSku(so.getWarehouseId(), pair.skuId);
                    java.math.BigDecimal reserved = snapshotRepository.sumReservedByWarehouseAndSku(so.getWarehouseId(), pair.skuId);
                    if (total    == null) total    = java.math.BigDecimal.ZERO;
                    if (reserved == null) reserved = java.math.BigDecimal.ZERO;
                    java.math.BigDecimal available = total.subtract(reserved).max(java.math.BigDecimal.ZERO);
                    if (available.compareTo(pair.qty) < 0) {
                        String skuCode = skuRepository.findById(pair.skuId)
                                .map(s -> s.getSkuCode()).orElse("SKU#" + pair.skuId);
                        stillShort.add(skuCode + " (cần " + pair.qty + ", có " + available + ")");
                    }
                }
                if (!stillShort.isEmpty()) {
                    throw new BusinessException(
                            "Chưa đủ tồn kho để phân bổ — vẫn đang thiếu: " +
                                    String.join("; ", stillShort) +
                                    ". Vui lòng chờ nhập thêm hàng trước khi Allocate lại.");
                }
            }

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

        // Idempotency: cancel existing OPEN reservations
        String refTableClean = request.getOrderType() == OutboundType.SALES_ORDER ? "sales_orders" : "transfers";
        List<ReservationEntity> existingReservations = reservationRepository
                .findByReferenceTableAndReferenceIdAndStatus(refTableClean, request.getDocumentId(), "OPEN");
        for (ReservationEntity existing : existingReservations) {
            if (existing.getLocationId() != null) {
                snapshotRepository.incrementReservedByLocationAndSku(
                        existing.getLocationId(), existing.getSkuId(), existing.getLotId(),
                        existing.getQuantity().negate());
            } else {
                snapshotRepository.incrementReservedByWarehouseAndSku(
                        existing.getWarehouseId(), existing.getSkuId(), existing.getQuantity().negate());
            }
            existing.setStatus("CANCELLED");
            reservationRepository.save(existing);
        }

        List<AllocateStockResponse.AllocationLine> allocations = new ArrayList<>();
        List<AllocateStockResponse.ShortageItem> shortages = new ArrayList<>();

        for (SkuQtyPair pair : required) {
            BigDecimal remaining = pair.qty;
            String skuCode = skuRepository.findById(pair.skuId).map(s -> s.getSkuCode()).orElse("SKU#" + pair.skuId);
            String skuName = skuRepository.findById(pair.skuId).map(s -> s.getSkuName()).orElse(null);

            List<InventoryAllocationRepository.FEFOAllocationProjection> stocks =
                    allocationRepository.findAvailableStockFEFO(warehouseId, pair.skuId);
            if (stocks.isEmpty()) {
                stocks = allocationRepository.findAvailableStockFEFONoLot(warehouseId, pair.skuId);
            }

            for (InventoryAllocationRepository.FEFOAllocationProjection stock : stocks) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                BigDecimal canAllocate = stock.getAvailableQty().min(remaining);
                if (canAllocate.compareTo(BigDecimal.ZERO) <= 0) continue;

                snapshotRepository.incrementReservedByLocationAndSku(
                        stock.getLocationId(), pair.skuId, stock.getLotId(), canAllocate);

                reservationRepository.save(ReservationEntity.builder()
                        .warehouseId(warehouseId).skuId(pair.skuId).lotId(stock.getLotId())
                        .locationId(stock.getLocationId()).quantity(canAllocate)
                        .referenceTable(request.getOrderType() == OutboundType.SALES_ORDER ? "sales_orders" : "transfers")
                        .referenceId(request.getDocumentId()).status("OPEN").build());

                allocations.add(AllocateStockResponse.AllocationLine.builder()
                        .skuId(pair.skuId).skuCode(skuCode).skuName(skuName)
                        .lotId(stock.getLotId()).lotNumber(
                                stock.getLotId() != null
                                        ? lotRepository.findById(stock.getLotId()).map(l -> l.getLotNumber()).orElse(null)
                                        : null)
                        .expiryDate(stock.getExpiryDate())
                        .locationId(stock.getLocationId()).locationCode(stock.getLocationCode())
                        .zoneCode(stock.getZoneCode())
                        .allocatedQty(canAllocate).requestedQty(pair.qty).build());

                remaining = remaining.subtract(canAllocate);
            }

            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal totalAvail = pair.qty.subtract(remaining);
                shortages.add(AllocateStockResponse.ShortageItem.builder()
                        .skuId(pair.skuId).skuCode(skuCode)
                        .requestedQty(pair.qty).availableQty(totalAvail).shortageQty(remaining).build());
            }
        }

        boolean fullyAllocated = shortages.isEmpty();
        String allocStatus = fullyAllocated ? "ALLOCATED" : "PARTIALLY_ALLOCATED";

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

        String message = fullyAllocated ? MessageConstants.ALLOCATE_SUCCESS : MessageConstants.ALLOCATE_PARTIAL;

        return ApiResponse.success(message, AllocateStockResponse.builder()
                .documentId(request.getDocumentId()).documentCode(documentCode)
                .status(allocStatus).fullyAllocated(fullyAllocated)
                .totalSkus(required.size()).allocatedSkus(required.size() - shortages.size())
                .allocations(allocations).shortages(shortages.isEmpty() ? null : shortages).build());
    }

    /**
     * Keeper báo thiếu hàng — tạo Incident SHORTAGE với soId để Manager xử lý.
     * [V20 FIX] Lưu soId vào incident.soId để countOpenIncidentsBySoId hoạt động.
     */
    @Transactional
    public ApiResponse<IncidentResponse> reportShortage(
            Long documentId, OutboundType orderType, Long userId, String ip, String ua) {

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

        List<CreateIncidentRequest.IncidentItemDto> incidentItems = new ArrayList<>();
        StringBuilder desc = new StringBuilder("Thiếu tồn kho khi phân bổ lệnh xuất " + documentCode + ": ");

        for (SkuQtyPair pair : required) {
            BigDecimal available = getAvailableQty(warehouseId, pair.skuId);
            if (available.compareTo(pair.qty) < 0) {
                BigDecimal shortage = pair.qty.subtract(available);
                String skuCode = skuRepository.findById(pair.skuId).map(s -> s.getSkuCode()).orElse("SKU#" + pair.skuId);
                incidentItems.add(new CreateIncidentRequest.IncidentItemDto(
                        pair.skuId, shortage, pair.qty, available, "SHORTAGE",
                        skuCode + ": cần " + pair.qty + ", còn " + available));
                desc.append(skuCode).append(" thiếu ").append(shortage).append("; ");
            }
        }

        if (incidentItems.isEmpty()) {
            throw new BusinessException("Không có SKU nào thiếu hàng để báo cáo.");
        }

        // [BUG-FIX] Chặn báo cáo trùng lặp: nếu SO đã có OPEN shortage incident thì từ chối.
        // Không có guard này, Keeper có thể bấm "Báo thiếu" nhiều lần → nhiều incident OPEN
        // → Manager thấy nhiều đơn cùng SO → xử lý 1 đơn xong, đơn còn lại gây lỗi khi resolve.
        if (orderType == OutboundType.SALES_ORDER) {
            long openCount = incidentJpaRepository.countOpenIncidentsBySoId(documentId);
            if (openCount > 0) {
                throw new BusinessException(
                        "Đã có " + openCount + " incident SHORTAGE đang chờ xử lý cho đơn này. " +
                                "Vui lòng chờ Manager xử lý trước khi báo cáo lại.");
            }
        }

        // [BUG-FIX] Đổi SO status → ON_HOLD để khoá Keeper không thể báo cáo lại
        // và để FE hiển thị banner "Đang chờ Manager xử lý" thay vì cho phép thao tác tiếp.
        // [FIX TC-1A] DRAFT → WAITING_STOCK (báo thiếu từ đơn nháp, chưa qua duyệt)
        // APPROVED → ON_HOLD (Allocate thất bại sau khi Manager đã duyệt)
        if (orderType == OutboundType.SALES_ORDER) {
            soRepository.findById(documentId).ifPresent(so -> {
                String prevStatus = so.getStatus();
                String newStatus = "DRAFT".equals(prevStatus) ? "WAITING_STOCK" : "ON_HOLD";
                so.setStatus(newStatus);
                so.setUpdatedAt(java.time.LocalDateTime.now());
                soRepository.save(so);
                log.info("SO {} → {} (shortage reported from {}, waiting Manager)",
                        so.getSoCode(), newStatus, prevStatus);
            });
        }

        // [V20 FIX] Tạo trực tiếp với soId — không reuse receivingId làm surrogate
        String code = "INC-SHORT-" + documentId + "-" + System.currentTimeMillis() % 100_000;
        IncidentEntity incident = IncidentEntity.builder()
                .warehouseId(warehouseId)
                .incidentCode(code)
                .incidentType(IncidentType.SHORTAGE)
                .category(IncidentCategory.QUALITY)
                .severity("HIGH")
                .occurredAt(LocalDateTime.now())
                .description(desc.toString().trim())
                .reportedBy(userId)
                .status("OPEN")
                .soId(orderType == OutboundType.SALES_ORDER ? documentId : null)
                .receivingId(null)
                .build();

        IncidentEntity saved = incidentJpaRepository.save(incident);

        for (CreateIncidentRequest.IncidentItemDto itemDto : incidentItems) {
            incidentItemJpaRepository.save(IncidentItemEntity.builder()
                    .incident(saved)
                    .skuId(itemDto.getSkuId())
                    .damagedQty(itemDto.getDamagedQty())
                    .expectedQty(itemDto.getExpectedQty())
                    .actualQty(itemDto.getActualQty())
                    .reasonCode(itemDto.getReasonCode())
                    .note(itemDto.getNote())
                    .build());
        }

        log.info("Shortage incident {} created for document {} ({})", code, documentCode, orderType);
        auditLogService.logAction(userId, "SHORTAGE_REPORTED",
                orderType == OutboundType.SALES_ORDER ? "SALES_ORDER" : "TRANSFER",
                documentId, "Shortage reported for " + documentCode, ip, ua);

        return ApiResponse.success("Shortage incident reported successfully.", incidentService.toResponse(saved));
    }

    private BigDecimal getAvailableQty(Long warehouseId, Long skuId) {
        BigDecimal total = snapshotRepository.sumQuantityByWarehouseAndSku(warehouseId, skuId);
        BigDecimal reserved = snapshotRepository.sumReservedByWarehouseAndSku(warehouseId, skuId);
        if (total == null) total = BigDecimal.ZERO;
        if (reserved == null) reserved = BigDecimal.ZERO;
        return total.subtract(reserved).max(BigDecimal.ZERO);
    }

    private record SkuQtyPair(Long skuId, BigDecimal qty) {}
}