package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.request.PutawayAllocateRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.PageResponse;
import org.example.sep26management.application.dto.response.PutawayAllocationResponse;
import org.example.sep26management.application.dto.response.PutawaySuggestion;
import org.example.sep26management.application.dto.response.PutawayTaskResponse;
import org.example.sep26management.application.event.PutawayEventPublisher;
import org.example.sep26management.application.event.PutawayTaskEvent;
import org.example.sep26management.infrastructure.persistence.entity.LocationEntity;
import org.example.sep26management.infrastructure.persistence.entity.PutawayAllocationEntity;
import org.example.sep26management.infrastructure.persistence.entity.PutawayTaskEntity;
import org.example.sep26management.infrastructure.persistence.entity.PutawayTaskItemEntity;
import org.example.sep26management.infrastructure.persistence.repository.InventorySnapshotJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.LocationJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.PutawayAllocationJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.PutawayTaskItemJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.PutawayTaskJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.SkuJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.ZoneJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.GrnJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.ReceivingOrderJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.ReceivingItemJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.example.sep26management.infrastructure.persistence.repository.InventoryLotJpaRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class PutawayTaskService {

    private final PutawayTaskJpaRepository putawayTaskRepo;
    private final PutawayTaskItemJpaRepository putawayTaskItemRepo;
    private final JdbcTemplate jdbcTemplate;
    private final LocationJpaRepository locationRepo;
    private final ZoneJpaRepository zoneRepo;
    private final InventorySnapshotJpaRepository inventorySnapshotRepo;
    private final PutawaySuggestionService putawaySuggestionService;
    private final SkuJpaRepository skuRepo;
    private final PutawayAllocationJpaRepository allocationRepo;
    private final GrnJpaRepository grnRepo;
    private final NotificationService notificationService;
    private final ReceivingOrderJpaRepository receivingOrderRepo;
    private final PutawayEventPublisher putawayEventPublisher;
    private final InventoryLotJpaRepository inventoryLotRepo;
    private final ReceivingItemJpaRepository receivingItemRepo;

    // ─── List tasks ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<PageResponse<PutawayTaskResponse>> listTasks(Long warehouseId, Long assignedTo, String status, int page,
                                                                    int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PutawayTaskEntity> tasksPage;
        if (assignedTo != null && status != null) {
            tasksPage = putawayTaskRepo.findByAssignedToAndStatusOrderByCreatedAtDesc(assignedTo, status, pageable);
        } else if (assignedTo != null && warehouseId != null) {
            // BUG FIX: when status is null but assignedTo is provided
            tasksPage = putawayTaskRepo.findByAssignedToAndWarehouseIdOrderByCreatedAtDesc(assignedTo, warehouseId, pageable);
        } else if (assignedTo != null) {
            tasksPage = putawayTaskRepo.findByAssignedToOrderByCreatedAtDesc(assignedTo, pageable);
        } else if (status != null && warehouseId != null) {
            tasksPage = putawayTaskRepo.findByWarehouseIdAndStatusOrderByCreatedAtDesc(warehouseId, status, pageable);
        } else if (warehouseId != null) {
            tasksPage = putawayTaskRepo.findByWarehouseIdOrderByCreatedAtDesc(warehouseId, pageable);
        } else {
            tasksPage = putawayTaskRepo.findAllByOrderByCreatedAtDesc(pageable);
        }

        List<PutawayTaskResponse> content = tasksPage.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        PageResponse<PutawayTaskResponse> pageResponse = PageResponse.<PutawayTaskResponse>builder()
                .content(content)
                .page(tasksPage.getNumber())
                .size(tasksPage.getSize())
                .totalElements(tasksPage.getTotalElements())
                .totalPages(tasksPage.getTotalPages())
                .last(tasksPage.isLast())
                .build();

        return ApiResponse.success("OK", pageResponse);
    }

    // ─── Get task detail ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<PutawayTaskResponse> getTask(Long taskId) {
        PutawayTaskEntity task = findTask(taskId);
        List<PutawayTaskItemEntity> rawItems = putawayTaskItemRepo.findByPutawayTaskPutawayTaskId(taskId);
        List<PutawayTaskItemEntity> groupedItems = groupItems(rawItems);
        PutawayTaskResponse response = toResponse(task, groupedItems.size());
        response.setItems(groupedItems.stream().map(this::toItemDtoEnriched).collect(Collectors.toList()));
        return ApiResponse.success("OK", response);
    }

    @Transactional(readOnly = true)
    public ApiResponse<PutawayTaskResponse> getTaskByGrnId(Long grnId) {
        PutawayTaskEntity task = putawayTaskRepo.findByGrnId(grnId)
                .orElseThrow(() -> new org.example.sep26management.exception.ResourceNotFoundException("No putaway task found for GRN: " + grnId));
        return getTask(task.getPutawayTaskId());
    }

    // ─── Get suggestions for a task ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<List<PutawaySuggestion>> getSuggestions(Long taskId) {
        log.info("[SUGGESTION DEBUG] ═══ getSuggestions called for taskId={} ═══", taskId);
        PutawayTaskEntity task = findTask(taskId);
        log.info("[SUGGESTION DEBUG] Task found: warehouseId={}", task.getWarehouseId());
        
        List<PutawayTaskItemEntity> rawItems = putawayTaskItemRepo.findByPutawayTaskPutawayTaskId(taskId);
        List<PutawayTaskItemEntity> groupedItems = groupItems(rawItems);
        log.info("[SUGGESTION DEBUG] Task has {} items (raw={}, grouped={})", groupedItems.size(), rawItems.size(), groupedItems.size());

        List<PutawaySuggestion> suggestions = new ArrayList<>();
        for (PutawayTaskItemEntity item : groupedItems) {
            log.info("[SUGGESTION DEBUG] Processing item: skuId={}, qty={}", item.getSkuId(), item.getQuantity());
            try {
                Optional<PutawaySuggestion> suggestion = putawaySuggestionService.suggestLocation(
                        task.getWarehouseId(), item.getSkuId(), item.getQuantity());
                if (suggestion.isPresent()) {
                    PutawaySuggestion s = suggestion.get();
                    log.info("[SUGGESTION DEBUG] ✓ FOUND suggestion: zone={} (zoneId={}), bin={} (locationId={}), available={}",
                            s.getMatchedZoneCode(), s.getMatchedZoneId(), s.getSuggestedLocationCode(), s.getSuggestedLocationId(), s.getAvailableCapacity());
                    suggestions.add(s);
                } else {
                    log.warn("[SUGGESTION DEBUG] ✗ NO suggestion returned for skuId={}", item.getSkuId());
                    PutawaySuggestion fallback = PutawaySuggestion.builder()
                            .skuId(item.getSkuId())
                            .reason("No matching zone or available BIN found for this SKU. "
                                    + "Check: (1) SKU has category assigned, "
                                    + "(2) Zone 'Z-{categoryCode}' exists and is active, "
                                    + "(3) Zone has active BIN locations with capacity.")
                            .build();
                    suggestions.add(fallback);
                }
            } catch (Exception e) {
                log.error("[SUGGESTION DEBUG] ✗ EXCEPTION for skuId={}: {}", item.getSkuId(), e.getMessage(), e);
                PutawaySuggestion fallback = PutawaySuggestion.builder()
                        .skuId(item.getSkuId())
                        .reason("Error: " + e.getMessage())
                        .build();
                suggestions.add(fallback);
            }
        }

        log.info("[SUGGESTION DEBUG] ═══ Result: {} total suggestions, {} with valid zone ═══",
                suggestions.size(), suggestions.stream().filter(s -> s.getMatchedZoneId() != null).count());
        return ApiResponse.success("OK", suggestions);
    }

    // ─── Allocate (Reserve) ────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<List<PutawayAllocationResponse>> allocate(Long taskId, PutawayAllocateRequest request, Long userId) {
        PutawayTaskEntity task = findTask(taskId);
        validateTaskStatus(task);

        // ── Ownership guard: task đang IN_PROGRESS bởi Keeper khác thì block ────
        // PENDING/OPEN: bất kỳ Keeper nào cũng có thể claim
        // IN_PROGRESS:  chỉ Keeper đã claim (assignedTo) mới được allocate tiếp
        if ("IN_PROGRESS".equals(task.getStatus())
                && task.getAssignedTo() != null
                && !task.getAssignedTo().equals(userId)) {
            throw new RuntimeException(
                    "Task " + taskId + " đang được xử lý bởi nhân viên khác (userId=" + task.getAssignedTo() + "). "
                            + "Bạn không thể cất hàng vào task này.");
        }

        List<PutawayTaskItemEntity> taskItems = putawayTaskItemRepo.findByPutawayTaskPutawayTaskId(taskId);
        List<PutawayAllocationResponse> results = new ArrayList<>();

        for (PutawayAllocateRequest.AllocateItem alloc : request.getItems()) {
            PutawayTaskItemEntity taskItem = taskItems.stream()
                    .filter(ti -> ti.getPutawayTaskItemId().equals(alloc.getPutawayTaskItemId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Task Item " + alloc.getPutawayTaskItemId() + " not found in putaway task " + taskId));

            // [FIX] Validate theo putawayTaskItemId — KHÔNG gộp theo skuId+lotId
            BigDecimal itemTaskQty = taskItem.getQuantity();
            BigDecimal itemPutawayQty = taskItem.getPutawayQty();

            BigDecimal alreadyAllocated = allocationRepo.findByPutawayTaskIdAndStatus(taskId, "RESERVED").stream()
                    .filter(a -> alloc.getPutawayTaskItemId().equals(a.getPutawayTaskItemId()))
                    .map(PutawayAllocationEntity::getAllocatedQty)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalUsed = itemPutawayQty.add(alreadyAllocated).add(alloc.getQty());
            if (totalUsed.compareTo(itemTaskQty) > 0) {
                BigDecimal remaining = itemTaskQty.subtract(itemPutawayQty).subtract(alreadyAllocated);
                throw new RuntimeException("Cannot allocate " + alloc.getQty() + " units of Task Item " + alloc.getPutawayTaskItemId()
                        + ". Remaining to allocate: " + remaining);
            }

            // SELECT FOR UPDATE: khóa bin row trước check capacity
            // → chống 2 Keeper putaway vào cùng BIN đồng thời gây overflow
            LocationEntity bin = locationRepo.findByIdForUpdate(alloc.getLocationId())
                    .orElseThrow(() -> new RuntimeException("Location not found: " + alloc.getLocationId()));
            if (bin.getMaxWeightKg() != null) {
                BigDecimal occupied = inventorySnapshotRepo.sumQuantityByLocationId(alloc.getLocationId());
                BigDecimal binReserved = allocationRepo.sumReservedQtyByLocation(alloc.getLocationId());
                BigDecimal totalInBin = occupied.add(binReserved).add(alloc.getQty());
                if (totalInBin.compareTo(bin.getMaxWeightKg()) > 0) {
                    BigDecimal binAvailable = bin.getMaxWeightKg().subtract(occupied).subtract(binReserved);
                    throw new RuntimeException("Bin " + bin.getLocationCode() + " does not have enough capacity. Available: " + binAvailable);
                }
            }

            PutawayAllocationEntity allocation = PutawayAllocationEntity.builder()
                    .putawayTaskId(taskId)
                    .putawayTaskItemId(alloc.getPutawayTaskItemId())
                    .skuId(alloc.getSkuId())
                    .lotId(taskItem.getLotId())
                    .locationId(alloc.getLocationId())
                    .allocatedQty(alloc.getQty())
                    .status("RESERVED")
                    .allocatedBy(userId)
                    .build();
            allocationRepo.save(allocation);

            results.add(toAllocationResponse(allocation));
        }

        // Update task status + publish realtime
        String oldStatus = task.getStatus();
        if ("PENDING".equals(task.getStatus()) || "OPEN".equals(task.getStatus())) {
            task.setStatus("IN_PROGRESS");
            task.setAssignedTo(userId);
            task.setStartedAt(LocalDateTime.now());
            putawayTaskRepo.save(task);
        }

        // ── Realtime: push trạng thái IN_PROGRESS / ALLOCATED ────────────────
        publishEvent(task, oldStatus, "ALLOCATED", userId);
        // ─────────────────────────────────────────────────────────────────────

        log.info("Putaway task {} allocated {} items by userId={}", taskId, results.size(), userId);
        return ApiResponse.success("Allocated " + results.size() + " items successfully.", results);
    }

    // ─── Confirm all allocations ──────────────────────────────────────────────

    @Transactional
    public ApiResponse<PutawayTaskResponse> confirmAll(Long taskId, Long userId) {
        PutawayTaskEntity task = findTask(taskId);
        validateTaskStatus(task);

        // ── Ownership guard: chỉ Keeper đã claim task mới được confirm ─────────
        if (task.getAssignedTo() != null && !task.getAssignedTo().equals(userId)) {
            throw new RuntimeException(
                    "Chỉ nhân viên đã xử lý task này (userId=" + task.getAssignedTo() + ") mới có thể xác nhận cất hàng.");
        }

        List<PutawayAllocationEntity> reservations = allocationRepo.findByPutawayTaskIdAndStatus(taskId, "RESERVED");
        if (reservations.isEmpty()) {
            throw new RuntimeException("No RESERVED allocations to confirm for task " + taskId);
        }

        List<PutawayTaskItemEntity> rawTaskItems = putawayTaskItemRepo.findByPutawayTaskPutawayTaskId(taskId);

        // [FIX] Validate theo từng item riêng biệt (putawayTaskItemId), KHÔNG groupItems
        for (PutawayTaskItemEntity item : rawTaskItems) {
            BigDecimal allocated = reservations.stream()
                    .filter(a -> item.getPutawayTaskItemId().equals(a.getPutawayTaskItemId()))
                    .map(PutawayAllocationEntity::getAllocatedQty)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal remaining = item.getQuantity().subtract(item.getPutawayQty()).subtract(allocated);
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                String skuCode = skuRepo.findById(item.getSkuId())
                        .map(sku -> sku.getSkuCode()).orElse("SKU " + item.getSkuId());
                String lotInfo = "";
                if (item.getLotId() != null) {
                    lotInfo = inventoryLotRepo.findById(item.getLotId())
                            .map(lot -> " (Lô: " + lot.getLotNumber() + ")")
                            .orElse(" (Lot #" + item.getLotId() + ")");
                } else if (item.getReceivingItemId() != null) {
                    lotInfo = receivingItemRepo.findById(item.getReceivingItemId())
                            .filter(rcv -> rcv.getLotNumber() != null && !rcv.getLotNumber().isBlank())
                            .map(rcv -> " (Lô: " + rcv.getLotNumber() + ")")
                            .orElse("");
                }
                throw new RuntimeException("Chưa phân bổ hết hàng! " + skuCode + lotInfo
                        + " còn " + remaining + " units chưa được allocate. Hãy allocate hết rồi mới confirm.");
            }
        }

        for (PutawayAllocationEntity alloc : reservations) {
            // [FIX] Match theo putawayTaskItemId — chính xác tới từng item
            PutawayTaskItemEntity matchedItem = alloc.getPutawayTaskItemId() != null
                    ? rawTaskItems.stream()
                        .filter(ti -> ti.getPutawayTaskItemId().equals(alloc.getPutawayTaskItemId()))
                        .findFirst().orElse(null)
                    : null;

            // Fallback cho allocations cũ chưa có putawayTaskItemId
            if (matchedItem == null) {
                matchedItem = rawTaskItems.stream()
                        .filter(ti -> ti.getSkuId().equals(alloc.getSkuId()) &&
                                (alloc.getLotId() == null ? ti.getLotId() == null : alloc.getLotId().equals(ti.getLotId())))
                        .filter(ti -> ti.getQuantity().subtract(ti.getPutawayQty()).compareTo(BigDecimal.ZERO) > 0)
                        .findFirst()
                        .orElse(rawTaskItems.stream()
                                .filter(ti -> ti.getSkuId().equals(alloc.getSkuId()))
                                .findFirst().orElse(null));
            }

            if (matchedItem == null) {
                throw new RuntimeException("Task item not found for SKU: " + alloc.getSkuId() + " Lot: " + alloc.getLotId());
            }

            BigDecimal qtyToDistribute = alloc.getAllocatedQty();

            inventorySnapshotRepo.upsertInventory(
                    task.getWarehouseId(), alloc.getSkuId(), alloc.getLotId(), alloc.getLocationId(), qtyToDistribute);

            jdbcTemplate.update(
                    "INSERT INTO inventory_transactions (warehouse_id, sku_id, lot_id, location_id, quantity, txn_type, reference_table, reference_id, created_by) "
                            + "VALUES (?, ?, ?, ?, ?, 'PUTAWAY', 'putaway_tasks', ?, ?)",
                    task.getWarehouseId(), alloc.getSkuId(), alloc.getLotId(), alloc.getLocationId(), qtyToDistribute, taskId, userId);

            // Cộng putawayQty cho chính item được match
            BigDecimal itemRemainingCap = matchedItem.getQuantity().subtract(matchedItem.getPutawayQty());
            BigDecimal toAdd = itemRemainingCap.min(qtyToDistribute);
            matchedItem.setPutawayQty(matchedItem.getPutawayQty().add(toAdd));
            matchedItem.setActualLocationId(alloc.getLocationId());
            putawayTaskItemRepo.save(matchedItem);

            alloc.setStatus("CONFIRMED");
            allocationRepo.save(alloc);
        }

        String oldStatus = task.getStatus();
        boolean allDone = rawTaskItems.stream().allMatch(i -> i.getPutawayQty().compareTo(i.getQuantity()) >= 0);
        if (allDone) {
            task.setStatus("DONE");
            task.setCompletedAt(LocalDateTime.now());
        }

        putawayTaskRepo.save(task);
        log.info("Putaway task {} confirmed all allocations by userId={}, status={}", taskId, userId, task.getStatus());

        String grnCode = grnRepo.findById(task.getGrnId())
                .map(g -> g.getGrnCode()).orElse("GRN #" + task.getGrnId());

        // ── Realtime: push CONFIRMED / DONE tới tất cả role ──────────────────
        publishEvent(task, oldStatus, "CONFIRMED", userId);
        // ─────────────────────────────────────────────────────────────────────

        // Notification bell (giữ nguyên logic cũ)
        if (allDone) {
            notificationService.notifyRoles(new String[]{"MANAGER", "QC", "KEEPER"},
                    "putaway_pending",
                    task.getPutawayTaskId(), "Task #" + task.getPutawayTaskId(),
                    grnCode + " — Putaway hoàn thành");
        }

        return ApiResponse.success("Putaway confirmed. Status: " + task.getStatus(), toResponse(task));
    }

    // ─── Cancel allocation ────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<Void> cancelAllocation(Long taskId, Long allocationId) {
        PutawayAllocationEntity alloc = allocationRepo.findById(allocationId)
                .orElseThrow(() -> new RuntimeException("Allocation not found: " + allocationId));
        if (!alloc.getPutawayTaskId().equals(taskId)) {
            throw new RuntimeException("Allocation " + allocationId + " does not belong to task " + taskId);
        }
        if (!"RESERVED".equals(alloc.getStatus())) {
            throw new RuntimeException("Cannot cancel allocation in status: " + alloc.getStatus());
        }
        alloc.setStatus("CANCELLED");
        allocationRepo.save(alloc);
        log.info("Cancelled allocation {} for task {}", allocationId, taskId);
        return ApiResponse.success("Allocation cancelled.", null);
    }

    // ─── Get allocations ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<List<PutawayAllocationResponse>> getAllocations(Long taskId) {
        List<PutawayAllocationEntity> allocations = allocationRepo.findByPutawayTaskId(taskId);
        List<PutawayAllocationResponse> results = allocations.stream()
                .map(this::toAllocationResponse)
                .collect(Collectors.toList());
        return ApiResponse.success("OK", results);
    }

    // ─── Private helpers ───────────────────────────────────────────────────────

    /**
     * Publish realtime event lên Redis Pub/Sub → WebSocket tới FE.
     * Gọi sau mọi thao tác thay đổi state của task.
     */
    private void publishEvent(PutawayTaskEntity task, String oldStatus, String eventType, Long actorUserId) {
        try {
            String grnCode = grnRepo.findById(task.getGrnId())
                    .map(g -> g.getGrnCode()).orElse("GRN#" + task.getGrnId());
            putawayEventPublisher.publish(PutawayTaskEvent.builder()
                    .taskId(task.getPutawayTaskId())
                    .warehouseId(task.getWarehouseId())
                    .newStatus(task.getStatus())
                    .oldStatus(oldStatus)
                    .grnCode(grnCode)
                    .actorUserId(actorUserId)
                    .eventType(eventType)
                    .build());
        } catch (Exception e) {
            // Không để realtime failure ảnh hưởng business
            log.warn("[Putaway] publishEvent failed for taskId={}: {}", task.getPutawayTaskId(), e.getMessage());
        }
    }

    private void validateTaskStatus(PutawayTaskEntity task) {
        if (!"PENDING".equals(task.getStatus()) && !"OPEN".equals(task.getStatus())
                && !"IN_PROGRESS".equals(task.getStatus())) {
            throw new RuntimeException("Putaway task " + task.getPutawayTaskId() + " is in invalid status: " + task.getStatus());
        }
    }

    private PutawayTaskEntity findTask(Long id) {
        return putawayTaskRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Putaway task not found: " + id));
    }

    private PutawayTaskResponse toResponse(PutawayTaskEntity t) {
        return toResponse(t, null); // Will fallback to itemCount calculated by DB
    }

    private PutawayTaskResponse toResponse(PutawayTaskEntity t, Integer groupedItemCount) {
        String grnCode = null;
        if (t.getGrnId() != null) {
            grnCode = grnRepo.findById(t.getGrnId())
                    .map(g -> g.getGrnCode())
                    .orElse(null);
        }

        String receivingCode = null;
        if (t.getReceivingId() != null) {
            receivingCode = receivingOrderRepo.findById(t.getReceivingId())
                    .map(r -> r.getReceivingCode())
                    .orElse(null);
        }

        int itemCount = groupedItemCount != null ? groupedItemCount : (int) putawayTaskItemRepo.findByPutawayTaskPutawayTaskId(t.getPutawayTaskId()).size();

        return PutawayTaskResponse.builder()
                .putawayTaskId(t.getPutawayTaskId())
                .warehouseId(t.getWarehouseId())
                .grnId(t.getGrnId())
                .grnCode(grnCode)
                .receivingId(t.getReceivingId())
                .receivingCode(receivingCode)
                .itemCount(itemCount)
                .status(t.getStatus())
                .fromLocationId(t.getFromLocationId())
                .assignedTo(t.getAssignedTo())
                .createdAt(t.getCreatedAt())
                .completedAt(t.getCompletedAt())
                .note(t.getNote())
                .signedNoteUrl(t.getSignedNoteUrl())
                .signedNoteUploadedAt(t.getSignedNoteUploadedAt())
                .build();
    }

    private PutawayTaskResponse.PutawayTaskItemDto toItemDtoEnriched(PutawayTaskItemEntity i) {
        // [FIX] Tính allocatedQty theo putawayTaskItemId — KHÔNG gộp theo skuId+lotId
        BigDecimal allocatedQty = allocationRepo.findByPutawayTaskId(i.getPutawayTask().getPutawayTaskId()).stream()
                .filter(a -> i.getPutawayTaskItemId().equals(a.getPutawayTaskItemId()))
                .filter(a -> "RESERVED".equals(a.getStatus()))
                .map(PutawayAllocationEntity::getAllocatedQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Fallback: allocations cũ chưa có putawayTaskItemId → match theo skuId+lotId
        if (allocatedQty.compareTo(BigDecimal.ZERO) == 0) {
            allocatedQty = allocationRepo.findByPutawayTaskId(i.getPutawayTask().getPutawayTaskId()).stream()
                    .filter(a -> a.getPutawayTaskItemId() == null) // chỉ fallback cho records cũ
                    .filter(a -> a.getSkuId().equals(i.getSkuId()))
                    .filter(a -> i.getLotId() == null ? a.getLotId() == null : i.getLotId().equals(a.getLotId()))
                    .filter(a -> "RESERVED".equals(a.getStatus()))
                    .map(PutawayAllocationEntity::getAllocatedQty)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal remainingQty = i.getQuantity().subtract(i.getPutawayQty()).subtract(allocatedQty);
        if (remainingQty.compareTo(BigDecimal.ZERO) < 0) remainingQty = BigDecimal.ZERO;

        PutawayTaskResponse.PutawayTaskItemDto.PutawayTaskItemDtoBuilder builder = PutawayTaskResponse.PutawayTaskItemDto
                .builder()
                .putawayTaskItemId(i.getPutawayTaskItemId())
                .skuId(i.getSkuId())
                .lotId(i.getLotId())
                .quantity(i.getQuantity())
                .putawayQty(i.getPutawayQty())
                .allocatedQty(allocatedQty)
                .remainingQty(remainingQty)
                .suggestedLocationId(i.getSuggestedLocationId())
                .actualLocationId(i.getActualLocationId());

        // Enrich lot info: ưu tiên inventoryLot, fallback receivingItem
        if (i.getLotId() != null) {
            inventoryLotRepo.findById(i.getLotId()).ifPresent(lot -> {
                builder.lotNumber(lot.getLotNumber());
                if (lot.getExpiryDate() != null) builder.expiryDate(lot.getExpiryDate().toString());
            });
        } else if (i.getReceivingItemId() != null) {
            receivingItemRepo.findById(i.getReceivingItemId()).ifPresent(rcv -> {
                if (rcv.getLotNumber() != null && !rcv.getLotNumber().isBlank()) {
                    builder.lotNumber(rcv.getLotNumber());
                }
                if (rcv.getExpiryDate() != null) {
                    builder.expiryDate(rcv.getExpiryDate().toString());
                }
            });
        }

        skuRepo.findById(i.getSkuId()).ifPresent(sku -> {
            builder.skuCode(sku.getSkuCode());
            builder.skuName(sku.getSkuName());
        });

        return builder.build();
    }

    private PutawayAllocationResponse toAllocationResponse(PutawayAllocationEntity a) {
        PutawayAllocationResponse.PutawayAllocationResponseBuilder builder = PutawayAllocationResponse.builder()
                .allocationId(a.getAllocationId())
                .putawayTaskId(a.getPutawayTaskId())
                .putawayTaskItemId(a.getPutawayTaskItemId())
                .skuId(a.getSkuId())
                .lotId(a.getLotId())
                .locationId(a.getLocationId())
                .allocatedQty(a.getAllocatedQty())
                .status(a.getStatus())
                .allocatedBy(a.getAllocatedBy())
                .allocatedAt(a.getAllocatedAt());

        skuRepo.findById(a.getSkuId()).ifPresent(sku -> {
            builder.skuCode(sku.getSkuCode());
            builder.skuName(sku.getSkuName());
        });
        locationRepo.findById(a.getLocationId()).ifPresent(loc -> {
            builder.locationCode(loc.getLocationCode());
        });

        // Enrich lot info
        if (a.getLotId() != null) {
            inventoryLotRepo.findById(a.getLotId()).ifPresent(lot -> {
                builder.lotNumber(lot.getLotNumber());
                if (lot.getExpiryDate() != null) builder.expiryDate(lot.getExpiryDate().toString());
            });
        }

        return builder.build();
    }

    /**
     * Group items theo putawayTaskItemId (mỗi item là unique).
     * Giữ lại method này cho backward compatibility với getTask() và getSuggestions().
     * Không gộp items nữa — mỗi PutawayTaskItem là riêng biệt.
     */
    private List<PutawayTaskItemEntity> groupItems(List<PutawayTaskItemEntity> rawItems) {
        // [FIX] Không gộp nữa — mỗi putawayTaskItemId là unique (1 SKU + 1 Lot)
        return new ArrayList<>(rawItems);
    }
}