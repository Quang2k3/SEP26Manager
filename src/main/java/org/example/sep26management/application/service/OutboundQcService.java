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
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * OutboundQcService — QC Scan + Dispatch for Sales Order outbound flow.
 *
 * [V20 changes]:
 *  - finalizeQc: khi có FAIL → tạo Incident(DAMAGE) + set SO → ON_HOLD
 *  - resolveOutboundDamage: Manager xử lý DAMAGE (RETURN_SCRAP / ACCEPT)
 *  - resolveOutboundShortage: Manager xử lý SHORTAGE (WAIT_BACKORDER / CLOSE_SHORT)
 *
 * [FIX DUPLICATE] finalizeQc bây giờ idempotent:
 *  - Nếu task đã ở QC_DONE / COMPLETED / CANCELLED → trả về summary hiện tại, không tạo incident mới
 *  - Nếu incident DAMAGE OPEN đã tồn tại cho soId → không tạo thêm
 *  - Điều này ngăn chặn việc điện thoại bấm "Xác nhận" rồi web modal poll
 *    và gọi finalizeQc lần 2, tạo ra 2 incident + 2 notification cho Manager
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
    private final ReservationJpaRepository reservationJpaRepository; // [FIX] dùng để tạo mới Reservation sau RETURN_SCRAP
    private final IncidentJpaRepository incidentRepository;
    private final IncidentItemJpaRepository incidentItemRepository;
    private final InventoryLotJpaRepository inventoryLotRepository;
    private final LocationJpaRepository locationRepository;
    private final ZoneJpaRepository zoneRepository;
    private final SkuJpaRepository skuRepository;
    private final UserJpaRepository userRepository;
    private final WarehouseJpaRepository warehouseRepository;
    private final CustomerJpaRepository customerRepository;
    private final DispatchPdfService dispatchPdfService;
    private final NotificationService notificationService;

    // ─────────────────────────────────────────────────────────────────────────
    // 1) START QC SESSION
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public ApiResponse<Void> startQcSession(Long taskId, Long userId) {
        PickingTaskEntity task = findPickingTask(taskId);
        if (!"PICKED".equals(task.getStatus()) && !"QC_IN_PROGRESS".equals(task.getStatus())) {
            throw new BusinessException(
                    "QC session chỉ khởi động được khi task ở PICKED hoặc QC_IN_PROGRESS. Hiện: " + task.getStatus());
        }

        // ── Giải pháp 4: QC claim ngay khi bấm "QC Scan" ────────────────────
        // Kiểm tra: nếu assignedQcId == mình → re-start OK
        if (task.getAssignedQcId() != null && !task.getAssignedQcId().equals(userId)) {
            throw new BusinessException(
                    "Pick List #" + taskId + " đang được QC khác kiểm định. Vui lòng liên hệ quản lý.");
        }

        if (task.getAssignedQcId() == null) {
            int claimed = pickingTaskRepository.claimQcAssignment(taskId, userId);
            if (claimed == 0) {
                // Race: QC khác claim cùng lúc
                PickingTaskEntity fresh = findPickingTask(taskId);
                if (fresh.getAssignedQcId() != null && !fresh.getAssignedQcId().equals(userId)) {
                    throw new BusinessException(
                            "Pick List #" + taskId + " vừa được QC khác nhận. Vui lòng thử lại.");
                }
            } else {
                log.info("[QcClaim-OB] QC userId={} claimed taskId={}", userId, taskId);
                try {
                    notificationService.notifyRoles(new String[]{"QC", "MANAGER"},
                            "outbound_qc_claimed", taskId, "Task #" + taskId,
                            "QC userId=" + userId + " bắt đầu kiểm định outbound");
                } catch (Exception ignored) {}
            }
        }

        if (!"QC_IN_PROGRESS".equals(task.getStatus())) {
            task.setStatus("QC_IN_PROGRESS");
            pickingTaskRepository.save(task);
        }

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
            item.setQcFailQty(safeBD(item.getQcFailQty()).add(BigDecimal.ONE));
            item.setQcNote(request.getReason());
            // [MULTI-PHOTO] Merge JSON array — không ghi đè, cộng dồn ảnh
            if (request.getAttachmentUrl() != null && !request.getAttachmentUrl().isBlank()) {
                item.setQcAttachmentUrl(mergePhotoUrls(item.getQcAttachmentUrl(), request.getAttachmentUrl()));
            }
        } else {
            item.setQcPassQty(safeBD(item.getQcPassQty()).add(BigDecimal.ONE));
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
        int total = items.size();
        // Đếm số lượng unit thực tế theo qcPassQty/qcFailQty — không đếm số row
        int pass = items.stream()
                .mapToInt(i -> safeInt(i.getQcPassQty()))
                .sum();
        int fail = items.stream()
                .mapToInt(i -> safeInt(i.getQcFailQty()))
                .sum();
        // pending = items chưa scan đủ (passQty + failQty < requiredQty)
        int pending = (int) items.stream()
                .filter(i -> safeBD(i.getQcPassQty()).add(safeBD(i.getQcFailQty()))
                        .compareTo(i.getRequiredQty()) < 0)
                .count();
        return ApiResponse.success("QC summary retrieved", QcSummaryResponse.builder()
                .pickingTaskId(taskId).totalItems(total).passCount(pass)
                .failCount(fail).holdCount(0).pendingCount(pending)
                .allScanned(pending == 0 && total > 0).build());
    }

    private int safeInt(java.math.BigDecimal v) {
        return v != null ? v.intValue() : 0;
    }
    private java.math.BigDecimal safeBD(java.math.BigDecimal v) {
        return v != null ? v : java.math.BigDecimal.ZERO;
    }

    /**
     * [MULTI-PHOTO] Merge danh sách ảnh: existing (JSON array hoặc URL đơn) + incoming (JSON array hoặc URL đơn).
     * Kết quả luôn là JSON array string: ["url1","url2",...] (tối đa 5 ảnh).
     * Tương thích ngược: nếu existing là URL đơn (row cũ) thì wrap thành ["url"].
     */
    private String mergePhotoUrls(String existing, String incoming) {
        java.util.List<String> urls = new java.util.ArrayList<>();
        // Parse existing
        if (existing != null && !existing.isBlank()) {
            if (existing.trim().startsWith("[")) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                    java.util.List<String> parsed = om.readValue(existing, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>(){});
                    urls.addAll(parsed);
                } catch (Exception e) { urls.add(existing); } // fallback: treat as plain URL
            } else {
                urls.add(existing);
            }
        }
        // Parse incoming
        if (incoming != null && !incoming.isBlank()) {
            if (incoming.trim().startsWith("[")) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                    java.util.List<String> parsed = om.readValue(incoming, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>(){});
                    urls.addAll(parsed);
                } catch (Exception e) { urls.add(incoming); }
            } else {
                urls.add(incoming);
            }
        }
        // Giới hạn 5 ảnh, loại trùng
        java.util.List<String> deduped = urls.stream().distinct().limit(5).collect(java.util.stream.Collectors.toList());
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(deduped);
        } catch (Exception e) {
            return deduped.isEmpty() ? null : deduped.get(0);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // submitQcResults: nhan toan bo ket qua PASS/FAIL tu FE, set cho tung item, sau do finalize
    @Transactional
    public ApiResponse<QcSummaryResponse> submitQcResults(
            Long taskId,
            java.util.List<QcScanRequest> results,
            Long userId) {
        PickingTaskEntity task = findPickingTask(taskId);
        if ("PICKED".equals(task.getStatus())) startQcSession(taskId, userId);
        for (QcScanRequest req : results) {
            try { scanItem(req, userId); }
            catch (Exception e) { log.warn("submitQcResults skip item {}: {}", req.getPickingTaskItemId(), e.getMessage()); }
        }
        return finalizeQc(taskId, userId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4) FINALIZE QC — [V20] tạo Incident DAMAGE khi có FAIL, set ON_HOLD
    //
    // [FIX DUPLICATE] Idempotent guard:
    //   - Task đã COMPLETED/CANCELLED → trả về summary, không làm gì thêm
    //   - SO đã ON_HOLD + incident DAMAGE OPEN tồn tại → trả về summary, không tạo thêm
    //   - SO đã QC_PASSED → trả về summary, không notify lại
    //
    // Root cause của duplicate: điện thoại bấm "Xác nhận" → gọi finalize-qc (lần 1),
    // rồi web modal polling QcScanPanel phát hiện allScanned → QcFinalizeOrDispatch
    // mount → gọi finalizeQc lại (lần 2) → 2 incident + 2 notification Manager.
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public ApiResponse<QcSummaryResponse> finalizeQc(Long taskId, Long userId) {
        PickingTaskEntity task = findPickingTask(taskId);

        // [FIX DUPLICATE] Guard 1: task đã kết thúc → trả về summary hiện tại, không làm gì
        if ("COMPLETED".equals(task.getStatus()) || "CANCELLED".equals(task.getStatus())) {
            log.info("finalizeQc taskId={} already {} — returning current summary (idempotent)", taskId, task.getStatus());
            return buildCurrentSummary(taskId);
        }

        if ("PICKED".equals(task.getStatus())) {
            startQcSession(taskId, userId);
            task = findPickingTask(taskId);
        }

        if (!"QC_IN_PROGRESS".equals(task.getStatus())) {
            // [FIX DUPLICATE] Guard 2: task không còn ở QC_IN_PROGRESS
            // (đã bị finalize bởi lần gọi trước) → trả về summary, không throw
            log.info("finalizeQc taskId={} status={} — not QC_IN_PROGRESS, returning summary (idempotent)",
                    taskId, task.getStatus());
            return buildCurrentSummary(taskId);
        }

        // Auto-PASS items chưa scan đủ: cộng phần còn thiếu vào qcPassQty
        LocalDateTime now = LocalDateTime.now();
        for (PickingTaskItemEntity item : pickingTaskItemRepository.findByPickingTaskId(taskId)) {
            java.math.BigDecimal scanned = safeBD(item.getQcPassQty()).add(safeBD(item.getQcFailQty()));
            java.math.BigDecimal remaining = item.getRequiredQty().subtract(scanned);
            if (remaining.compareTo(java.math.BigDecimal.ZERO) > 0) {
                item.setQcPassQty(safeBD(item.getQcPassQty()).add(remaining));
                item.setQcResult(safeBD(item.getQcFailQty()).compareTo(java.math.BigDecimal.ZERO) > 0 ? "FAIL" : "PASS");
                item.setQcScannedAt(now);
                item.setQcNote(item.getQcNote() != null ? item.getQcNote() : "Auto-PASS by finalize-qc");
                pickingTaskItemRepository.save(item);
            }
        }

        List<PickingTaskItemEntity> allItems = pickingTaskItemRepository.findByPickingTaskId(taskId);
        int total = allItems.size();
        // Đếm theo số lượng unit thực tế — FIX BUG: 1 PASS + 1 FAIL không bị đếm thành 0 PASS + 1 FAIL
        int pass = allItems.stream().mapToInt(i -> safeInt(i.getQcPassQty())).sum();
        int fail = allItems.stream().mapToInt(i -> safeInt(i.getQcFailQty())).sum();
        int hold = 0;
        int pending = (int) allItems.stream()
                .filter(i -> safeBD(i.getQcPassQty()).add(safeBD(i.getQcFailQty()))
                        .compareTo(i.getRequiredQty()) < 0)
                .count();

        Long soId = task.getSoId();
        // [FIX COMPILE] task bị reassign ở trên nên không effectively final.
        // Capture snapshot effectively-final để dùng được bên trong lambda.
        final PickingTaskEntity finalTask = task;
        final List<PickingTaskItemEntity> finalAllItems = allItems;

        if ((fail > 0 || hold > 0) && soId != null) {
            salesOrderRepository.findById(soId).ifPresent(so -> {
                // [FIX DUPLICATE] Guard 3: Chỉ tạo incident và notify nếu SO chưa ON_HOLD
                // hoặc chưa có incident DAMAGE OPEN nào cho soId này.
                // Điều này ngăn lần gọi thứ 2 tạo thêm incident + notification.
                boolean soAlreadyOnHold = "ON_HOLD".equals(so.getStatus());
                boolean incidentAlreadyExists = !incidentRepository.findOpenIncidentsBySoId(soId)
                        .stream()
                        .anyMatch(inc -> inc.getIncidentType() == IncidentType.DAMAGE);

                if (!soAlreadyOnHold || !incidentAlreadyExists) {
                    // Chỉ tạo incident nếu chưa có
                    if (incidentAlreadyExists) {
                        // incidentAlreadyExists = true nghĩa là KHÔNG có → cần tạo
                        createDamageIncident(finalTask, finalAllItems, soId, userId);
                    }
                    so.setStatus("ON_HOLD");
                    so.setUpdatedAt(now);
                    salesOrderRepository.save(so);
                    log.info("SO {} → ON_HOLD (QC fail={}, hold={})", so.getSoCode(), fail, hold);
                    // ── Realtime: notify MANAGER có đơn lỗi QC cần xử lý ─────────
                    String customerName = customerRepository.findById(so.getCustomerId())
                            .map(c -> c.getCustomerName()).orElse("—");
                    notificationService.notifyRoles(new String[]{"MANAGER", "QC", "KEEPER"}, "incident_open",
                            soId, so.getSoCode(),
                            customerName + " — QC lỗi (" + fail + " fail, " + hold + " hold)");
                } else {
                    log.info("finalizeQc taskId={}: SO {} đã ON_HOLD + incident DAMAGE đã tồn tại — bỏ qua tạo incident/notify (idempotent)",
                            taskId, so.getSoCode());
                }
            });
        } else if (soId != null) {
            salesOrderRepository.findById(soId).ifPresent(so -> {
                // [FIX DUPLICATE] Guard 4: Chỉ set QC_PASSED và notify nếu SO chưa ở trạng thái đó
                if ("QC_SCAN".equals(so.getStatus()) || "PICKING".equals(so.getStatus())) {
                    so.setStatus("QC_PASSED");
                    so.setUpdatedAt(now);
                    salesOrderRepository.save(so);
                    log.info("SO {} → QC_PASSED (all items pass)", so.getSoCode());
                    // ── Realtime: notify KEEPER đơn QC đạt, sẵn sàng dispatch ─
                    String customerName = customerRepository.findById(so.getCustomerId())
                            .map(c -> c.getCustomerName()).orElse("—");
                    notificationService.notifyRoles(new String[]{"MANAGER", "QC", "KEEPER"}, "qc_outbound_passed",
                            soId, so.getSoCode(),
                            customerName + " — QC đạt, sẵn sàng xuất kho");
                } else {
                    log.info("finalizeQc taskId={}: SO {} đã ở {} — bỏ qua set QC_PASSED/notify (idempotent)",
                            taskId, so.getSoCode(), so.getStatus());
                }
            });
        }

        // Release QC claim — task đã finalize, QC khác có thể xem lại nếu cần
        try { pickingTaskRepository.releaseQcAssignment(taskId, userId); } catch (Exception ignored) {}
        try {
            notificationService.notifyRoles(new String[]{"QC", "MANAGER", "KEEPER"},
                    "outbound_qc_released", taskId, "Task #" + taskId,
                    "QC hoàn thành kiểm định outbound");
        } catch (Exception ignored) {}

        log.info("QC finalized taskId={}: pass={}, fail={}, hold={}", taskId, pass, fail, hold);
        return ApiResponse.success("QC finalized.", QcSummaryResponse.builder()
                .pickingTaskId(taskId).totalItems(total).passCount(pass)
                .failCount(fail).holdCount(hold).pendingCount(pending)
                .allScanned(pending == 0 && total > 0).build());
    }

    /** Build current summary from DB — dùng cho idempotent early-return. */
    private ApiResponse<QcSummaryResponse> buildCurrentSummary(Long taskId) {
        List<PickingTaskItemEntity> items = pickingTaskItemRepository.findByPickingTaskId(taskId);
        int total   = items.size();
        int pass    = items.stream().mapToInt(i -> safeInt(i.getQcPassQty())).sum();
        int fail    = items.stream().mapToInt(i -> safeInt(i.getQcFailQty())).sum();
        int pending = (int) items.stream()
                .filter(i -> safeBD(i.getQcPassQty()).add(safeBD(i.getQcFailQty()))
                        .compareTo(i.getRequiredQty()) < 0)
                .count();
        return ApiResponse.success("QC already finalized.", QcSummaryResponse.builder()
                .pickingTaskId(taskId).totalItems(total).passCount(pass)
                .failCount(fail).holdCount(0).pendingCount(pending)
                .allScanned(pending == 0 && total > 0).build());
    }

    /**
     * Tạo Incident DAMAGE cho các FAIL items trong task.
     *
     * [FIX ROOT CAUSE] Bug cũ: 1 SKU có thể có nhiều PickingTaskItem (mỗi item = 1 reservation line).
     * Ví dụ: SO yêu cầu 2 units SKU001 → AllocateStock tạo 2 reservation lines (1 unit/line)
     * → 2 PickingTaskItem: item1(req=1,fail=1,pass=0), item2(req=1,fail=0,pass=1)
     * → Bug cũ tạo IncidentItem với expectedQty=1 (chỉ item có fail)
     * → FE hiển thị "SL Giấy tờ: 1" thay vì đúng là "2"
     *
     * Fix: Group theo skuId, cộng dồn toàn bộ qty → 1 IncidentItem/SKU với số liệu chính xác
     */
    private void createDamageIncident(PickingTaskEntity task,
                                      List<PickingTaskItemEntity> allItems,
                                      Long soId, Long reportedBy) {
        SalesOrderEntity so = salesOrderRepository.findById(soId).orElse(null);
        if (so == null) return;

        // [FIX] Group tất cả picking items theo skuId để cộng dồn qty đúng
        java.util.Map<Long, java.util.List<PickingTaskItemEntity>> bySkuId = allItems.stream()
                .collect(Collectors.groupingBy(PickingTaskItemEntity::getSkuId));

        // Chỉ tạo incident nếu có ít nhất 1 SKU có failQty > 0
        boolean hasAnyFail = bySkuId.values().stream().anyMatch(skuItems ->
                skuItems.stream().anyMatch(i -> safeBD(i.getQcFailQty()).compareTo(BigDecimal.ZERO) > 0));
        if (!hasAnyFail) return;

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

        for (java.util.Map.Entry<Long, java.util.List<PickingTaskItemEntity>> entry : bySkuId.entrySet()) {
            Long skuId = entry.getKey();
            java.util.List<PickingTaskItemEntity> skuItems = entry.getValue();

            // [FIX] Cộng dồn toàn bộ qty của cùng SKU qua tất cả reservation lines
            BigDecimal totalRequired = skuItems.stream()
                    .map(i -> safeBD(i.getRequiredQty()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalFailQty = skuItems.stream()
                    .map(i -> safeBD(i.getQcFailQty()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalPassQty = skuItems.stream()
                    .map(i -> safeBD(i.getQcPassQty()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Bỏ qua SKU hoàn toàn pass
            if (totalFailQty.compareTo(BigDecimal.ZERO) <= 0) continue;

            SkuEntity sku = skuRepository.findById(skuId).orElse(null);
            String skuCode = sku != null ? sku.getSkuCode() : "SKU#" + skuId;

            // Đại diện: item có failQty > 0 đầu tiên (để lấy ảnh, note, location)
            PickingTaskItemEntity rep = skuItems.stream()
                    .filter(i -> safeBD(i.getQcFailQty()).compareTo(BigDecimal.ZERO) > 0)
                    .findFirst()
                    .orElse(skuItems.get(0));
            String fromLocCode = locationRepository.findById(rep.getFromLocationId())
                    .map(l -> l.getLocationCode()).orElse("N/A");

            desc.append(skuCode)
                    .append("[FAIL x").append(totalFailQty.intValue())
                    .append("/PASS x").append(totalPassQty.intValue()).append("] ");

            String noteStr = "FAIL x" + totalFailQty.intValue() + " / PASS x" + totalPassQty.intValue()
                    + (rep.getQcNote() != null ? " | " + rep.getQcNote() : "")
                    + " | from_bin: " + fromLocCode
                    + (rep.getQcAttachmentUrl() != null ? " | photo: " + rep.getQcAttachmentUrl() : "");

            incidentItemRepository.save(IncidentItemEntity.builder()
                    .incident(saved)
                    .skuId(skuId)
                    // [FIX] expectedQty = TỔNG requiredQty (SL Giấy tờ đúng = 2, không phải 1)
                    .expectedQty(totalRequired)
                    // [FIX] actualQty = TỔNG pass + fail = tổng đã QC thực tế
                    .actualQty(totalPassQty.add(totalFailQty))
                    // damagedQty = TỔNG failQty
                    .damagedQty(totalFailQty)
                    .reasonCode("DAMAGE")
                    .note(noteStr)
                    .attachmentUrl(rep.getQcAttachmentUrl())
                    .build());
        }

        saved.setDescription(desc.toString().trim());
        incidentRepository.save(saved);
        log.info("Created DAMAGE Incident {} for SO {} (grouped by SKU)", code, so.getSoCode());
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
                // ─── FLOW ĐÚNG sau confirmPicked ──────────────────────────────────────────
                // Sau confirmPicked: tồn kho đã bị trừ hết tại bin gốc (quantity=0).
                // RETURN_SCRAP cần:
                //   1. Hàng FAIL (qcFailQty) → cộng vào Z-DEFECT (hàng lỗi)
                //   2. Hàng PASS (qcPassQty) → cộng lại vào bin gốc (trả về kho để re-pick)
                //   3. Giảm orderedQty của SO xuống còn passQty (bỏ phần lỗi)
                //   4. SO → PICKING lại (task mới) — không cần qua APPROVED→Allocate vì hàng đã sẵn sàng
                // ─────────────────────────────────────────────────────────────────────────
                returnFailToDefectAndRestorePass(incident.getSoId(), managerId, so.getWarehouseId());
                // Reset QC kết quả để task mới có thể scan lại
                resetQcForRepick(incident.getSoId());
                // Hủy picking task cũ (đã PICKED/QC_IN_PROGRESS) để tạo task mới
                cancelOldPickingTask(incident.getSoId(), so.getWarehouseId());
                // SO → ALLOCATED: hàng PASS đã về bin gốc, Keeper chỉ cần tạo Pick List mới
                so.setStatus("ALLOCATED");
                so.setUpdatedAt(LocalDateTime.now());
                salesOrderRepository.save(so);
                log.info("SO {} → ALLOCATED (RETURN_SCRAP: fail→defect, pass→bin gốc)", so.getSoCode());
                notificationService.notifyRoles(new String[]{"MANAGER", "QC", "KEEPER"}, "outbound_approved",
                        so.getSoId(), so.getSoCode(), "Hàng lỗi về Z-DEFECT, hàng tốt trả bin — Keeper tạo Pick List mới");
            }
            case "ACCEPT" -> {
                // Xuất luôn hàng lỗi → QC_PASSED để cho phép dispatch
                so.setStatus("QC_PASSED");
                so.setUpdatedAt(LocalDateTime.now());
                salesOrderRepository.save(so);
                log.info("SO {} → QC_PASSED (DAMAGE ACCEPT)", so.getSoCode());
                // ── Realtime: notify KEEPER QC đạt, sẵn sàng dispatch ────────
                notificationService.notifyRoles(new String[]{"MANAGER", "QC", "KEEPER"}, "qc_outbound_passed",
                        so.getSoId(), so.getSoCode(), "Chấp nhận hàng lỗi — sẵn sàng xuất kho");
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

    /**
     * RETURN_SCRAP: Sau confirmPicked, bin gốc đã trống (quantity=0).
     *
     * Flow:
     *   1. Hàng FAIL (qcFailQty) → upsert vào Z-DEFECT
     *   2. Hàng PASS (qcPassQty) → upsert trả lại bin gốc + tạo lại Reservation OPEN
     *      (bắt buộc — Keeper gen Pick List cần reservation OPEN mới pick được)
     *   3. Giảm orderedQty của SO item xuống còn passQty
     *   4. Ghi inventory_transaction cho cả 2 chiều
     *
     * [FIX ROOT CAUSE] Bug cũ: chỉ upsert tồn kho nhưng không tạo Reservation OPEN mới
     * → PickListService.generatePickList() query reservation OPEN → empty → throw PICKLIST_NO_ALLOCATION
     */
    private void returnFailToDefectAndRestorePass(Long soId, Long userId, Long warehouseId) {
        List<PickingTaskItemEntity> allItems = pickingTaskItemRepository.findAllActiveItemsBySoId(soId);
        if (allItems.isEmpty()) {
            log.warn("RETURN_SCRAP: soId={} — no active picking task items found", soId);
            return;
        }

        LocationEntity defectBin = getOrCreateDefectBin(warehouseId);
        Long actorId = userId != null ? userId : getSystemUserId();

        // Huỷ toàn bộ reservation cũ (nếu còn sót) trước khi tạo lại
        reservationRepository.findByReferenceTableAndReferenceIdAndStatus("sales_orders", soId, "OPEN")
                .forEach(r -> {
                    if (r.getLocationId() != null) {
                        inventorySnapshotRepository.incrementReservedByLocationAndSku(
                                r.getLocationId(), r.getSkuId(), r.getLotId(), r.getQuantity().negate());
                    }
                    r.setStatus("CANCELLED");
                    reservationRepository.save(r);
                });

        for (PickingTaskItemEntity item : allItems) {
            Long fromLocationId = item.getFromLocationId();
            if (fromLocationId == null) continue;

            BigDecimal failQty = safeBD(item.getQcFailQty());
            BigDecimal passQty = safeBD(item.getQcPassQty());

            // ── 1. Hàng FAIL → cộng vào Z-DEFECT ─────────────────────────────────
            // (không trừ bin gốc — đã trừ lúc confirmPicked)
            if (failQty.compareTo(BigDecimal.ZERO) > 0) {
                inventorySnapshotRepository.upsertInventory(
                        warehouseId, item.getSkuId(), item.getLotId(),
                        defectBin.getLocationId(), failQty);
                inventoryTransactionRepository.save(InventoryTransactionEntity.builder()
                        .warehouseId(warehouseId).locationId(defectBin.getLocationId())
                        .skuId(item.getSkuId()).lotId(item.getLotId()).quantity(failQty)
                        .txnType("DAMAGE_TRANSFER").referenceTable("sales_orders").referenceId(soId)
                        .reasonCode("QC_FAIL_TO_DEFECT").createdBy(actorId)
                        .build());
                log.info("RETURN_SCRAP: skuId={} failQty={} → defect bin={}",
                        item.getSkuId(), failQty, defectBin.getLocationId());
            }

            // ── 2. Hàng PASS → cộng lại vào bin gốc + tạo Reservation OPEN mới ──
            if (passQty.compareTo(BigDecimal.ZERO) > 0) {
                // 2a. Cộng tồn kho về bin gốc
                inventorySnapshotRepository.upsertInventory(
                        warehouseId, item.getSkuId(), item.getLotId(), fromLocationId, passQty);

                // 2b. [FIX] Lock reserved_qty trên snapshot ngay
                inventorySnapshotRepository.incrementReservedByLocationAndSku(
                        fromLocationId, item.getSkuId(), item.getLotId(), passQty);

                // 2c. [FIX] Tạo Reservation OPEN mới → PickListService mới gen được Pick List
                reservationJpaRepository.save(ReservationEntity.builder()
                        .warehouseId(warehouseId)
                        .skuId(item.getSkuId())
                        .lotId(item.getLotId())
                        .locationId(fromLocationId)
                        .quantity(passQty)
                        .referenceTable("sales_orders")
                        .referenceId(soId)
                        .status("OPEN")
                        .build());

                inventoryTransactionRepository.save(InventoryTransactionEntity.builder()
                        .warehouseId(warehouseId).locationId(fromLocationId)
                        .skuId(item.getSkuId()).lotId(item.getLotId()).quantity(passQty)
                        .txnType("RETURN_TO_BIN").referenceTable("sales_orders").referenceId(soId)
                        .reasonCode("QC_PASS_RESTORE").createdBy(actorId)
                        .build());
                log.info("RETURN_SCRAP: skuId={} passQty={} → restored to bin={} + reservation OPEN created",
                        item.getSkuId(), passQty, fromLocationId);
            }

            // ── 3. Giảm orderedQty về passQty (loại bỏ phần lỗi khỏi đơn) ─────────
            salesOrderItemRepository.findBySoId(soId).stream()
                    .filter(si -> si.getSkuId().equals(item.getSkuId()))
                    .findFirst()
                    .ifPresent(si -> {
                        BigDecimal newQty = passQty.max(BigDecimal.ZERO);
                        if (newQty.compareTo(si.getOrderedQty()) < 0) {
                            log.info("RETURN_SCRAP: reduce orderedQty sku={} {} → {}",
                                    item.getSkuId(), si.getOrderedQty(), newQty);
                            si.setOrderedQty(newQty);
                            salesOrderItemRepository.save(si);
                        }
                    });
        }
    }

    /** Hủy picking task cũ (đã PICKED/QC_IN_PROGRESS) để Keeper tạo task mới. */
    private void cancelOldPickingTask(Long soId, Long warehouseId) {
        pickingTaskRepository.findByWarehouseIdAndSoId(warehouseId, soId).stream()
                .filter(t -> !"CANCELLED".equals(t.getStatus()) && !"COMPLETED".equals(t.getStatus()))
                .forEach(t -> {
                    t.setStatus("CANCELLED");
                    pickingTaskRepository.save(t);
                    log.info("RETURN_SCRAP: cancelled old picking task #{}", t.getPickingTaskId());
                });
    }

    /**
     * Tìm khu hàng lỗi (defect bin) của warehouse.
     * Nếu chưa có → tự tạo location DEFECT-BIN với is_defect=true.
     */
    private LocationEntity getOrCreateDefectBin(Long warehouseId) {
        // 1. Tim bin is_defect=true co zone (trong Z-DEFECT chinh thuc) truoc
        Optional<LocationEntity> existing = locationRepository.findDefectBinByWarehouse(warehouseId);
        if (existing.isPresent() && existing.get().getZoneId() != null) return existing.get();

        // 2. Tim zone defect theo tên — hỗ trợ nhiều variant
        List<String> defectZoneCandidates = List.of("Z-DEFECT", "DEFEQ", "Z-DEFEQ", "DEFECT", "Z-DAMAGE", "DAMAGE");
        org.example.sep26management.infrastructure.persistence.entity.ZoneEntity foundDefectZone = null;
        for (String candidate : defectZoneCandidates) {
            Optional<org.example.sep26management.infrastructure.persistence.entity.ZoneEntity> z =
                    zoneRepository.findByWarehouseIdAndZoneCode(warehouseId, candidate);
            if (z.isPresent()) {
                foundDefectZone = z.get();
                log.info("getOrCreateDefectBin: found defect zone '{}' (id={})", candidate, z.get().getZoneId());
                break;
            }
        }

        if (foundDefectZone != null) {
            Long zoneId = foundDefectZone.getZoneId();
            List<LocationEntity> binsInZone = locationRepository.findByZoneId(zoneId);

            // 2a. Bin is_defect=true trong Z-DEFECT
            Optional<LocationEntity> defectBinInZone = binsInZone.stream()
                    .filter(l -> Boolean.TRUE.equals(l.getIsDefect()) && Boolean.TRUE.equals(l.getActive()))
                    .findFirst();
            if (defectBinInZone.isPresent()) return defectBinInZone.get();

            // 2b. Lay bin dau tien trong zone defect, danh dau is_defect=true
            List<LocationEntity> activeBins = binsInZone.stream()
                    .filter(l -> Boolean.TRUE.equals(l.getActive())
                            && org.example.sep26management.application.enums.LocationType.BIN.equals(l.getLocationType()))
                    .collect(java.util.stream.Collectors.toList());

            if (!activeBins.isEmpty()) {
                for (LocationEntity bin : activeBins) {
                    if (!Boolean.TRUE.equals(bin.getIsDefect())) {
                        bin.setIsDefect(true);
                        locationRepository.save(bin);
                    }
                }
                return activeBins.get(0);
            }
        }

        // 3. Fallback: dung bin is_defect=true hien co (du no-zone)
        if (existing.isPresent()) {
            log.warn("No defect zone bins, using existing defect bin {}", existing.get().getLocationCode());
            return existing.get();
        }

        // 4. Fallback cuoi: tao bin moi no-zone
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
        log.warn("No defect zone — auto-created no-zone defect bin {} (warehouseId={})", code, warehouseId);
        return locationRepository.save(defect);
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

    /** Reset toàn bộ QC state của task items để task mới có thể scan sạch. */
    private void resetQcForRepick(Long soId) {
        List<PickingTaskItemEntity> items = pickingTaskItemRepository.findAllActiveItemsBySoId(soId);
        for (PickingTaskItemEntity item : items) {
            item.setQcResult(null);
            item.setQcScannedAt(null);
            item.setQcPassQty(BigDecimal.ZERO);
            item.setQcFailQty(BigDecimal.ZERO);
            item.setQcNote("[Reset after RETURN_SCRAP — re-pick required]");
            item.setPickedQty(BigDecimal.ZERO);
            pickingTaskItemRepository.save(item);
        }
        log.info("resetQcForRepick: reset {} items for soId={}", items.size(), soId);
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
                so.setStatus("WAITING_STOCK");
                so.setUpdatedAt(LocalDateTime.now());
                salesOrderRepository.save(so);
                log.info("SO {} → WAITING_STOCK (chờ hàng bù)", so.getSoCode());
                notificationService.notifyRoles(new String[]{"MANAGER", "QC", "KEEPER"}, "outbound_approved",
                        so.getSoId(), so.getSoCode(), "Chờ nhập bù hàng — tạm giữ đơn");
            }
            case "CLOSE_SHORT" -> {
                adjustOrderedQtyToAvailable(so);
                so.setStatus("APPROVED");
                so.setUpdatedAt(LocalDateTime.now());
                salesOrderRepository.save(so);
                log.info("SO {} → APPROVED (CLOSE_SHORT, re-Allocate ready)", so.getSoCode());
                notificationService.notifyRoles(new String[]{"MANAGER", "QC", "KEEPER"}, "outbound_approved",
                        so.getSoId(), so.getSoCode(), "Đã cắt số lượng thiếu — cần phân bổ lại");
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
        // [BUG FIX] Chấp nhận cả QC_PASSED (all pass) lẫn QC_SCAN (ACCEPT damage)
        if (!"QC_PASSED".equals(so.getStatus()) && !"QC_SCAN".equals(so.getStatus()))
            throw new BusinessException("Dispatch requires QC_PASSED or QC_SCAN status. Current: " + so.getStatus());
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

        String customerName = customerRepository.findById(so.getCustomerId())
                .map(c -> c.getCustomerName()).orElse("—");
        notificationService.notifyRoles(new String[]{"MANAGER", "QC", "KEEPER"}, "outbound_dispatched",
                so.getSoId(), so.getSoCode(), customerName + " — Đã xuất kho");

        return ApiResponse.success("Order dispatched. Status: DISPATCHED", null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9) COMPLETE OUTBOUND
    // Bước cuối cùng: sau khi DISPATCHED, Keeper đã in phiếu và upload đủ
    // 2 ảnh chữ ký (phiếu lấy hàng + phiếu xuất kho) → Hoàn thành xuất kho
    // DISPATCHED → COMPLETED
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public ApiResponse<Void> completeOutbound(Long soId, Long userId) {
        SalesOrderEntity so = findSalesOrder(soId);

        if (!"DISPATCHED".equals(so.getStatus()))
            throw new BusinessException(
                    "Chỉ có thể hoàn thành đơn đang ở trạng thái DISPATCHED. Hiện tại: " + so.getStatus());

        // Kiểm tra ảnh phiếu lấy hàng đã ký (pick_signed_note_url)
        boolean hasPickNote = so.getPickSignedNoteUrl() != null && !so.getPickSignedNoteUrl().isBlank();
        // Kiểm tra ảnh phiếu xuất kho đã ký (signed_note_url)
        boolean hasDispatchNote = so.getSignedNoteUrl() != null && !so.getSignedNoteUrl().isBlank();

        if (!hasPickNote && !hasDispatchNote) {
            throw new BusinessException(
                    "Chưa có ảnh chữ ký. Cần upload đủ 2 phiếu: Phiếu lấy hàng và Phiếu xuất kho.");
        }
        if (!hasPickNote) {
            throw new BusinessException(
                    "Còn thiếu ảnh Phiếu lấy hàng đã ký. Keeper scan QR 'Phiếu lấy hàng' để chụp ảnh.");
        }
        if (!hasDispatchNote) {
            throw new BusinessException(
                    "Còn thiếu ảnh Phiếu xuất kho đã ký. Scan QR 'Phiếu xuất kho' để chụp và upload.");
        }

        so.setStatus("COMPLETED");
        so.setUpdatedAt(LocalDateTime.now());
        salesOrderRepository.save(so);
        log.info("SO {} → COMPLETED (both signed notes uploaded)", so.getSoCode());

        String customerName = customerRepository.findById(so.getCustomerId())
                .map(c -> c.getCustomerName()).orElse("—");
        notificationService.notifyRoles(new String[]{"MANAGER", "QC", "KEEPER"}, "outbound_completed",
                so.getSoId(), so.getSoCode(), customerName + " — Xuất kho hoàn tất");

        return ApiResponse.success("Xuất kho hoàn tất. Đơn hàng đã COMPLETED.", null);
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

    /** Lay user_id he thong (admin dau tien) de dung lam created_by khi userId=null. */
    private Long getSystemUserId() {
        return userRepository.findAll().stream()
                .filter(u -> org.example.sep26management.domain.enums.UserStatus.ACTIVE.equals(u.getStatus()))
                .map(u -> u.getUserId())
                .min(Long::compareTo)
                .orElse(1L);
    }

}