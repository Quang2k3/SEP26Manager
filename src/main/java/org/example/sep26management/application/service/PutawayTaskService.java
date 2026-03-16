package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.request.PutawayAllocateRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.PageResponse;
import org.example.sep26management.application.dto.response.PutawayAllocationResponse;
import org.example.sep26management.application.dto.response.PutawaySuggestion;
import org.example.sep26management.application.dto.response.PutawayTaskResponse;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.example.sep26management.infrastructure.exception.ResourceNotFoundException;
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

    // ─── List tasks ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<PageResponse<PutawayTaskResponse>> listTasks(Long warehouseId, Long assignedTo, String status, int page,
                                                                    int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PutawayTaskEntity> tasksPage;
        if (assignedTo != null && status != null) {
            tasksPage = putawayTaskRepo.findByAssignedToAndStatusOrderByCreatedAtDesc(assignedTo, status, pageable);
        } else if (status != null) {
            tasksPage = putawayTaskRepo.findByWarehouseIdAndStatusOrderByCreatedAtDesc(warehouseId, status, pageable);
        } else {
            // Luôn filter theo warehouseId của user đang login
            tasksPage = putawayTaskRepo.findByWarehouseIdOrderByCreatedAtDesc(warehouseId, pageable);
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
        List<PutawayTaskItemEntity> items = putawayTaskItemRepo.findByPutawayTaskPutawayTaskId(taskId);
        PutawayTaskResponse response = toResponse(task);
        response.setItems(items.stream().map(this::toItemDtoEnriched).collect(Collectors.toList()));
        return ApiResponse.success("OK", response);
    }

    @Transactional(readOnly = true)
    public ApiResponse<PutawayTaskResponse> getTaskByGrnId(Long grnId) {
        // BUG-09 FIX: ResourceNotFoundException → HTTP 404 thay vì 500
        PutawayTaskEntity task = putawayTaskRepo.findByGrnId(grnId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy putaway task cho GRN: " + grnId));
        return getTask(task.getPutawayTaskId());
    }

    // ─── Get suggestions for a task ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<List<PutawaySuggestion>> getSuggestions(Long taskId) {
        PutawayTaskEntity task = findTask(taskId);
        List<PutawayTaskItemEntity> items = putawayTaskItemRepo.findByPutawayTaskPutawayTaskId(taskId);

        List<PutawaySuggestion> suggestions = new ArrayList<>();
        for (PutawayTaskItemEntity item : items) {
            Optional<PutawaySuggestion> suggestion = putawaySuggestionService.suggestLocation(
                    task.getWarehouseId(), item.getSkuId(), item.getQuantity());
            if (suggestion.isPresent()) {
                suggestions.add(suggestion.get());
            } else {
                // Return a fallback entry so the caller knows which items couldn't be matched
                PutawaySuggestion fallback = PutawaySuggestion.builder()
                        .skuId(item.getSkuId())
                        .reason("No matching zone or available BIN found for this SKU. "
                                + "Check: (1) SKU has category assigned, "
                                + "(2) Zone 'Z-{categoryCode}' exists and is active, "
                                + "(3) Zone has active BIN locations with capacity.")
                        .build();
                suggestions.add(fallback);
            }
        }

        return ApiResponse.success("OK", suggestions);
    }

    // ─── Allocate (Reserve) ────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<List<PutawayAllocationResponse>> allocate(Long taskId, PutawayAllocateRequest request, Long userId) {
        PutawayTaskEntity task = findTask(taskId);
        validateTaskStatus(task);

        List<PutawayTaskItemEntity> taskItems = putawayTaskItemRepo.findByPutawayTaskPutawayTaskId(taskId);
        List<PutawayAllocationResponse> results = new ArrayList<>();

        for (PutawayAllocateRequest.AllocateItem alloc : request.getItems()) {
            // Find matching task item by skuId
            // BUG-09 FIX: ResourceNotFoundException → HTTP 404
            PutawayTaskItemEntity taskItem = taskItems.stream()
                    .filter(ti -> ti.getSkuId().equals(alloc.getSkuId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "SKU " + alloc.getSkuId() + " không tồn tại trong putaway task " + taskId));

            // Check: allocated + putawayQty + newQty <= totalQty
            BigDecimal alreadyAllocated = allocationRepo.sumReservedQtyByTaskAndSku(taskId, alloc.getSkuId());
            BigDecimal totalUsed = taskItem.getPutawayQty().add(alreadyAllocated).add(alloc.getQty());
            if (totalUsed.compareTo(taskItem.getQuantity()) > 0) {
                BigDecimal remaining = taskItem.getQuantity().subtract(taskItem.getPutawayQty()).subtract(alreadyAllocated);
                // BUG-09 FIX: BusinessException → HTTP 400
                throw new BusinessException("Không thể phân bổ " + alloc.getQty() + " đơn vị SKU " + alloc.getSkuId()
                        + ". Số lượng còn có thể phân bổ: " + remaining);
            }

            // Check: bin capacity
            // BUG-09 FIX: ResourceNotFoundException → HTTP 404
            LocationEntity bin = locationRepo.findById(alloc.getLocationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy location: " + alloc.getLocationId()));
            if (bin.getMaxWeightKg() != null) {
                BigDecimal occupied = inventorySnapshotRepo.sumQuantityByLocationId(alloc.getLocationId());
                BigDecimal binReserved = allocationRepo.sumReservedQtyByLocation(alloc.getLocationId());
                BigDecimal totalInBin = occupied.add(binReserved).add(alloc.getQty());
                if (totalInBin.compareTo(bin.getMaxWeightKg()) > 0) {
                    BigDecimal binAvailable = bin.getMaxWeightKg().subtract(occupied).subtract(binReserved);
                    // BUG-09 FIX: BusinessException → HTTP 400
                    throw new BusinessException("Bin " + bin.getLocationCode()
                            + " không đủ sức chứa. Sức chứa còn lại: " + binAvailable);
                }
            }

            // Create allocation
            PutawayAllocationEntity allocation = PutawayAllocationEntity.builder()
                    .putawayTaskId(taskId)
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

        // Update task status
        if ("PENDING".equals(task.getStatus()) || "OPEN".equals(task.getStatus())) {
            task.setStatus("IN_PROGRESS");
            task.setAssignedTo(userId);
            task.setStartedAt(LocalDateTime.now());
            putawayTaskRepo.save(task);
        }

        log.info("Putaway task {} allocated {} items by userId={}", taskId, results.size(), userId);
        return ApiResponse.success("Allocated " + results.size() + " items successfully.", results);
    }

    // ─── Confirm all allocations ──────────────────────────────────────────────

    @Transactional
    public ApiResponse<PutawayTaskResponse> confirmAll(Long taskId, Long userId) {
        PutawayTaskEntity task = findTask(taskId);
        validateTaskStatus(task);

        List<PutawayAllocationEntity> reservations = allocationRepo.findByPutawayTaskIdAndStatus(taskId, "RESERVED");
        if (reservations.isEmpty()) {
            // BUG-09 FIX: BusinessException → HTTP 400
            throw new BusinessException("Không có allocation ở trạng thái RESERVED để confirm cho task " + taskId);
        }

        List<PutawayTaskItemEntity> taskItems = putawayTaskItemRepo.findByPutawayTaskPutawayTaskId(taskId);

        for (PutawayAllocationEntity alloc : reservations) {
            // BUG-09 FIX: ResourceNotFoundException → HTTP 404
            PutawayTaskItemEntity item = taskItems.stream()
                    .filter(ti -> ti.getSkuId().equals(alloc.getSkuId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy task item cho SKU: " + alloc.getSkuId()));

            Long fromLocationId = task.getFromLocationId();
            BigDecimal qty = alloc.getAllocatedQty();

            // Decrease from staging
            if (fromLocationId != null) {
                inventorySnapshotRepo.decrementQuantity(
                        task.getWarehouseId(), item.getSkuId(), item.getLotId(), fromLocationId, qty);
            }

            // Upsert to target bin
            inventorySnapshotRepo.upsertInventory(
                    task.getWarehouseId(), item.getSkuId(), item.getLotId(), alloc.getLocationId(), qty);

            // Record PUTAWAY transaction
            jdbcTemplate.update(
                    "INSERT INTO inventory_transactions (warehouse_id, sku_id, lot_id, location_id, quantity, txn_type, reference_table, reference_id, created_by) "
                            + "VALUES (?, ?, ?, ?, ?, 'PUTAWAY', 'putaway_tasks', ?, ?)",
                    task.getWarehouseId(), item.getSkuId(), item.getLotId(), alloc.getLocationId(), qty, taskId, userId);

            // Update putaway item
            item.setPutawayQty(item.getPutawayQty().add(qty));
            item.setActualLocationId(alloc.getLocationId());
            putawayTaskItemRepo.save(item);

            // Mark allocation as CONFIRMED
            alloc.setStatus("CONFIRMED");
            allocationRepo.save(alloc);
        }

        // Check if all items are done
        boolean allDone = taskItems.stream().allMatch(i -> i.getPutawayQty().compareTo(i.getQuantity()) >= 0);
        if (allDone) {
            task.setStatus("DONE");
            task.setCompletedAt(LocalDateTime.now());
        }

        putawayTaskRepo.save(task);
        log.info("Putaway task {} confirmed all allocations by userId={}, status={}", taskId, userId, task.getStatus());

        return ApiResponse.success("Putaway confirmed. Status: " + task.getStatus(), toResponse(task));
    }

    // ─── Cancel allocation ────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<Void> cancelAllocation(Long taskId, Long allocationId) {
        // BUG-09 FIX: ResourceNotFoundException → HTTP 404
        PutawayAllocationEntity alloc = allocationRepo.findById(allocationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy allocation: " + allocationId));
        // BUG-09 FIX: BusinessException → HTTP 400
        if (!alloc.getPutawayTaskId().equals(taskId)) {
            throw new BusinessException("Allocation " + allocationId + " không thuộc task " + taskId);
        }
        if (!"RESERVED".equals(alloc.getStatus())) {
            throw new BusinessException("Không thể huỷ allocation ở trạng thái: " + alloc.getStatus()
                    + ". Chỉ có thể huỷ allocation ở trạng thái RESERVED.");
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

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private void validateTaskStatus(PutawayTaskEntity task) {
        if (!"PENDING".equals(task.getStatus()) && !"OPEN".equals(task.getStatus())
                && !"IN_PROGRESS".equals(task.getStatus())) {
            // BUG-09 FIX: BusinessException → HTTP 400
            throw new BusinessException("Putaway task " + task.getPutawayTaskId()
                    + " ở trạng thái không hợp lệ để thực hiện thao tác này: " + task.getStatus());
        }
    }

    private PutawayTaskEntity findTask(Long id) {
        // BUG-09 FIX: ResourceNotFoundException → HTTP 404
        return putawayTaskRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy putaway task: " + id));
    }

    private PutawayTaskResponse toResponse(PutawayTaskEntity t) {
        return PutawayTaskResponse.builder()
                .putawayTaskId(t.getPutawayTaskId())
                .warehouseId(t.getWarehouseId())
                .grnId(t.getGrnId())
                .status(t.getStatus())
                .fromLocationId(t.getFromLocationId())
                .assignedTo(t.getAssignedTo())
                .createdAt(t.getCreatedAt())
                .completedAt(t.getCompletedAt())
                .note(t.getNote())
                .build();
    }

    private PutawayTaskResponse.PutawayTaskItemDto toItemDtoEnriched(PutawayTaskItemEntity i) {
        // Calculate allocated (RESERVED) and remaining
        BigDecimal allocatedQty = allocationRepo.sumReservedQtyByTaskAndSku(
                i.getPutawayTask().getPutawayTaskId(), i.getSkuId());
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

        // Resolve SKU code & name
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

        return builder.build();
    }
}