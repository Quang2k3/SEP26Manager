package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.infrastructure.SseEmitterRegistry;
import org.example.sep26management.application.dto.request.GeneratePickListRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.PickListResponse;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * SCRUM-511: UC-WXE-06 Generate Pick List
 * BR-WXE-22: only from allocated stock
 * BR-WXE-23: optimal picking route (zone → location code order)
 * BR-WXE-24: SKU, lot, location traceability
 *
 * Luồng trừ tồn kho (sau refactor bỏ Z-OUT):
 *   confirmPicked → trừ quantity + reserved_qty + close reservation + ghi txn PICK
 *   confirmDispatch → chỉ set DISPATCHED, KHÔNG trừ tồn nữa
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PickListService {

    private final SalesOrderJpaRepository soRepository;
    private final TransferJpaRepository transferRepository;
    private final ReservationQueryRepository revReservationQueryRepository;
    private final PickingTaskJpaRepository pickingTaskRepository;
    private final PickingTaskItemJpaRepository pickingTaskItemRepository;
    private final PickingTaskItemExtendedRepository pickingTaskItemExtendedRepository;
    private final LocationJpaRepository locationRepository;
    private final ZoneJpaRepository zoneRepository;
    private final SkuJpaRepository skuRepository;
    private final InventoryLotJpaRepository lotRepository;
    private final AuditLogService auditLogService;
    // Dependencies added for inventory deduction at confirmPicked
    private final InventorySnapshotJpaRepository snapshotRepository;
    private final InventoryTransactionJpaRepository txnRepository;
    private final ReservationJpaRepository reservationRepository;
    private final NotificationService notificationService;
    private final SseEmitterRegistry sseRegistry;
    private final com.cloudinary.Cloudinary cloudinary;

    @Transactional
    public ApiResponse<PickListResponse> generatePickList(
            GeneratePickListRequest request,
            Long userId, String ip, String ua) {

        log.info("Generating pick list for documentId={}, type={}", request.getDocumentId(), request.getOrderType());

        Long warehouseId;
        String documentCode;
        String refTable;

        if (request.getOrderType() == OutboundType.SALES_ORDER) {
            SalesOrderEntity so = soRepository.findById(request.getDocumentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            String.format(MessageConstants.OUTBOUND_NOT_FOUND, request.getDocumentId())));

            if (!"ALLOCATED".equals(so.getStatus())) {
                throw new BusinessException(
                        "Đơn hàng phải được phân bổ tồn kho đầy đủ (ALLOCATED) trước khi tạo Pick List. "
                                + "Trạng thái hiện tại: " + so.getStatus()
                                + ". Vui lòng thực hiện bước Phân Bổ Tồn Kho trước.");
            }
            warehouseId = so.getWarehouseId();
            documentCode = so.getSoCode();
            refTable = "sales_orders";
        } else {
            TransferEntity transfer = transferRepository.findById(request.getDocumentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            String.format(MessageConstants.OUTBOUND_NOT_FOUND, request.getDocumentId())));

            if (!"ALLOCATED".equals(transfer.getStatus())) {
                throw new BusinessException(
                        "Lệnh chuyển kho phải được phân bổ tồn kho đầy đủ (ALLOCATED) trước khi tạo Pick List. "
                                + "Trạng thái hiện tại: " + transfer.getStatus());
            }
            warehouseId = transfer.getFromWarehouseId();
            documentCode = transfer.getTransferCode();
            refTable = "transfers";
        }

        List<ReservationEntity> reservations = revReservationQueryRepository
                .findByReferenceTableAndReferenceIdAndStatus(refTable, request.getDocumentId(), "OPEN");

        if (reservations.isEmpty()) {
            throw new BusinessException(MessageConstants.PICKLIST_NO_ALLOCATION);
        }

        // Huỷ pick task cũ nếu có (bỏ qua task đã COMPLETED để bảo lưu lịch sử nhặt hàng PASS)
        List<PickingTaskEntity> existing = pickingTaskRepository
                .findByWarehouseIdAndSoId(warehouseId, request.getDocumentId());
        existing.stream()
                .filter(t -> !"CANCELLED".equals(t.getStatus()) && !"COMPLETED".equals(t.getStatus()))
                .forEach(t -> {
                    t.setStatus("CANCELLED");
                    pickingTaskRepository.save(t);
                });

        String taskCode = generatePickTaskCode(warehouseId);
        PickingTaskEntity task = PickingTaskEntity.builder()
                .warehouseId(warehouseId)
                .soId(request.getOrderType() == OutboundType.SALES_ORDER ? request.getDocumentId() : null)
                .status("OPEN")
                .priority(3)
                .assignedTo(request.getAssignedTo())
                .build();
        PickingTaskEntity savedTask = pickingTaskRepository.save(task);

        List<PickingTaskItemEntity> taskItems = new ArrayList<>();

        for (ReservationEntity res : reservations) {
            Long fromLocation = res.getLocationId();
            if (fromLocation == null) {
                fromLocation = resolveLocationForReservation(res, warehouseId);
                log.warn("Reservation {} locationId=null — fallback query. Re-allocate để đảm bảo đúng bin.",
                        res.getReservationId());
            }
            PickingTaskItemEntity item = PickingTaskItemEntity.builder()
                    .pickingTaskId(savedTask.getPickingTaskId())
                    .skuId(res.getSkuId())
                    .lotId(res.getLotId())
                    .fromLocationId(fromLocation)
                    .requiredQty(res.getQuantity())
                    .pickedQty(BigDecimal.ZERO)
                    .build();
            taskItems.add(item);
        }

        List<PickingTaskItemEntity> savedItems = pickingTaskItemRepository.saveAll(taskItems);

        // Build response
        int seq = 1;
        List<PickListResponse.PickListItem> responseItems = new ArrayList<>();
        for (int i = 0; i < savedItems.size(); i++) {
            PickingTaskItemEntity item = savedItems.get(i);
            ReservationEntity res = reservations.get(i);

            LocationEntity loc = locationRepository.findById(item.getFromLocationId()).orElse(null);
            String zoneCode = null;
            String rackCode = null;
            if (loc != null && loc.getZoneId() != null) {
                zoneCode = zoneRepository.findById(loc.getZoneId()).map(z -> z.getZoneCode()).orElse(null);
            }
            if (loc != null && loc.getParentLocationId() != null) {
                rackCode = locationRepository.findById(loc.getParentLocationId())
                        .map(LocationEntity::getLocationCode).orElse(null);
            }

            var skuOpt = skuRepository.findById(item.getSkuId());
            String skuCode    = skuOpt.map(s -> s.getSkuCode()).orElse(null);
            String skuName    = skuOpt.map(s -> s.getSkuName()).orElse(null);
            String skuBarcode = skuOpt.map(s -> s.getBarcode()).orElse(null);
            String lotNumber  = null;
            LocalDate expiryDate = null;
            if (item.getLotId() != null) {
                var lot = lotRepository.findById(item.getLotId()).orElse(null);
                if (lot != null) { lotNumber = lot.getLotNumber(); expiryDate = lot.getExpiryDate(); }
            }

            responseItems.add(PickListResponse.PickListItem.builder()
                    .sequence(seq++).pickingTaskItemId(item.getPickingTaskItemId())
                    .locationId(item.getFromLocationId())
                    .locationCode(loc != null ? loc.getLocationCode() : null)
                    .zoneCode(zoneCode).rackCode(rackCode)
                    .skuId(item.getSkuId()).skuCode(skuCode).skuName(skuName).barcode(skuBarcode)
                    .lotId(item.getLotId()).lotNumber(lotNumber).expiryDate(expiryDate)
                    .requiredQty(item.getRequiredQty()).pickedQty(item.getPickedQty())
                    .status("PENDING")
                    .build());
        }

        responseItems.sort(Comparator
                .comparing((PickListResponse.PickListItem r) -> r.getZoneCode() != null ? r.getZoneCode() : "")
                .thenComparing(r -> r.getLocationCode() != null ? r.getLocationCode() : ""));
        for (int i = 0; i < responseItems.size(); i++) responseItems.get(i).setSequence(i + 1);

        if (request.getOrderType() == OutboundType.SALES_ORDER) {
            soRepository.findById(request.getDocumentId()).ifPresent(so -> {
                so.setStatus("PICKING");
                soRepository.save(so);
                log.info("SO {} → PICKING", so.getSoCode());

                // ── Realtime: notify KEEPER có pick task mới cần thực hiện ──────
                notificationService.notifyRole("KEEPER", "outbound_pick_pending",
                        so.getSoId(), so.getSoCode(), documentCode + " — cần lấy hàng");

                // ── [FIX] Realtime: notify QC đơn xuất vào PICKING, sẵn sàng cần QC ──
                notificationService.notifyRole("QC", "qc_outbound_pending",
                        so.getSoId(), so.getSoCode(), documentCode + " — cần QC kiểm định");
            });
        } else {
            transferRepository.findById(request.getDocumentId()).ifPresent(t -> {
                t.setStatus("PICKING");
                transferRepository.save(t);
            });
        }

        auditLogService.logAction(userId, "PICK_LIST_GENERATED",
                request.getOrderType() == OutboundType.SALES_ORDER ? "SALES_ORDER" : "TRANSFER",
                request.getDocumentId(),
                "Pick list " + taskCode + " generated for " + documentCode, ip, ua);

        return ApiResponse.success(MessageConstants.PICKLIST_GENERATED_SUCCESS,
                PickListResponse.builder()
                        .pickingTaskId(savedTask.getPickingTaskId())
                        .pickingTaskCode(taskCode)
                        .documentId(request.getDocumentId())
                        .documentCode(documentCode)
                        .status("OPEN")
                        .assignedTo(savedTask.getAssignedTo())
                        .assignedQcId(savedTask.getAssignedQcId())
                        .items(responseItems)
                        .generatedAt(LocalDateTime.now())
                        .build());
    }

    @Transactional(readOnly = true)
    public ApiResponse<PickListResponse> getPickList(Long pickingTaskId) {
        PickingTaskEntity task = pickingTaskRepository.findById(pickingTaskId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.PICKLIST_NOT_FOUND, pickingTaskId)));

        List<PickingTaskItemEntity> items = pickingTaskItemExtendedRepository.findByPickingTaskId(pickingTaskId);

        List<PickListResponse.PickListItem> responseItems = new ArrayList<>();
        int seq = 1;
        for (PickingTaskItemEntity item : items) {
            LocationEntity loc = locationRepository.findById(item.getFromLocationId()).orElse(null);
            String zoneCode = (loc != null && loc.getZoneId() != null)
                    ? zoneRepository.findById(loc.getZoneId()).map(z -> z.getZoneCode()).orElse(null) : null;
            var skuOpt = skuRepository.findById(item.getSkuId());
            String skuCode    = skuOpt.map(s -> s.getSkuCode()).orElse(null);
            String skuName    = skuOpt.map(s -> s.getSkuName()).orElse(null);
            String skuBarcode = skuOpt.map(s -> s.getBarcode()).orElse(null);
            String lotNumber  = null;
            LocalDate expiryDate = null;
            if (item.getLotId() != null) {
                var lot = lotRepository.findById(item.getLotId()).orElse(null);
                if (lot != null) { lotNumber = lot.getLotNumber(); expiryDate = lot.getExpiryDate(); }
            }

            responseItems.add(PickListResponse.PickListItem.builder()
                    .sequence(seq++).pickingTaskItemId(item.getPickingTaskItemId())
                    .locationId(item.getFromLocationId())
                    .locationCode(loc != null ? loc.getLocationCode() : null)
                    .zoneCode(zoneCode)
                    .skuId(item.getSkuId()).skuCode(skuCode).skuName(skuName).barcode(skuBarcode)
                    .lotId(item.getLotId()).lotNumber(lotNumber).expiryDate(expiryDate)
                    .requiredQty(item.getRequiredQty()).pickedQty(item.getPickedQty())
                    .status(item.getPickedQty().compareTo(item.getRequiredQty()) >= 0 ? "PICKED" : "PENDING")
                    .qcResult(item.getQcResult()).qcScannedAt(item.getQcScannedAt())
                    .build());
        }

        responseItems.sort(Comparator
                .comparing((PickListResponse.PickListItem r) -> r.getZoneCode() != null ? r.getZoneCode() : "")
                .thenComparing(r -> r.getLocationCode() != null ? r.getLocationCode() : ""));

        return ApiResponse.success("Pick list retrieved", PickListResponse.builder()
                .pickingTaskId(task.getPickingTaskId())
                .documentId(task.getSoId())
                .status(task.getStatus())
                .assignedTo(task.getAssignedTo())
                .assignedQcId(task.getAssignedQcId())
                .items(responseItems)
                .mispickResolutionNoteUrl(task.getMispickResolutionNoteUrl())
                .build());
    }

    /**
     * Keeper xác nhận đã lấy đủ hàng.
     *
     * Sau refactor bỏ Z-OUT, bước này chịu trách nhiệm trừ tồn kho:
     *   1. decrementQuantity  — trừ quantity trong BIN
     *   2. decrementReserved  — giải phóng reserved_qty
     *   3. Close reservation  — status → CLOSED
     *   4. Ghi inventory_transaction txnType=PICK
     *   5. task → PICKED, SO → QC_SCAN
     *
     * confirmDispatch sau này chỉ còn task → COMPLETED, SO → DISPATCHED.
     */
    @Transactional
    public ApiResponse<PickListResponse> confirmPicked(Long taskId, Long userId, String ip, String ua) {
        try {
            log.info("confirmPicked: taskId={}, userId={}", taskId, userId);

            PickingTaskEntity task = pickingTaskRepository.findById(taskId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            String.format(MessageConstants.PICKLIST_NOT_FOUND, taskId)));

            // ── Giải pháp 3: Validate Keeper ownership ────────────────────────────
            if (task.getAssignedTo() != null && !task.getAssignedTo().equals(userId)) {
                throw new BusinessException(
                        "Bạn không có quyền xác nhận Pick List #" + taskId
                                + ". Task này đang được Keeper khác thực hiện.");
            }

            if (!("OPEN".equals(task.getStatus()) || "IN_PROGRESS".equals(task.getStatus()))) {
                throw new BusinessException(
                        "Không thể xác nhận: task phải ở OPEN hoặc IN_PROGRESS. Hiện: " + task.getStatus());
            }

            Long warehouseId = task.getWarehouseId();

            // Set pickedQty = requiredQty cho item chưa được đánh dấu
            List<PickingTaskItemEntity> items = pickingTaskItemExtendedRepository.findByPickingTaskId(taskId);
            for (PickingTaskItemEntity item : items) {
                if (item.getPickedQty() == null || item.getPickedQty().compareTo(BigDecimal.ZERO) == 0) {
                    item.setPickedQty(item.getRequiredQty());
                    pickingTaskItemRepository.save(item);
                }
            }

            // Trừ tồn kho theo từng item
            for (PickingTaskItemEntity item : items) {
                BigDecimal qty = item.getPickedQty().compareTo(BigDecimal.ZERO) > 0
                        ? item.getPickedQty() : item.getRequiredQty();

                Long locationId = item.getFromLocationId();

                // 1) Giải phóng reserved_qty TRƯỚC — constraint chk_reserved_lte_quantity
                //    yêu cầu reserved_qty <= quantity tại mọi thời điểm.
                //    Nếu trừ quantity trước → quantity=0 nhưng reserved>0 → vi phạm constraint → JDBC error.
                snapshotRepository.decrementReservedByLocationSkuLot(
                        locationId, item.getSkuId(), item.getLotId(), qty);

                // 2) Trừ quantity trong BIN sau khi reserved đã về 0
                snapshotRepository.decrementQuantity(
                        warehouseId, item.getSkuId(), item.getLotId(), locationId, qty);

                // 3) Ghi inventory_transaction txnType = PICK
                txnRepository.save(InventoryTransactionEntity.builder()
                        .warehouseId(warehouseId)
                        .skuId(item.getSkuId())
                        .lotId(item.getLotId())
                        .locationId(locationId)
                        .quantity(qty.negate())
                        .txnType("PICK")
                        .referenceTable("picking_tasks")
                        .referenceId(taskId)
                        .createdBy(userId)
                        .build());
            }

            // 4) Close OPEN reservations liên quan đến document của task này
            //    Với SO: refTable=sales_orders, refId=soId
            //    Với Transfer: tìm từ reservations theo items
            if (task.getSoId() != null) {
                List<ReservationEntity> openRes = reservationRepository
                        .findByReferenceTableAndReferenceIdAndStatus("sales_orders", task.getSoId(), "OPEN");
                openRes.forEach(r -> {
                    r.setStatus("CLOSED");
                    reservationRepository.save(r);
                });
            } else {
                // Internal Transfer: close reservation bằng cách match sku + location từ items
                for (PickingTaskItemEntity item : items) {
                    List<ReservationEntity> openRes = reservationRepository
                            .findByReferenceTableAndReferenceIdAndStatus("transfers", item.getPickingTaskId(), "OPEN");
                    // fallback nếu không tìm được theo task: tìm theo sku+location+warehouse OPEN
                    if (openRes.isEmpty()) {
                        openRes = reservationRepository.findOpenByWarehouseSkuLocation(
                                warehouseId, item.getSkuId(), item.getFromLocationId());
                    }
                    final Long locId = item.getFromLocationId();
                    final Long skuId = item.getSkuId();
                    openRes.stream()
                            .filter(r -> r.getSkuId().equals(skuId)
                                    && (r.getLocationId() == null || r.getLocationId().equals(locId)))
                            .forEach(r -> {
                                r.setStatus("CLOSED");
                                reservationRepository.save(r);
                            });
                }
            }

            // 5) Task → PICKED
            task.setStatus("PICKED");
            if (task.getStartedAt() == null) task.setStartedAt(LocalDateTime.now());
            pickingTaskRepository.save(task);

            // 6) SO → QC_SCAN
            if (task.getSoId() != null) {
                soRepository.findById(task.getSoId()).ifPresent(so -> {
                    so.setStatus("QC_SCAN");
                    soRepository.save(so);
                    log.info("SO {} → QC_SCAN after confirmPicked", so.getSoCode());
                });
            }

            auditLogService.logAction(userId, "PICKING_CONFIRMED", "picking_tasks", taskId,
                    "Pick task " + taskId + " confirmed PICKED — tồn kho đã trừ trực tiếp từ BIN", ip, ua);

            // Release Keeper claim — task đã PICKED, Keeper khác có thể xem nhưng không nhận lại
            try { pickingTaskRepository.releaseKeeperAssignment(taskId, userId); } catch (Exception ignored) {}
            log.info("confirmPicked OK: taskId={} → PICKED, inventory deducted", taskId);
            return getPickList(taskId);
        } catch (Exception e) {
            String trace = e.getStackTrace().length > 0 ? e.getStackTrace()[0].toString() : "No trace";
            String rootCause = e.getCause() != null ? e.getCause().getMessage() : "No cause";
            throw new BusinessException("ERROR 500 DEBUG: " + e.getClass().getSimpleName() + " | " + e.getMessage() + " | Cause: " + rootCause + " | Trace: " + trace);
        }
    }

    @Transactional(readOnly = true)
    public ApiResponse<PickListResponse> getPickListByDocument(Long documentId, Long warehouseId) {
        List<PickingTaskEntity> tasks = pickingTaskRepository
                .findByWarehouseIdAndSoId(warehouseId, documentId);

        PickingTaskEntity active = tasks.stream()
                .filter(t -> !"CANCELLED".equals(t.getStatus()) && !"COMPLETED".equals(t.getStatus()))
                .findFirst()
                .orElse(tasks.isEmpty() ? null : tasks.get(tasks.size() - 1));

        if (active == null) {
            throw new ResourceNotFoundException("Không tìm thấy Pick List cho đơn #" + documentId);
        }
        return getPickList(active.getPickingTaskId());
    }

    private Long resolveLocationForReservation(ReservationEntity res, Long warehouseId) {
        return revReservationQueryRepository.findLocationForReservation(
                warehouseId, res.getSkuId(), res.getLotId());
    }

    private String generatePickTaskCode(Long warehouseId) {
        String date  = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        LocalDate today = LocalDate.now();
        long count = pickingTaskRepository.countTodayByWarehouse(
                warehouseId, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        return String.format("PKL-%s-%04d", date, count);
    }

    /**
     * [FIX REALTIME] Keeper scan 1 item → cập nhật pickedQty lên DB ngay lập tức.
     * Web poll fetchPickList mỗi 2s sẽ thấy số lượng đã quét realtime.
     * PATCH /v1/outbound/pick-list/{taskId}/items/{itemId}/scan
     */
    @Transactional
    public ApiResponse<Void> scanPickItem(Long taskId, Long itemId, java.math.BigDecimal pickedQty, String sessionId, String scannedLotNumber) {
        PickingTaskItemEntity item = pickingTaskItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("PickingTaskItem not found: " + itemId));
        if (!item.getPickingTaskId().equals(taskId)) {
            throw new BusinessException("Item " + itemId + " không thuộc task " + taskId);
        }

        // Validate LOT
        if (scannedLotNumber != null && !scannedLotNumber.isBlank() && item.getLotId() != null) {
            lotRepository.findById(item.getLotId()).ifPresent(lot -> {
                if (!lot.getLotNumber().equalsIgnoreCase(scannedLotNumber.trim())) {
                    throw new BusinessException("Mã LOT quét (" + scannedLotNumber + ") không khớp với mã LOT được phân bổ (" + lot.getLotNumber() + ")");
                }
            });
        }

        // Cập nhật pickedQty — ném lỗi nếu quét thừa hàng
        if (pickedQty.compareTo(item.getRequiredQty()) > 0) {
            throw new BusinessException("Số lượng lấy vượt quá yêu cầu lệnh! Thừa hàng, yêu cầu kiểm tra và trả lại vị trí cũ.");
        }
        item.setPickedQty(pickedQty);
        pickingTaskItemRepository.save(item);
        log.info("scanPickItem: taskId={} itemId={} pickedQty={}", taskId, itemId, pickedQty);

        // [FIX REALTIME] Push SSE snapshot toàn bộ pick items → web nhận ngay, không cần poll
        if (sessionId != null && !sessionId.isBlank()) {
            try {
                var allItems = pickingTaskItemExtendedRepository.findByPickingTaskId(taskId);
                java.util.List<java.util.Map<String, Object>> itemSnapshots = allItems.stream().map(it -> {
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("pickingTaskItemId", it.getPickingTaskItemId());
                    m.put("skuId",             it.getSkuId());
                    m.put("requiredQty",       it.getRequiredQty());
                    m.put("pickedQty",         it.getPickedQty() != null ? it.getPickedQty() : BigDecimal.ZERO);
                    // skuCode từ sku repo
                    skuRepository.findById(it.getSkuId()).ifPresent(s -> {
                        m.put("skuCode", s.getSkuCode());
                        m.put("skuName", s.getSkuName());
                    });
                    return m;
                }).collect(java.util.stream.Collectors.toList());

                java.util.Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("type",    "picking_scan");
                payload.put("taskId",  taskId);
                payload.put("items",   itemSnapshots);
                sseRegistry.send(sessionId, payload);
            } catch (Exception e) {
                log.warn("scanPickItem SSE push failed: {}", e.getMessage());
            }
        }
        return ApiResponse.success("Picked qty updated", null);
    }

    @Transactional
    public ApiResponse<Object> cancelPickTask(Long taskId, Long userId, String ip, String ua) {
        log.info("cancelPickTask: taskId={}, userId={}", taskId, userId);

        PickingTaskEntity task = pickingTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Pick List #" + taskId));

        if (!("OPEN".equals(task.getStatus()) || "IN_PROGRESS".equals(task.getStatus()))) {
            throw new BusinessException("Chỉ có thể huỷ Pick List ở trạng thái OPEN hoặc IN_PROGRESS");
        }

        task.setStatus("CANCELLED");
        pickingTaskRepository.save(task);

        Long documentId = task.getSoId();
        String refTable = "sales_orders";

        // Internal transfer workaround
        if (documentId == null) {
            var items = pickingTaskItemExtendedRepository.findByPickingTaskId(taskId);
            if (!items.isEmpty() && items.get(0).getPickingTaskId() != null) {
                // If it is a transfer, we will try to clean up reservations based on pickingTaskId if mapped
            }
        }

        java.util.List<java.util.Map<String, Object>> restockedItems = new java.util.ArrayList<>();

        if (documentId != null) {
            // Giải phóng tồn kho: huỷ tất cả OPEN reservations của đơn hàng này
            List<ReservationEntity> existingReservations = reservationRepository
                    .findByReferenceTableAndReferenceIdAndStatus(refTable, documentId, "OPEN");
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

            // [NEW] Hoàn trả hàng đã PASS QC từ các vòng repick trước
            List<PickingTaskItemEntity> passedItems = pickingTaskItemRepository.findPassedItemsBySoId(documentId);
            for (PickingTaskItemEntity pItem : passedItems) {
                java.math.BigDecimal passQty = pItem.getQcPassQty();
                if (passQty != null && passQty.compareTo(java.math.BigDecimal.ZERO) > 0 && pItem.getFromLocationId() != null) {
                    // Trả lại kho vật lý (cộng available)
                    snapshotRepository.upsertInventory(
                            task.getWarehouseId(), pItem.getSkuId(), pItem.getLotId(),
                            pItem.getFromLocationId(), passQty);

                    txnRepository.save(InventoryTransactionEntity.builder()
                            .warehouseId(task.getWarehouseId())
                            .skuId(pItem.getSkuId())
                            .lotId(pItem.getLotId())
                            .locationId(pItem.getFromLocationId())
                            .quantity(passQty)
                            .txnType("RESTOCK_CANCEL")
                            .referenceTable("sales_orders")
                            .referenceId(documentId)
                            .reasonCode("SO_CANCELLED_RESTOCK")
                            .createdBy(userId)
                            .build());

                    String skuCode = skuRepository.findById(pItem.getSkuId()).map(s -> s.getSkuCode()).orElse("Unknown");
                    String skuName = skuRepository.findById(pItem.getSkuId()).map(s -> s.getSkuName()).orElse("Unknown");
                    String locCode = locationRepository.findById(pItem.getFromLocationId()).map(l -> l.getLocationCode()).orElse("Unknown");
                    String lotNumber = pItem.getLotId() != null 
                        ? lotRepository.findById(pItem.getLotId()).map(l -> l.getLotNumber()).orElse(null) 
                        : null;

                    java.util.Map<String, Object> rItem = new java.util.HashMap<>();
                    rItem.put("skuCode", skuCode);
                    rItem.put("skuName", skuName);
                    rItem.put("lotNumber", lotNumber);
                    rItem.put("locationCode", locCode);
                    rItem.put("quantity", passQty);
                    restockedItems.add(rItem);
                    
                    log.info("RESTOCK PASS ITEMS: soId={} skuId={} qty={} loc={}", documentId, pItem.getSkuId(), passQty, pItem.getFromLocationId());
                }
            }

            // Cập nhật: Khi huỷ phiên lấy hàng (Pick List) thì huỷ luôn lệnh xuất kho (Sales Order)
            soRepository.findById(documentId).ifPresent(so -> {
                so.setStatus("CANCELLED");
                soRepository.save(so);
                log.info("SO {} closed (CANCELLED) after pick task cancelled", so.getSoCode());
            });
        }

        auditLogService.logAction(userId, "PICKING_CANCELLED", "picking_tasks", taskId,
                "Pick task " + taskId + " cancelled. Reservations released and SO reverted to ALLOCATED.", ip, ua);

        // Giải phóng claim
        try { pickingTaskRepository.releaseKeeperAssignment(taskId, userId); } catch (Exception ignored) {}

        java.util.Map<String, Object> resultData = new java.util.HashMap<>();
        resultData.put("restockedItems", restockedItems);

        return ApiResponse.success("Đã huỷ lấy hàng và giải phóng tồn kho thành công.", resultData);
    }

    @Transactional
    public ApiResponse<java.util.Map<String, String>> uploadMispickResolutionNote(Long taskId, org.springframework.web.multipart.MultipartFile file, String mispickJson) {
        PickingTaskEntity task = pickingTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("Pick List không tồn tại: " + taskId));

        if (file == null || file.isEmpty())
            throw new BusinessException("Vui lòng chọn ảnh kiện hàng bị lấy nhầm.");

        try {
            String publicId = "mispick_notes/task_" + taskId + "_" + System.currentTimeMillis();

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    com.cloudinary.utils.ObjectUtils.asMap(
                            "public_id",     publicId,
                            "resource_type", "image",
                            "overwrite",     true,
                            "quality",       "auto",
                            "fetch_format",  "auto"
                    )
            );

            String url = (String) result.get("secure_url");
            if (url == null) throw new BusinessException("Upload thất bại. Vui lòng thử lại.");

            task.setMispickResolutionNoteUrl(url);
            pickingTaskRepository.save(task);

            log.info("Mispick resolution note uploaded for taskId={}: {}", taskId, url);

            // ─── Xử lý Auto-Relocation dựa trên Mispick JSON ───
            if (mispickJson != null && !mispickJson.isBlank() && !mispickJson.equals("[]")) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    java.util.List<java.util.Map<String, Object>> mispickQueue = mapper.readValue(
                            mispickJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
                    
                    for (java.util.Map<String, Object> entry : mispickQueue) {
                        String barcode = (String) entry.get("barcode");
                        String lotNumber = (String) entry.get("lotNumber");
                        Object qtyObj = entry.get("qty");
                        java.math.BigDecimal qty = qtyObj != null ? new java.math.BigDecimal(qtyObj.toString()) : java.math.BigDecimal.ONE;

                        // 1. Tìm SKU
                        org.example.sep26management.infrastructure.persistence.entity.SkuEntity sku = skuRepository.findBySkuCode(barcode).orElse(null);
                        if (sku == null) {
                            sku = skuRepository.findByBarcode(barcode).orElse(null);
                        }
                        if (sku == null) continue;

                        Long warehouseId = task.getWarehouseId();

                        // 2. Lookup Source Location ID (Thuật toán "Lớn nhất gánh team")
                        Long sourceLocationId = snapshotRepository.findLargestSourceLocationIdForRelocation(
                                warehouseId, sku.getSkuId(), lotNumber);

                        List<String> optimalBinCodes = snapshotRepository.findOptimalBinLocationByWarehouseAndSkuAndLot(
                                warehouseId, sku.getSkuId(), lotNumber);

                        if (sourceLocationId != null && !optimalBinCodes.isEmpty()) {
                            String targetBinCode = optimalBinCodes.get(0);
                            org.example.sep26management.infrastructure.persistence.entity.LocationEntity targetLoc = 
                                    locationRepository.findByLocationCode(targetBinCode).orElse(null);
                            
                            if (targetLoc != null && !targetLoc.getLocationId().equals(sourceLocationId)) {
                                Long lotId = lotNumber != null ? lotRepository.findByLotNumber(lotNumber).map(l -> l.getLotId()).orElse(null) : null;
                                
                                // Trừ Source
                                snapshotRepository.upsertInventory(warehouseId, sku.getSkuId(), lotId, sourceLocationId, qty.negate());
                                // Cộng Target (Bin mới)
                                snapshotRepository.upsertInventory(warehouseId, sku.getSkuId(), lotId, targetLoc.getLocationId(), qty);
                                
                                // Ghi Audit Log - Relocation - Minus
                                txnRepository.save(org.example.sep26management.infrastructure.persistence.entity.InventoryTransactionEntity.builder()
                                        .warehouseId(warehouseId).skuId(sku.getSkuId()).lotId(lotId)
                                        .locationId(sourceLocationId).quantity(qty.negate())
                                        .txnType("RELOCATION").referenceTable("picking_tasks").referenceId(taskId)
                                        .reasonCode("MISPICK_AUTO_RELOCATION_MINUS").createdBy(task.getAssignedTo() == null ? 1L : task.getAssignedTo())
                                        .build());
                                
                                // Ghi Audit Log - Relocation - Plus
                                txnRepository.save(org.example.sep26management.infrastructure.persistence.entity.InventoryTransactionEntity.builder()
                                        .warehouseId(warehouseId).skuId(sku.getSkuId()).lotId(lotId)
                                        .locationId(targetLoc.getLocationId()).quantity(qty)
                                        .txnType("RELOCATION").referenceTable("picking_tasks").referenceId(taskId)
                                        .reasonCode("MISPICK_AUTO_RELOCATION_PLUS").createdBy(task.getAssignedTo() == null ? 1L : task.getAssignedTo())
                                        .build());
                                        
                                log.info("Auto-Relocated SKU: {}, Qty: {}, From: {}, To: {}", 
                                        sku.getSkuCode(), qty, sourceLocationId, targetLoc.getLocationId());
                            }
                        }
                    }
                } catch (Exception ex) {
                    log.error("Failed to parse or execute auto-relocation for mispickJson: {}", ex.getMessage(), ex);
                    // Lỗi background không throw để tránh làm đứt mạch upload ảnh của người dùng
                }
            }

            return ApiResponse.success("Đã lưu ảnh bằng chứng cất lại hàng và hoàn tất hạch toán kho thành công.", java.util.Map.of(
                    "taskId", String.valueOf(taskId),
                    "url",    url
            ));

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Upload mispick resolution note failed for taskId={}: {}", taskId, e.getMessage(), e);
            throw new BusinessException("Không thể upload ảnh: " + e.getMessage());
        }
    }
}