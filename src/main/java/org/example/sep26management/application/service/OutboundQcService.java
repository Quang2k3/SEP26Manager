package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.request.QcScanRequest;
import org.example.sep26management.application.dto.request.ResolveOutboundDamageRequest;
import org.example.sep26management.application.dto.request.ResolveOutboundShortageRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.DispatchNoteResponse;
import org.example.sep26management.application.dto.response.IncidentResponse;
import org.example.sep26management.application.dto.response.QcSummaryResponse;
import org.example.sep26management.application.enums.IncidentCategory;
import org.example.sep26management.application.enums.IncidentType;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.example.sep26management.infrastructure.exception.ResourceNotFoundException;
import org.example.sep26management.infrastructure.persistence.entity.*;
import org.example.sep26management.infrastructure.persistence.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * OutboundQcService — QC Scan + Dispatch for Sales Order outbound flow.
 *
 * [V20 changes]:
 *  - finalizeQc: khi có FAIL → tạo Incident(DAMAGE) + set SO → ON_HOLD
 *  - resolveOutboundDamage: Manager xử lý DAMAGE (RETURN_SCRAP / ACCEPT)
 *  - resolveOutboundShortage: Manager xử lý SHORTAGE (WAIT_BACKORDER / CLOSE_SHORT)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboundQcService {

    private final PickingTaskJpaRepository pickingTaskRepository;
    private final PickingTaskItemJpaRepository pickingTaskItemRepository;
    private final SalesOrderJpaRepository salesOrderRepository;
    private final SalesOrderItemJpaRepository salesOrderItemRepository;
    private final InventorySnapshotJpaRepository inventorySnapshotRepository;
    private final InventoryTransactionJpaRepository inventoryTransactionRepository;
    private final ReservationQueryRepository reservationRepository;
    private final IncidentJpaRepository incidentRepository;
    private final IncidentItemJpaRepository incidentItemRepository;
    private final InventoryLotJpaRepository inventoryLotRepository;
    private final LocationJpaRepository locationRepository;
    private final SkuJpaRepository skuRepository;
    private final UserJpaRepository userRepository;
    private final WarehouseJpaRepository warehouseRepository;
    private final CustomerJpaRepository customerRepository;
    private final DispatchPdfService dispatchPdfService;

    // ─────────────────────────────────────────────────────────────────────────
    // 1) START QC SESSION
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public ApiResponse<Void> startQcSession(Long taskId, Long userId) {
        PickingTaskEntity task = findPickingTask(taskId);
        if (!"PICKED".equals(task.getStatus())) {
            throw new BusinessException(
                    "QC session can only be started for tasks in PICKED status. Current: " + task.getStatus());
        }
        task.setStatus("QC_IN_PROGRESS");
        pickingTaskRepository.save(task);

        if (task.getSoId() != null) {
            salesOrderRepository.findById(task.getSoId()).ifPresent(so -> {
                if ("PICKING".equals(so.getStatus())) {
                    so.setStatus("QC_SCAN");
                    so.setUpdatedAt(LocalDateTime.now());
                    salesOrderRepository.save(so);
                }
            });
        }
        return ApiResponse.success("QC session started. Task status: QC_IN_PROGRESS", null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2) QC SCAN ITEM
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public ApiResponse<Void> scanItem(QcScanRequest request, Long userId) {
        PickingTaskEntity task = findPickingTask(request.getPickingTaskId());
        if (!"QC_IN_PROGRESS".equals(task.getStatus())) {
            throw new BusinessException(
                    "QC scan only allowed when task is QC_IN_PROGRESS. Current: " + task.getStatus());
        }

        PickingTaskItemEntity item = pickingTaskItemRepository.findById(request.getPickingTaskItemId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PickingTaskItem not found: " + request.getPickingTaskItemId()));

        if (!item.getPickingTaskId().equals(request.getPickingTaskId())) {
            throw new BusinessException("Item does not belong to task " + request.getPickingTaskId());
        }

        if ("FAIL".equals(request.getResult())
                && (request.getReason() == null || request.getReason().isBlank())) {
            throw new BusinessException("reason is required when result is FAIL (BR-QC-01)");
        }

        item.setQcResult(request.getResult());
        item.setQcScannedAt(LocalDateTime.now());
        if ("FAIL".equals(request.getResult())) {
            item.setQcNote(request.getReason());
            // [V20] Lưu ảnh hàng hỏng nếu có
            if (request.getAttachmentUrl() != null && !request.getAttachmentUrl().isBlank()) {
                item.setQcAttachmentUrl(request.getAttachmentUrl());
            }
        } else {
            item.setQcNote(null);
        }
        pickingTaskItemRepository.save(item);
        return ApiResponse.success("QC scan recorded: " + request.getResult(), null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3) QC SUMMARY
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public ApiResponse<QcSummaryResponse> getQcSummary(Long taskId) {
        findPickingTask(taskId);
        List<PickingTaskItemEntity> items = pickingTaskItemRepository.findByPickingTaskId(taskId);
        int total   = items.size();
        int pass    = (int) items.stream().filter(i -> "PASS".equals(i.getQcResult())).count();
        int fail    = (int) items.stream().filter(i -> "FAIL".equals(i.getQcResult())).count();
        int hold    = (int) items.stream().filter(i -> "HOLD".equals(i.getQcResult())).count();
        int pending = (int) items.stream().filter(i -> i.getQcScannedAt() == null).count();
        return ApiResponse.success("QC summary retrieved", QcSummaryResponse.builder()
                .pickingTaskId(taskId).totalItems(total).passCount(pass)
                .failCount(fail).holdCount(hold).pendingCount(pending)
                .allScanned(pending == 0 && total > 0).build());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4) FINALIZE QC — [V20] tạo Incident DAMAGE khi có FAIL, set ON_HOLD
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public ApiResponse<QcSummaryResponse> finalizeQc(Long taskId, Long userId) {
        PickingTaskEntity task = findPickingTask(taskId);

        if ("PICKED".equals(task.getStatus())) {
            startQcSession(taskId, userId);
            task = findPickingTask(taskId);
        }

        if (!"QC_IN_PROGRESS".equals(task.getStatus())) {
            throw new BusinessException(
                    "Cannot finalize QC: task status is " + task.getStatus() + ". Expected QC_IN_PROGRESS.");
        }

        // Auto-PASS all unscanned items
        LocalDateTime now = LocalDateTime.now();
        for (PickingTaskItemEntity item : pickingTaskItemRepository.findUnscannedByTaskId(taskId)) {
            item.setQcResult("PASS");
            item.setQcScannedAt(now);
            item.setQcNote("Auto-PASS by finalize-qc");
            pickingTaskItemRepository.save(item);
        }

        List<PickingTaskItemEntity> allItems = pickingTaskItemRepository.findByPickingTaskId(taskId);
        int total   = allItems.size();
        int pass    = (int) allItems.stream().filter(i -> "PASS".equals(i.getQcResult())).count();
        int fail    = (int) allItems.stream().filter(i -> "FAIL".equals(i.getQcResult())).count();
        int hold    = (int) allItems.stream().filter(i -> "HOLD".equals(i.getQcResult())).count();
        int pending = (int) allItems.stream().filter(i -> i.getQcScannedAt() == null).count();

        Long soId = task.getSoId();

        if ((fail > 0 || hold > 0) && soId != null) {
            // [V20] GAP 4 FIX: tạo Incident DAMAGE + set SO → ON_HOLD
            createDamageIncident(task, allItems, soId, userId);
            salesOrderRepository.findById(soId).ifPresent(so -> {
                so.setStatus("ON_HOLD");
                so.setUpdatedAt(now);
                salesOrderRepository.save(so);
                log.info("SO {} → ON_HOLD (QC fail={}, hold={})", so.getSoCode(), fail, hold);
            });
        } else if (soId != null) {
            // All PASS
            salesOrderRepository.findById(soId).ifPresent(so -> {
                if ("PICKING".equals(so.getStatus()) || "QC_SCAN".equals(so.getStatus())) {
                    so.setStatus("QC_SCAN");
                    so.setUpdatedAt(now);
                    salesOrderRepository.save(so);
                }
            });
        }

        log.info("QC finalized taskId={}: pass={}, fail={}, hold={}", taskId, pass, fail, hold);
        return ApiResponse.success("QC finalized.", QcSummaryResponse.builder()
                .pickingTaskId(taskId).totalItems(total).passCount(pass)
                .failCount(fail).holdCount(hold).pendingCount(pending)
                .allScanned(pending == 0 && total > 0).build());
    }

    /** Tạo Incident DAMAGE cho các FAIL/HOLD items trong task. */
    private void createDamageIncident(PickingTaskEntity task,
                                      List<PickingTaskItemEntity> allItems,
                                      Long soId, Long reportedBy) {
        SalesOrderEntity so = salesOrderRepository.findById(soId).orElse(null);
        if (so == null) return;

        List<PickingTaskItemEntity> failItems = allItems.stream()
                .filter(i -> "FAIL".equals(i.getQcResult()) || "HOLD".equals(i.getQcResult()))
                .collect(Collectors.toList());
        if (failItems.isEmpty()) return;

        String code = "INC-QC-" + soId + "-" + (System.currentTimeMillis() % 100_000);
        StringBuilder desc = new StringBuilder("QC FAIL khi xuất " + so.getSoCode() + ": ");

        IncidentEntity incident = IncidentEntity.builder()
                .warehouseId(so.getWarehouseId())
                .incidentCode(code)
                .incidentType(IncidentType.DAMAGE)
                .category(IncidentCategory.QUALITY)
                .severity("HIGH")
                .occurredAt(LocalDateTime.now())
                .description("placeholder")
                .reportedBy(reportedBy)
                .status("OPEN")
                .soId(soId)
                .receivingId(null)
                .build();
        IncidentEntity saved = incidentRepository.save(incident);

        for (PickingTaskItemEntity item : failItems) {
            SkuEntity sku = skuRepository.findById(item.getSkuId()).orElse(null);
            String skuCode = sku != null ? sku.getSkuCode() : "SKU#" + item.getSkuId();
            desc.append(skuCode).append("[").append(item.getQcResult()).append("] ");

            String noteStr = item.getQcResult()
                    + (item.getQcNote() != null ? ": " + item.getQcNote() : "")
                    + (item.getQcAttachmentUrl() != null ? " | photo: " + item.getQcAttachmentUrl() : "");

            incidentItemRepository.save(IncidentItemEntity.builder()
                    .incident(saved)
                    .skuId(item.getSkuId())
                    .damagedQty(item.getRequiredQty())
                    .expectedQty(item.getRequiredQty())
                    .actualQty(BigDecimal.ZERO)
                    .reasonCode("DAMAGE")
                    .note(noteStr)
                    // [FIX QC] Lưu ảnh bằng chứng vào field riêng — không nhét vào note nữa
                    .attachmentUrl(item.getQcAttachmentUrl())
                    .build());
        }

        saved.setDescription(desc.toString().trim());
        incidentRepository.save(saved);
        log.info("Created DAMAGE Incident {} for SO {} ({} items)", code, so.getSoCode(), failItems.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5) RESOLVE DAMAGE (Manager) — [V20] GAP 5 FIX
    // POST /v1/outbound/incidents/{incidentId}/resolve-damage
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public ApiResponse<IncidentResponse> resolveOutboundDamage(Long incidentId,
                                                               ResolveOutboundDamageRequest request,
                                                               Long managerId) {
        IncidentEntity incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + incidentId));

        if (!"OPEN".equals(incident.getStatus()))
            throw new BusinessException("Incident is not OPEN. Current: " + incident.getStatus());
        if (!IncidentType.DAMAGE.equals(incident.getIncidentType()))
            throw new BusinessException("This endpoint handles DAMAGE incidents only.");
        if (incident.getSoId() == null)
            throw new BusinessException("Incident has no linked soId.");

        SalesOrderEntity so = salesOrderRepository.findById(incident.getSoId())
                .orElseThrow(() -> new ResourceNotFoundException("SalesOrder not found: " + incident.getSoId()));

        String action = request.getAction().toUpperCase();
        switch (action) {
            case "RETURN_SCRAP" -> {
                // [FIX] Trừ tồn kho hàng hỏng tại vị trí gốc
                // và chuyển sang khu hàng lỗi (defect zone) để theo dõi
                deductFailItems(incident.getSoId(), managerId, so.getWarehouseId());
                // Reset picking items FAIL/HOLD để không tính vào pick list cũ
                resetFailItemsForRepick(incident.getSoId());
                // [FIX] SO → APPROVED (không phải PICKING) để Keeper re-allocate từ đầu.
                // Hàng lỗi đã bị trừ khỏi kho → allocate lại sẽ lấy hàng thay thế từ bin khác.
                // Cancel reservations cũ trước khi đổi status
                cancelOpenReservationsForSo(incident.getSoId());
                so.setStatus("APPROVED");
                so.setUpdatedAt(LocalDateTime.now());
                salesOrderRepository.save(so);
                log.info("SO {} → APPROVED (re-allocate after DAMAGE RETURN_SCRAP)", so.getSoCode());
            }
            case "ACCEPT" -> {
                // Xuất luôn hàng lỗi
                so.setStatus("QC_SCAN");
                so.setUpdatedAt(LocalDateTime.now());
                salesOrderRepository.save(so);
                log.info("SO {} → QC_SCAN (DAMAGE ACCEPT)", so.getSoCode());
            }
            default -> throw new BusinessException(
                    "Invalid action: " + action + ". Must be RETURN_SCRAP or ACCEPT.");
        }

        incident.setStatus("RESOLVED");
        String noteAppend = "[Manager " + managerId + "]: " + action
                + (request.getNote() != null && !request.getNote().isBlank() ? " — " + request.getNote() : "");
        incident.setDescription(incident.getDescription() + "\n" + noteAppend);
        incidentRepository.save(incident);

        log.info("DAMAGE Incident {} resolved by manager {}, action={}", incidentId, managerId, action);
        return ApiResponse.success("Incident resolved. SO updated.", buildSimpleResponse(incident));
    }

    /** Trừ tồn kho hàng lỗi tại vị trí gốc, cộng vào khu hàng lỗi. */
    private void deductFailItems(Long soId, Long userId, Long warehouseId) {
        List<PickingTaskItemEntity> failItems = pickingTaskItemRepository.findAllActiveItemsBySoId(soId)
                .stream()
                .filter(i -> "FAIL".equals(i.getQcResult()) || "HOLD".equals(i.getQcResult()))
                .collect(Collectors.toList());

        if (failItems.isEmpty()) return;

        // Tìm hoặc tạo khu hàng lỗi (defect bin)
        LocationEntity defectBin = getOrCreateDefectBin(warehouseId);

        for (PickingTaskItemEntity item : failItems) {
            BigDecimal qty = item.getPickedQty().compareTo(BigDecimal.ZERO) > 0
                    ? item.getPickedQty() : item.getRequiredQty();
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;

            Long fromLocationId = item.getFromLocationId();

            // 1. Trừ quantity tại vị trí gốc
            inventorySnapshotRepository.decrementQuantity(
                    warehouseId, item.getSkuId(), item.getLotId(), fromLocationId, qty);

            // 2. Cộng quantity vào khu hàng lỗi
            inventorySnapshotRepository.upsertInventory(
                    warehouseId, item.getSkuId(), item.getLotId(), defectBin.getLocationId(), qty);

            // 3. Ghi txn DAMAGE_WRITE_OFF (xuất khỏi bin gốc)
            inventoryTransactionRepository.save(InventoryTransactionEntity.builder()
                    .warehouseId(warehouseId)
                    .locationId(fromLocationId)
                    .skuId(item.getSkuId())
                    .lotId(item.getLotId())
                    .quantity(qty.negate())
                    .txnType("DAMAGE_WRITE_OFF")
                    .referenceTable("sales_orders")
                    .referenceId(soId)
                    .reasonCode("QC_FAIL")
                    .createdBy(userId != null ? userId : 0L)
                    .build());

            // 4. Ghi txn DAMAGE_TRANSFER (nhập vào khu hàng lỗi)
            inventoryTransactionRepository.save(InventoryTransactionEntity.builder()
                    .warehouseId(warehouseId)
                    .locationId(defectBin.getLocationId())
                    .skuId(item.getSkuId())
                    .lotId(item.getLotId())
                    .quantity(qty)
                    .txnType("DAMAGE_TRANSFER")
                    .referenceTable("sales_orders")
                    .referenceId(soId)
                    .reasonCode("QC_FAIL_MOVE_TO_DEFECT")
                    .createdBy(userId != null ? userId : 0L)
                    .build());

            log.info("DAMAGE: skuId={} qty={} moved from loc={} to defect bin={}",
                    item.getSkuId(), qty, fromLocationId, defectBin.getLocationId());
        }
    }

    /**
     * Tìm khu hàng lỗi (defect bin) của warehouse.
     * Nếu chưa có → tự tạo location DEFECT-BIN với is_defect=true.
     */
    private LocationEntity getOrCreateDefectBin(Long warehouseId) {
        return locationRepository.findDefectBinByWarehouse(warehouseId)
                .orElseGet(() -> {
                    String code = "DEFECT-BIN-WH" + warehouseId;
                    LocationEntity defect = LocationEntity.builder()
                            .warehouseId(warehouseId)
                            .locationCode(code)
                            .locationType(org.example.sep26management.application.enums.LocationType.BIN)
                            .isPickingFace(false)
                            .isStaging(false)
                            .isDefect(true)
                            .active(true)
                            .build();
                    LocationEntity saved = locationRepository.save(defect);
                    log.info("Auto-created defect bin: {} (warehouseId={})", code, warehouseId);
                    return saved;
                });
    }

    /** Cancel OPEN reservations cho SO trước khi re-allocate sau RETURN_SCRAP. */
    private void cancelOpenReservationsForSo(Long soId) {
        reservationRepository.findByReferenceTableAndReferenceIdAndStatus("sales_orders", soId, "OPEN")
                .forEach(r -> {
                    if (r.getLocationId() != null) {
                        inventorySnapshotRepository.incrementReservedByLocationAndSku(
                                r.getLocationId(), r.getSkuId(), r.getLotId(), r.getQuantity().negate());
                    } else {
                        inventorySnapshotRepository.incrementReservedByWarehouseAndSku(
                                r.getWarehouseId(), r.getSkuId(), r.getQuantity().negate());
                    }
                    r.setStatus("CANCELLED");
                    reservationRepository.save(r);
                });
    }

    /** Reset qc_result → null cho FAIL/HOLD items để Keeper re-pick. */
    private void resetFailItemsForRepick(Long soId) {
        List<PickingTaskItemEntity> failItems = pickingTaskItemRepository.findAllActiveItemsBySoId(soId)
                .stream()
                .filter(i -> "FAIL".equals(i.getQcResult()) || "HOLD".equals(i.getQcResult()))
                .collect(Collectors.toList());
        for (PickingTaskItemEntity item : failItems) {
            item.setQcResult(null);
            item.setQcScannedAt(null);
            item.setQcNote("[Reset — re-pick required after DAMAGE RETURN_SCRAP]");
            pickingTaskItemRepository.save(item);
        }
        log.info("Reset {} FAIL/HOLD items for re-pick, soId={}", failItems.size(), soId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6) RESOLVE SHORTAGE (Manager) — [V20] GAP 2 + GAP 3 FIX
    // POST /v1/outbound/incidents/{incidentId}/resolve-shortage
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public ApiResponse<IncidentResponse> resolveOutboundShortage(Long incidentId,
                                                                 ResolveOutboundShortageRequest request,
                                                                 Long managerId) {
        IncidentEntity incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + incidentId));

        if (!"OPEN".equals(incident.getStatus()))
            throw new BusinessException("Incident is not OPEN. Current: " + incident.getStatus());
        if (!IncidentType.SHORTAGE.equals(incident.getIncidentType()))
            throw new BusinessException("This endpoint handles SHORTAGE incidents only.");
        if (incident.getSoId() == null)
            throw new BusinessException("Incident has no linked soId.");

        SalesOrderEntity so = salesOrderRepository.findById(incident.getSoId())
                .orElseThrow(() -> new ResourceNotFoundException("SalesOrder not found: " + incident.getSoId()));

        String action = request.getAction().toUpperCase();
        switch (action) {
            case "WAIT_BACKORDER" -> {
                // Chờ nhập hàng bù — SO giữ trạng thái WAITING_STOCK
                // Khi hàng về, Keeper allocate lại → AllocateStockService cho phép từ WAITING_STOCK
                so.setStatus("WAITING_STOCK");
                so.setUpdatedAt(LocalDateTime.now());
                salesOrderRepository.save(so);
                log.info("SO {} → WAITING_STOCK (chờ hàng bù)", so.getSoCode());
            }
            case "CLOSE_SHORT" -> {
                // [GAP 3 FIX] Cắt giảm orderedQty về available → SO → APPROVED → re-Allocate
                adjustOrderedQtyToAvailable(so);
                so.setStatus("APPROVED");
                so.setUpdatedAt(LocalDateTime.now());
                salesOrderRepository.save(so);
                log.info("SO {} → APPROVED (CLOSE_SHORT, re-Allocate ready)", so.getSoCode());
            }
            default -> throw new BusinessException(
                    "Invalid action: " + action + ". Must be WAIT_BACKORDER or CLOSE_SHORT.");
        }

        incident.setStatus("RESOLVED");
        String noteAppend = "[Manager " + managerId + "]: " + action
                + (request.getNote() != null && !request.getNote().isBlank() ? " — " + request.getNote() : "");
        incident.setDescription(incident.getDescription() + "\n" + noteAppend);
        incidentRepository.save(incident);

        log.info("SHORTAGE Incident {} resolved, action={}", incidentId, action);
        return ApiResponse.success("Shortage incident resolved.", buildSimpleResponse(incident));
    }

    /** CLOSE_SHORT: giảm orderedQty về số lượng available thực tế trong kho. */
    private void adjustOrderedQtyToAvailable(SalesOrderEntity so) {
        salesOrderItemRepository.findBySoId(so.getSoId()).forEach(item -> {
            BigDecimal total    = inventorySnapshotRepository.sumQuantityByWarehouseAndSku(so.getWarehouseId(), item.getSkuId());
            BigDecimal reserved = inventorySnapshotRepository.sumReservedByWarehouseAndSku(so.getWarehouseId(), item.getSkuId());
            if (total == null) total = BigDecimal.ZERO;
            if (reserved == null) reserved = BigDecimal.ZERO;
            BigDecimal available = total.subtract(reserved).max(BigDecimal.ZERO);
            if (available.compareTo(item.getOrderedQty()) < 0) {
                log.info("CLOSE_SHORT: soItem={} {} → {}", item.getSoItemId(), item.getOrderedQty(), available);
                item.setOrderedQty(available);
                salesOrderItemRepository.save(item);
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7) DISPATCH NOTE
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public ApiResponse<DispatchNoteResponse> generateDispatchNote(Long soId) {
        SalesOrderEntity so = findSalesOrder(soId);

        if (!pickingTaskItemRepository.allItemsScannedForSo(soId))
            throw new BusinessException("Cannot generate dispatch note: some items not QC-scanned (BR-QC-03)");

        long openIncidents = incidentRepository.countOpenIncidentsBySoId(soId);
        if (openIncidents > 0)
            throw new BusinessException("Cannot generate dispatch note: " + openIncidents + " open incident(s) (BR-QC-04)");

        WarehouseEntity warehouse = warehouseRepository.findById(so.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found"));
        String customerName = customerRepository.findById(so.getCustomerId())
                .map(CustomerEntity::getCustomerName).orElse("N/A");
        String createdByName = userRepository.findById(so.getCreatedBy())
                .map(UserEntity::getFullName).orElse("N/A");

        List<DispatchNoteResponse.DispatchNoteItem> noteItems = pickingTaskItemRepository.findPassedItemsBySoId(soId)
                .stream().map(this::buildDispatchNoteItem).collect(Collectors.toList());

        return ApiResponse.success("Dispatch note generated", DispatchNoteResponse.builder()
                .dispatchNoteCode("DN-" + so.getSoCode())
                .warehouseName(warehouse.getWarehouseName())
                .customerName(customerName)
                .dispatchDate(LocalDateTime.now())
                .items(noteItems)
                .totalItems(noteItems.size())
                .createdByName(createdByName)
                .build());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8) CONFIRM DISPATCH
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public ApiResponse<Void> confirmDispatch(Long soId, Long userId) {
        SalesOrderEntity so = findSalesOrder(soId);
        if (!"QC_SCAN".equals(so.getStatus()))
            throw new BusinessException("Dispatch requires QC_SCAN status. Current: " + so.getStatus());
        if (!pickingTaskItemRepository.allItemsScannedForSo(soId))
            throw new BusinessException("Dispatch blocked: items not QC-scanned (BR-DISPATCH-02)");
        long openIncidents = incidentRepository.countOpenIncidentsBySoId(soId);
        if (openIncidents > 0)
            throw new BusinessException("Dispatch blocked: " + openIncidents + " open incident(s) (BR-DISPATCH-03)");

        pickingTaskRepository.findByWarehouseIdAndSoId(so.getWarehouseId(), soId).stream()
                .filter(t -> !"CANCELLED".equals(t.getStatus()) && !"COMPLETED".equals(t.getStatus()))
                .forEach(t -> {
                    t.setStatus("COMPLETED");
                    t.setCompletedAt(LocalDateTime.now());
                    pickingTaskRepository.save(t);
                });

        so.setStatus("DISPATCHED");
        so.setUpdatedAt(LocalDateTime.now());
        salesOrderRepository.save(so);
        log.info("SO {} → DISPATCHED", so.getSoCode());
        return ApiResponse.success("Order dispatched. Status: DISPATCHED", null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────
    private PickingTaskEntity findPickingTask(Long taskId) {
        return pickingTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("PickingTask not found: " + taskId));
    }

    private SalesOrderEntity findSalesOrder(Long soId) {
        return salesOrderRepository.findById(soId)
                .orElseThrow(() -> new ResourceNotFoundException("SalesOrder not found: " + soId));
    }

    private IncidentResponse buildSimpleResponse(IncidentEntity e) {
        return IncidentResponse.builder()
                .incidentId(e.getIncidentId())
                .incidentCode(e.getIncidentCode())
                .incidentType(e.getIncidentType())
                .category(e.getCategory())
                .status(e.getStatus())
                .description(e.getDescription())
                .soId(e.getSoId())
                .build();
    }

    private DispatchNoteResponse.DispatchNoteItem buildDispatchNoteItem(PickingTaskItemEntity item) {
        SkuEntity sku = skuRepository.findById(item.getSkuId()).orElse(null);
        String lotNumber = null, manufactureDate = null, expiryDate = null;
        if (item.getLotId() != null) {
            InventoryLotEntity lot = inventoryLotRepository.findById(item.getLotId()).orElse(null);
            if (lot != null) {
                lotNumber       = lot.getLotNumber();
                manufactureDate = lot.getManufactureDate() != null ? lot.getManufactureDate().toString() : null;
                expiryDate      = lot.getExpiryDate() != null ? lot.getExpiryDate().toString() : null;
            }
        }
        return DispatchNoteResponse.DispatchNoteItem.builder()
                .skuCode(sku != null ? sku.getSkuCode() : "N/A")
                .skuName(sku != null ? sku.getSkuName() : "N/A")
                .unit(sku != null ? sku.getUnit() : "")
                .lotNumber(lotNumber).manufactureDate(manufactureDate).expiryDate(expiryDate)
                .locationCode(locationRepository.findById(item.getFromLocationId())
                        .map(LocationEntity::getLocationCode).orElse("N/A"))
                .quantity(item.getPickedQty().compareTo(BigDecimal.ZERO) > 0
                        ? item.getPickedQty() : item.getRequiredQty())
                .build();
    }
}