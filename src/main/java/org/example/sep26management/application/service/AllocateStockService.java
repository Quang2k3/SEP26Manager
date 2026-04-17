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
    private final NotificationService notificationService;
    private final PickingTaskItemJpaRepository pickingTaskItemRepository;

    @Transactional
    public ApiResponse<AllocateStockResponse> allocateStock(
            AllocateStockRequest request,
            Long userId, String ip, String ua) {

        log.info("Allocating stock for documentId={}, type={}", request.getDocumentId(), request.getOrderType());

        Long warehouseId;
        String documentCode;
        List<SkuQtyPair> required = new ArrayList<>();
        java.util.Map<Long, java.math.BigDecimal> groupedMap = new java.util.LinkedHashMap<>();

        if (request.getOrderType() == OutboundType.SALES_ORDER) {
            SalesOrderEntity so = soRepository.findById(request.getDocumentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            String.format(MessageConstants.OUTBOUND_NOT_FOUND, request.getDocumentId())));

            // [FIX] Cho phép tự động giữ hàng (allocate) ngay từ trạng thái nháp (DRAFT)
            java.util.List<String> allowedStatuses = java.util.List.of("DRAFT", "PENDING_APPROVAL", "APPROVED", "WAITING_STOCK", "SHORTAGE_PENDING");
            if (!allowedStatuses.contains(so.getStatus())) {
                throw new BusinessException("Lệnh xuất " + so.getSoCode() + " có trạng thái " + so.getStatus() + " không hợp lệ để phân bổ.");
            }

            warehouseId = so.getWarehouseId();
            documentCode = so.getSoCode();
            soItemRepository.findBySoId(so.getSoId())
                    .forEach(i -> groupedMap.merge(i.getSkuId(), i.getOrderedQty(), java.math.BigDecimal::add));

            // [PARTIAL REPICK FIX] Deduct already passed items from previous picking tasks
            pickingTaskItemRepository.findAllUncancelledItemsBySoId(so.getSoId()).forEach(oldItem -> {
                if (oldItem.getQcPassQty() != null && oldItem.getQcPassQty().compareTo(java.math.BigDecimal.ZERO) > 0) {
                    groupedMap.computeIfPresent(oldItem.getSkuId(), (k, v) -> v.subtract(oldItem.getQcPassQty()).max(java.math.BigDecimal.ZERO));
                }
            });

            // Populate required early for the WAITING_STOCK and APPROVED guards (only keep qty > 0)
            groupedMap.forEach((sku, qty) -> {
                if (qty.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    required.add(new SkuQtyPair(sku, qty));
                }
            });

            // [RACE CONDITION FIX] Lock SKUs in ascending order to prevent deadlocks and ensure 
            // that our availability calculations (total, reserved, ownReserved) remain completely 
            // atomic across concurrent allocations by different Keepers.
            required.stream().map(p -> p.skuId).sorted().distinct().forEach(skuId -> {
                skuRepository.findByIdForUpdate(skuId);
            });

            // [BUG-FIX] WAITING_STOCK guard: chỉ cho phép re-allocate khi tồn kho ĐÃ ĐỦ
            // toàn bộ yêu cầu. Nếu chưa đủ, block và yêu cầu chờ nhập thêm.
            // Không có guard này, Keeper có thể re-allocate bất kỳ lúc nào dù hàng
            // vẫn còn thiếu → tạo PARTIAL allocation mới → lại phải báo thiếu lại.
            if ("WAITING_STOCK".equals(so.getStatus())) {
                List<String> stillShort = new java.util.ArrayList<>();
                for (SkuQtyPair pair : required) {
                    java.math.BigDecimal total    = snapshotRepository.sumQuantityByWarehouseAndSku(so.getWarehouseId(), pair.skuId);
                    java.math.BigDecimal reserved = snapshotRepository.sumReservedByWarehouseAndSku(so.getWarehouseId(), pair.skuId);
                    java.math.BigDecimal ownReserved = reservationRepository.sumReservedByReferenceAndSku("sales_orders", so.getSoId(), pair.skuId);
                    if (total    == null) total    = java.math.BigDecimal.ZERO;
                    if (reserved == null) reserved = java.math.BigDecimal.ZERO;
                    if (ownReserved == null) ownReserved = java.math.BigDecimal.ZERO;
                    java.math.BigDecimal available = total.subtract(reserved).add(ownReserved).max(java.math.BigDecimal.ZERO);
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

            if (!"APPROVED".equals(transfer.getStatus()) && !"DRAFT".equals(transfer.getStatus())) {
                throw new BusinessException("Trạng thái lệnh chuyển kho không hợp lệ để phân bổ. Cần APPROVED hoặc DRAFT.");
            }

            warehouseId = transfer.getFromWarehouseId();
            documentCode = transfer.getTransferCode();
            transferItemRepository.findByTransferId(transfer.getTransferId())
                    .forEach(i -> groupedMap.merge(i.getSkuId(), i.getQuantity(), java.math.BigDecimal::add));

            groupedMap.forEach((sku, qty) -> required.add(new SkuQtyPair(sku, qty)));
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
                    if ("APPROVED".equals(so.getStatus()) || "WAITING_STOCK".equals(so.getStatus())) {
                        so.setStatus("ALLOCATED");
                        soRepository.save(so);
                        log.info("SO {} status → ALLOCATED", so.getSoCode());
                        // ── Realtime: notify KEEPER đơn đã phân bổ, cần tạo pick list ──
                        notificationService.notifyRoles(new String[]{"MANAGER", "QC", "KEEPER"}, "outbound_approved",
                                so.getSoId(), so.getSoCode(), "Đã phân bổ tồn kho — cần tạo Pick List");
                    }
                });
            } else {
                transferRepository.findById(request.getDocumentId()).ifPresent(t -> {
                    if ("APPROVED".equals(t.getStatus())) {
                        t.setStatus("ALLOCATED");
                        transferRepository.save(t);
                        // ── Realtime: notify KEEPER transfer đã phân bổ ────────────────
                        notificationService.notifyRoles(new String[]{"MANAGER", "QC", "KEEPER"}, "outbound_approved",
                                t.getTransferId(), t.getTransferCode(), "Transfer đã phân bổ — cần tạo Pick List");
                    }
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

    @Transactional
    public void cancelReservations(String referenceTable, Long documentId) {
        List<ReservationEntity> existingReservations = reservationRepository
                .findByReferenceTableAndReferenceIdAndStatus(referenceTable, documentId, "OPEN");
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
        log.info("Cancelled {} reservations for {} ID {}", existingReservations.size(), referenceTable, documentId);
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
        java.util.Map<Long, java.math.BigDecimal> groupedMap = new java.util.LinkedHashMap<>();

        if (orderType == OutboundType.SALES_ORDER) {
            SalesOrderEntity so = soRepository.findById(documentId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            String.format(MessageConstants.OUTBOUND_NOT_FOUND, documentId)));
            warehouseId = so.getWarehouseId();
            documentCode = so.getSoCode();
            soItemRepository.findBySoId(so.getSoId())
                    .forEach(i -> groupedMap.merge(i.getSkuId(), i.getOrderedQty(), java.math.BigDecimal::add));
        } else {
            TransferEntity transfer = transferRepository.findById(documentId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            String.format(MessageConstants.OUTBOUND_NOT_FOUND, documentId)));
            warehouseId = transfer.getFromWarehouseId();
            documentCode = transfer.getTransferCode();
            transferItemRepository.findByTransferId(transfer.getTransferId())
                    .forEach(i -> groupedMap.merge(i.getSkuId(), i.getQuantity(), java.math.BigDecimal::add));
        }

        groupedMap.forEach((sku, qty) -> required.add(new SkuQtyPair(sku, qty)));

        // Lấy danh sách reservation OPEN của đơn này (đã allocate được)
        // để tính đúng shortage = orderedQty - allocatedQty (không phải orderedQty - available)
        //
        // Bug cũ: dùng getAvailableQty() = total - reserved
        //   → available=0 vì reservation của đơn này đã chiếm hết reserved
        //   → shortage = orderedQty(11) - 0 = 11 ← SAI
        //
        // Fix: shortage = orderedQty - allocatedQty (SL đã được allocate cho đơn này)
        //   → allocated=10, orderedQty=11 → shortage=1 ← ĐÚNG
        String refTable = orderType == OutboundType.SALES_ORDER ? "sales_orders" : "transfers";
        java.util.Map<Long, BigDecimal> allocatedBySkuMap = new java.util.LinkedHashMap<>();
        reservationRepository.findByReferenceTableAndReferenceIdAndStatus(refTable, documentId, "OPEN")
                .forEach(r -> allocatedBySkuMap.merge(r.getSkuId(), r.getQuantity(), BigDecimal::add));

        List<CreateIncidentRequest.IncidentItemDto> incidentItems = new ArrayList<>();
        StringBuilder desc = new StringBuilder("Thiếu tồn kho khi phân bổ lệnh xuất " + documentCode + ": ");

        boolean autoApproveBackorder = false;

        for (SkuQtyPair pair : required) {
            // SL đã allocate được cho đơn này (reservation OPEN)
            BigDecimal allocated = allocatedBySkuMap.getOrDefault(pair.skuId, BigDecimal.ZERO);

            if (allocated.compareTo(pair.qty) < 0) {
                // shortage = SL cần - SL đã allocate được
                BigDecimal shortage = pair.qty.subtract(allocated);
                // available = tổng tồn - reserved (bao gồm cả reservation của đơn này)
                // → để hiển thị thực tế tồn kho, tính lại available = allocated + (total - reserved)
                BigDecimal totalQty   = snapshotRepository.sumQuantityByWarehouseAndSku(warehouseId, pair.skuId);
                BigDecimal totalRes   = snapshotRepository.sumReservedByWarehouseAndSku(warehouseId, pair.skuId);
                if (totalQty == null) totalQty = BigDecimal.ZERO;
                if (totalRes == null) totalRes = BigDecimal.ZERO;
                
                BigDecimal actualAvailable = totalQty.subtract(totalRes).max(BigDecimal.ZERO);
                if (actualAvailable.compareTo(BigDecimal.ZERO) == 0) {
                    autoApproveBackorder = true;
                }

                // tồn thực trong kho = allocated (đang giữ cho đơn này) + phần tự do
                BigDecimal totalInStock = totalQty; // tổng tồn vật lý
                String skuCode = skuRepository.findById(pair.skuId).map(s -> s.getSkuCode()).orElse("SKU#" + pair.skuId);
                incidentItems.add(new CreateIncidentRequest.IncidentItemDto(
                        pair.skuId, BigDecimal.ZERO, pair.qty, allocated, "SHORTAGE",
                        skuCode + ": cần " + pair.qty + ", kho có " + totalInStock + ", đã giữ " + allocated + ", thiếu " + shortage));
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

        // Đơn hàng tạm thời giữ nguyên trạng thái (APPROVED) để giao diện AllocatePanel 
        // tiếp tục mở, cho phép Manager nhìn thấy bảng chọn xử lý inline.
        // Trạng thái sẽ chỉ chuyển sang WAITING_STOCK khi Manager click "WAIT_BACKORDER".
        if (orderType == OutboundType.SALES_ORDER) {
            soRepository.findById(documentId).ifPresent(so -> {
                log.info("SO {} reported shortage. Pending Manager resolution.", so.getSoCode());
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

        // ── Realtime: notify MANAGER có sự cố thiếu hàng mới ────────────────
        notificationService.notifyRoles(new String[]{"MANAGER", "QC", "KEEPER"}, "incident_open",
                saved.getIncidentId(), saved.getIncidentCode(),
                documentCode + " — thiếu tồn kho");

        if (autoApproveBackorder && orderType == OutboundType.SALES_ORDER) {
            saved.setStatus("RESOLVED");
            saved.setDescription(saved.getDescription() + "\n[System Auto]: Tồn kho khả dụng về 0, hệ thống tự động duyệt chờ nhập bù.");
            incidentJpaRepository.save(saved);

            SalesOrderEntity so = soRepository.findById(documentId).orElse(null);
            if (so != null) {
                so.setStatus("WAITING_STOCK");
                so.setUpdatedAt(LocalDateTime.now());
                soRepository.save(so);

                log.info("SO {} auto-approved to WAIT_BACKORDER", so.getSoCode());
            }
        }

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