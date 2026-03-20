package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.request.QcScanRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.DispatchNoteResponse;
import org.example.sep26management.application.dto.response.QcSummaryResponse;
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
 * OutboundQcService — QC Scan + Dispatch logic for the Sales Order outbound flow.
 *
 * New lifecycle states introduced:
 *   picking_task.status: PICKED → QC_IN_PROGRESS → COMPLETED
 *   sales_order.status:  PICKING → QC_SCAN → DISPATCHED
 *
 * APIs covered:
 *   POST /v1/outbound/pick-list/{taskId}/start-qc      (BR-QC-01/02)
 *   POST /v1/outbound/qc-scan                          (BR-QC-01/02)
 *   GET  /v1/outbound/pick-list/{taskId}/qc-summary
 *   GET  /v1/outbound/sales-orders/{soId}/dispatch-note (BR-QC-03/04)
 *   POST /v1/outbound/sales-orders/{soId}/dispatch      (BR-DISPATCH-01/02/03)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboundQcService {

    private final PickingTaskJpaRepository pickingTaskRepository;
    private final PickingTaskItemJpaRepository pickingTaskItemRepository;
    private final SalesOrderJpaRepository salesOrderRepository;
    private final InventorySnapshotJpaRepository inventorySnapshotRepository;
    private final InventoryTransactionJpaRepository inventoryTransactionRepository;
    private final ReservationQueryRepository reservationRepository;
    private final IncidentJpaRepository incidentRepository;
    private final InventoryLotJpaRepository inventoryLotRepository;
    private final LocationJpaRepository locationRepository;
    private final SkuJpaRepository skuRepository;
    private final UserJpaRepository userRepository;
    private final WarehouseJpaRepository warehouseRepository;
    private final CustomerJpaRepository customerRepository;
    // ── [NEW] PDF service — tạo Phiếu Xuất Kho sau khi dispatch thành công
    private final DispatchPdfService dispatchPdfService;

    // ─────────────────────────────────────────────────────────────────────────
    // 1) START QC SESSION
    // POST /v1/outbound/pick-list/{taskId}/start-qc
    // Role: KEEPER
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Transitions a picking task from PICKED → QC_IN_PROGRESS.
     * Validates that all items have been picked before starting QC.
     */
    @Transactional
    public ApiResponse<Void> startQcSession(Long taskId, Long userId) {
        log.info("Starting QC session for taskId={}, userId={}", taskId, userId);

        PickingTaskEntity task = findPickingTask(taskId);

        if (!"PICKED".equals(task.getStatus())) {
            throw new BusinessException(
                    "QC session can only be started for tasks in PICKED status. Current status: " + task.getStatus());
        }

        task.setStatus("QC_IN_PROGRESS");
        pickingTaskRepository.save(task);

        // Also reflect on the Sales Order
        if (task.getSoId() != null) {
            salesOrderRepository.findById(task.getSoId()).ifPresent(so -> {
                if ("PICKING".equals(so.getStatus())) {
                    so.setStatus("QC_SCAN");
                    so.setUpdatedAt(LocalDateTime.now());
                    salesOrderRepository.save(so);
                }
            });
        }

        log.info("QC session started for taskId={}", taskId);
        return ApiResponse.success("QC session started. Task status: QC_IN_PROGRESS", null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2) QC SCAN ITEM
    // POST /v1/outbound/qc-scan
    // Role: KEEPER
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Records QC result (PASS / FAIL / HOLD) for a single picking task item.
     *
     * BR-QC-01: FAIL → qc_result = FAIL, qc_note populated, keeper creates incident separately.
     * BR-QC-02: PASS → qc_result = PASS.
     */
    @Transactional
    public ApiResponse<Void> scanItem(QcScanRequest request, Long userId) {
        log.info("QC scan: taskId={}, itemId={}, result={}",
                request.getPickingTaskId(), request.getPickingTaskItemId(),
                request.getResult());

        // Validate task exists and is in QC_IN_PROGRESS
        PickingTaskEntity task = findPickingTask(request.getPickingTaskId());
        if (!"QC_IN_PROGRESS".equals(task.getStatus())) {
            throw new BusinessException(
                    "QC scan is only allowed when task status is QC_IN_PROGRESS. Current: " + task.getStatus());
        }

        // Validate item belongs to this task
        PickingTaskItemEntity item = pickingTaskItemRepository.findById(request.getPickingTaskItemId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PickingTaskItem not found: " + request.getPickingTaskItemId()));

        if (!item.getPickingTaskId().equals(request.getPickingTaskId())) {
            throw new BusinessException("Item " + request.getPickingTaskItemId()
                    + " does not belong to task " + request.getPickingTaskId());
        }

        // BR-QC-01: FAIL requires a reason
        if ("FAIL".equals(request.getResult())
                && (request.getReason() == null || request.getReason().isBlank())) {
            throw new BusinessException("reason is required when result is FAIL (BR-QC-01)");
        }

        // Apply QC result
        item.setQcResult(request.getResult());
        item.setQcScannedAt(LocalDateTime.now());

        if ("FAIL".equals(request.getResult())) {
            // BR-QC-01: store note; keeper will create incident via /v1/incidents
            item.setQcNote(request.getReason());
        } else {
            item.setQcNote(null);
        }

        pickingTaskItemRepository.save(item);

        log.info("QC scan saved: itemId={}, result={}", item.getPickingTaskItemId(), item.getQcResult());
        return ApiResponse.success("QC scan recorded: " + request.getResult(), null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3) QC SUMMARY
    // GET /v1/outbound/pick-list/{taskId}/qc-summary
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns a live summary of QC results for all items in a picking task.
     * pendingCount = items where qc_scanned_at IS NULL.
     */
    @Transactional(readOnly = true)
    public ApiResponse<QcSummaryResponse> getQcSummary(Long taskId) {
        log.info("Fetching QC summary for taskId={}", taskId);

        findPickingTask(taskId); // existence check

        List<PickingTaskItemEntity> items = pickingTaskItemRepository.findByPickingTaskId(taskId);

        int total   = items.size();
        int pass    = (int) items.stream().filter(i -> "PASS".equals(i.getQcResult())).count();
        int fail    = (int) items.stream().filter(i -> "FAIL".equals(i.getQcResult())).count();
        int hold    = (int) items.stream().filter(i -> "HOLD".equals(i.getQcResult())).count();
        int pending = (int) items.stream().filter(i -> i.getQcScannedAt() == null).count();

        QcSummaryResponse summary = QcSummaryResponse.builder()
                .pickingTaskId(taskId)
                .totalItems(total)
                .passCount(pass)
                .failCount(fail)
                .holdCount(hold)
                .pendingCount(pending)
                .allScanned(pending == 0 && total > 0)
                .build();

        return ApiResponse.success("QC summary retrieved", summary);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4) DISPATCH NOTE  (generated on-the-fly, NOT stored)
    // GET /v1/outbound/sales-orders/{soId}/dispatch-note
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Dynamically generates a dispatch note from PASS items.
     *
     * BR-QC-03: returns 400 if any item has qc_scanned_at IS NULL.
     * BR-QC-04: returns 400 if there are OPEN incidents for this SO.
     */
    @Transactional(readOnly = true)
    public ApiResponse<DispatchNoteResponse> generateDispatchNote(Long soId) {
        log.info("Generating dispatch note for soId={}", soId);

        SalesOrderEntity so = findSalesOrder(soId);

        // BR-QC-03: All items must have been scanned
        boolean allScanned = pickingTaskItemRepository.allItemsScannedForSo(soId);
        if (!allScanned) {
            throw new BusinessException(
                    "Cannot generate dispatch note: some items have not been QC-scanned yet (BR-QC-03)");
        }

        // BR-QC-04: No OPEN incidents
        long openIncidents = incidentRepository.countOpenIncidentsBySoId(soId);
        if (openIncidents > 0) {
            throw new BusinessException(
                    "Cannot generate dispatch note: there are " + openIncidents
                            + " open incident(s) linked to this order (BR-QC-04)");
        }

        // Fetch supporting data
        WarehouseEntity warehouse = warehouseRepository.findById(so.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found: " + so.getWarehouseId()));

        String customerName = customerRepository.findById(so.getCustomerId())
                .map(CustomerEntity::getCustomerName)
                .orElse("N/A");

        String createdByName = userRepository.findById(so.getCreatedBy())
                .map(UserEntity::getFullName)
                .orElse("N/A");

        // Only PASS items go on the dispatch note
        List<PickingTaskItemEntity> passItems = pickingTaskItemRepository.findPassedItemsBySoId(soId);

        List<DispatchNoteResponse.DispatchNoteItem> noteItems = passItems.stream()
                .map(item -> buildDispatchNoteItem(item))
                .collect(Collectors.toList());

        DispatchNoteResponse note = DispatchNoteResponse.builder()
                .dispatchNoteCode("DN-" + so.getSoCode())
                .warehouseName(warehouse.getWarehouseName())
                .customerName(customerName)
                .dispatchDate(LocalDateTime.now())
                .items(noteItems)
                .totalItems(noteItems.size())
                .createdByName(createdByName)
                .build();

        return ApiResponse.success("Dispatch note generated", note);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5) CONFIRM DISPATCH
    // POST /v1/outbound/sales-orders/{soId}/dispatch
    // Role: KEEPER
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Finalises the outbound dispatch:
     *
     * For each PASS picking_task_item:
     *   1. Decrease inventory_snapshot.quantity        (BR-DISPATCH-01)
     *   2. Decrease inventory_snapshot.reserved_qty   (BR-DISPATCH-01)
     *   3. Insert inventory_transaction (txn_type=DISPATCH, negative qty)
     *   4. Close reservation (status → CLOSED)
     *
     * Then:
     *   5. Update picking_task  → status=COMPLETED, completed_at=now()
     *   6. Update sales_order   → status=DISPATCHED, dispatched_at=now()
     *
     * Guards:
     *   BR-DISPATCH-02: blocks if any item is not QC-scanned.
     *   BR-DISPATCH-03: blocks if any OPEN incident exists for the SO.
     */
    @Transactional
    public ApiResponse<Void> confirmDispatch(Long soId, Long userId) {
        log.info("Confirming dispatch for soId={}, userId={}", soId, userId);

        SalesOrderEntity so = findSalesOrder(soId);

        // Guard: SO must be in QC_SCAN status before dispatch
        if (!"QC_SCAN".equals(so.getStatus())) {
            throw new BusinessException(
                    "Dispatch can only be confirmed when sales order status is QC_SCAN. Current: " + so.getStatus());
        }

        // BR-DISPATCH-02: all items must be QC-scanned
        boolean allScanned = pickingTaskItemRepository.allItemsScannedForSo(soId);
        if (!allScanned) {
            throw new BusinessException(
                    "Dispatch blocked: some picking task items have not been QC-scanned (BR-DISPATCH-02)");
        }

        // BR-DISPATCH-03: no OPEN incidents
        long openIncidents = incidentRepository.countOpenIncidentsBySoId(soId);
        if (openIncidents > 0) {
            throw new BusinessException(
                    "Dispatch blocked: " + openIncidents + " open incident(s) must be resolved first (BR-DISPATCH-03)");
        }

        // [FIX-BUG-3] Guard: kiểm tra phải có OPEN reservation trước khi trừ tồn.
        // Nếu bước Allocate chưa chạy hoặc bị bypass, dispatch sẽ trừ quantity nhưng
        // không close được reservation → reserved_qty còn treo → tồn khả dụng bị tính sai.
        List<ReservationEntity> openReservationsGuard = reservationRepository
                .findByReferenceTableAndReferenceIdAndStatus("sales_orders", soId, "OPEN");
        if (openReservationsGuard.isEmpty()) {
            throw new BusinessException(
                    "Dispatch bị chặn: Không tìm thấy reservation nào cho đơn hàng này. "
                            + "Vui lòng thực hiện bước Phân Bổ Tồn Kho (Allocate) trước khi xuất kho.");
        }

        // Fetch PASS items for inventory deduction
        List<PickingTaskItemEntity> passItems = pickingTaskItemRepository.findPassedItemsBySoId(soId);

        if (passItems.isEmpty()) {
            throw new BusinessException("No PASS items found for dispatch. Dispatch cannot proceed.");
        }

        // Process each PASS item
        for (PickingTaskItemEntity item : passItems) {
            BigDecimal qty = item.getPickedQty().compareTo(BigDecimal.ZERO) > 0
                    ? item.getPickedQty()
                    : item.getRequiredQty();

            // [FIX] Resolve locationId đúng từ inventory_snapshot
            // fromLocationId trong picking item có thể sai (reservation cũ không có locationId)
            // → tìm lại từ snapshot để đảm bảo trừ đúng bin
            Long resolvedLocationId = item.getFromLocationId();
            if (resolvedLocationId == null) {
                resolvedLocationId = inventorySnapshotRepository
                        .findLocationIdByWarehouseSkuLot(so.getWarehouseId(), item.getSkuId(), item.getLotId());
            }
            if (resolvedLocationId == null) {
                log.warn("Cannot resolve locationId for SKU={} lot={} — skipping deduction",
                        item.getSkuId(), item.getLotId());
                continue;
            }

            // 1 & 2) Decrease quantity + reserved_qty in inventory_snapshot
            inventorySnapshotRepository.decrementQuantity(
                    so.getWarehouseId(), item.getSkuId(), item.getLotId(), resolvedLocationId, qty);

            inventorySnapshotRepository.decrementReservedByLocationSkuLot(
                    resolvedLocationId, item.getSkuId(), item.getLotId(), qty);

            // 3) Insert inventory_transaction with txn_type = DISPATCH (negative quantity)
            InventoryTransactionEntity txn = InventoryTransactionEntity.builder()
                    .warehouseId(so.getWarehouseId())
                    .skuId(item.getSkuId())
                    .lotId(item.getLotId())
                    .locationId(resolvedLocationId)
                    .quantity(qty.negate())  // negative = outbound movement
                    .txnType("DISPATCH")
                    .referenceTable("sales_orders")
                    .referenceId(soId)
                    .reasonCode("DISPATCH")
                    .createdBy(userId)
                    .build();
            inventoryTransactionRepository.save(txn);

            // 4) Close reservation cho đúng sku + lot + location
            // [FIX-CORE] Filter thêm locationId để không đóng nhầm reservation của bin khác
            // (trường hợp 1 SKU có stock ở nhiều bin, mỗi bin 1 reservation riêng).
            List<ReservationEntity> openReservations = reservationRepository
                    .findByReferenceTableAndReferenceIdAndStatus("sales_orders", soId, "OPEN");

            final Long finalResolvedLocationId = resolvedLocationId;
            openReservations.stream()
                    .filter(r -> r.getSkuId().equals(item.getSkuId())
                            && (item.getLotId() == null
                            ? r.getLotId() == null
                            : item.getLotId().equals(r.getLotId()))
                            && (r.getLocationId() == null
                            || r.getLocationId().equals(finalResolvedLocationId)))
                    .forEach(r -> {
                        r.setStatus("CLOSED");
                        reservationRepository.save(r);
                    });
        }

        // 5) Complete all active picking tasks for this SO
        List<PickingTaskEntity> activeTasks = pickingTaskRepository.findByWarehouseIdAndSoId(
                so.getWarehouseId(), soId);

        activeTasks.stream()
                .filter(t -> !"CANCELLED".equals(t.getStatus()) && !"COMPLETED".equals(t.getStatus()))
                .forEach(t -> {
                    t.setStatus("COMPLETED");
                    t.setCompletedAt(LocalDateTime.now());
                    pickingTaskRepository.save(t);
                });

        // 6) Update Sales Order → DISPATCHED
        so.setStatus("DISPATCHED");
        so.setUpdatedAt(LocalDateTime.now());
        salesOrderRepository.save(so);

        log.info("Dispatch confirmed for soId={}. SO status → DISPATCHED", soId);

        // PDF được tạo lazy khi user request GET /dispatch-pdf
        // Không gọi ở đây để tránh Cloudinary exception làm rollback transaction dispatch

        return ApiResponse.success("Order dispatched successfully. Status: DISPATCHED", null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6) FINALIZE QC — Auto-PASS all unscanned items, mark task complete
    // POST /v1/outbound/pick-list/{taskId}/finalize-qc
    // Role: KEEPER / QC
    // Called from mobile "Kết thúc Scan" button
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Finalises the QC scan session from mobile:
     *  - Ensures task is in QC_IN_PROGRESS (calls startQcSession if still PICKED)
     *  - Auto-marks all still-unscanned items as PASS
     *  - Updates task status → COMPLETED
     *  - Updates sales_order status → QC_SCAN (so web can dispatch)
     */
    @Transactional
    public ApiResponse<QcSummaryResponse> finalizeQc(Long taskId, Long userId) {
        log.info("Finalizing QC for taskId={}, userId={}", taskId, userId);

        PickingTaskEntity task = findPickingTask(taskId);

        // Auto-start QC if task is still PICKED
        if ("PICKED".equals(task.getStatus())) {
            log.info("Task {} still in PICKED — auto-starting QC", taskId);
            startQcSession(taskId, userId);
            // Re-fetch after status change
            task = findPickingTask(taskId);
        }

        if (!"QC_IN_PROGRESS".equals(task.getStatus())) {
            throw new BusinessException(
                    "Cannot finalize QC: task status is " + task.getStatus() + ". Expected QC_IN_PROGRESS.");
        }

        // Auto-PASS all items that were not manually scanned
        List<PickingTaskItemEntity> unscanned = pickingTaskItemRepository.findUnscannedByTaskId(taskId);
        LocalDateTime now = LocalDateTime.now();
        for (PickingTaskItemEntity item : unscanned) {
            item.setQcResult("PASS");
            item.setQcScannedAt(now);
            item.setQcNote("Auto-PASS by finalize-qc");
            pickingTaskItemRepository.save(item);
        }
        log.info("Auto-PASSed {} unscanned items for taskId={}", unscanned.size(), taskId);

        // Compute summary
        List<PickingTaskItemEntity> allItems = pickingTaskItemRepository.findByPickingTaskId(taskId);
        int total   = allItems.size();
        int pass    = (int) allItems.stream().filter(i -> "PASS".equals(i.getQcResult())).count();
        int fail    = (int) allItems.stream().filter(i -> "FAIL".equals(i.getQcResult())).count();
        int hold    = (int) allItems.stream().filter(i -> "HOLD".equals(i.getQcResult())).count();
        int pending = (int) allItems.stream().filter(i -> i.getQcScannedAt() == null).count();

        // Update SO → QC_SCAN so web knows QC is done and can dispatch
        if (task.getSoId() != null) {
            salesOrderRepository.findById(task.getSoId()).ifPresent(so -> {
                if ("PICKING".equals(so.getStatus()) || "QC_SCAN".equals(so.getStatus())) {
                    so.setStatus("QC_SCAN");
                    so.setUpdatedAt(LocalDateTime.now());
                    salesOrderRepository.save(so);
                    log.info("SO {} status → QC_SCAN", so.getSoCode());
                }
            });
        }

        QcSummaryResponse summary = QcSummaryResponse.builder()
                .pickingTaskId(taskId)
                .totalItems(total)
                .passCount(pass)
                .failCount(fail)
                .holdCount(hold)
                .pendingCount(pending)
                .allScanned(pending == 0 && total > 0)
                .build();

        log.info("QC finalized for taskId={}: pass={}, fail={}, hold={}", taskId, pass, fail, hold);
        return ApiResponse.success("QC finalized. All items processed.", summary);
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

    private DispatchNoteResponse.DispatchNoteItem buildDispatchNoteItem(PickingTaskItemEntity item) {
        // Resolve SKU code
        SkuEntity sku = skuRepository.findById(item.getSkuId()).orElse(null);
        String skuCode = sku != null ? sku.getSkuCode() : "N/A";
        String skuName = sku != null ? sku.getSkuName() : "N/A";
        String unit    = sku != null ? sku.getUnit()    : "";

        // Resolve lot info
        String lotNumber       = null;
        String manufactureDate = null;
        String expiryDate      = null;
        if (item.getLotId() != null) {
            InventoryLotEntity lot = inventoryLotRepository.findById(item.getLotId()).orElse(null);
            if (lot != null) {
                lotNumber       = lot.getLotNumber();
                manufactureDate = lot.getManufactureDate() != null ? lot.getManufactureDate().toString() : null;
                expiryDate      = lot.getExpiryDate() != null ? lot.getExpiryDate().toString() : null;
            }
        }

        // Resolve location code
        String locationCode = locationRepository.findById(item.getFromLocationId())
                .map(LocationEntity::getLocationCode)
                .orElse("N/A");

        BigDecimal qty = item.getPickedQty().compareTo(BigDecimal.ZERO) > 0
                ? item.getPickedQty()
                : item.getRequiredQty();

        return DispatchNoteResponse.DispatchNoteItem.builder()
                .skuCode(skuCode)
                .skuName(skuName)
                .unit(unit)
                .lotNumber(lotNumber)
                .manufactureDate(manufactureDate)
                .expiryDate(expiryDate)
                .locationCode(locationCode)
                .quantity(qty)
                .build();
    }
}