package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.request.PutawayConfirmRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.PageResponse;
import org.example.sep26management.application.dto.response.PutawaySuggestion;
import org.example.sep26management.application.dto.response.PutawayTaskResponse;
import org.example.sep26management.infrastructure.persistence.entity.PutawayTaskEntity;
import org.example.sep26management.infrastructure.persistence.entity.PutawayTaskItemEntity;
import org.example.sep26management.infrastructure.persistence.repository.InventorySnapshotJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.LocationJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.PutawayTaskItemJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.PutawayTaskJpaRepository;
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

    // ─── List tasks ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<PageResponse<PutawayTaskResponse>> listTasks(Long assignedTo, String status, int page,
            int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PutawayTaskEntity> tasksPage;
        if (assignedTo != null && status != null) {
            tasksPage = putawayTaskRepo.findByAssignedToAndStatusOrderByCreatedAtDesc(assignedTo, status, pageable);
        } else if (status != null) {
            tasksPage = putawayTaskRepo.findByWarehouseIdAndStatusOrderByCreatedAtDesc(null, status, pageable);
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
        List<PutawayTaskItemEntity> items = putawayTaskItemRepo.findByPutawayTaskPutawayTaskId(taskId);
        PutawayTaskResponse response = toResponse(task);
        response.setItems(items.stream().map(this::toItemDtoEnriched).collect(Collectors.toList()));
        return ApiResponse.success("OK", response);
    }

    @Transactional(readOnly = true)
    public ApiResponse<PutawayTaskResponse> getTaskByGrnId(Long grnId) {
        PutawayTaskEntity task = putawayTaskRepo.findByGrnId(grnId)
                .orElseThrow(() -> new RuntimeException("No putaway task found for GRN: " + grnId));
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

    // ─── Confirm putaway ───────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<PutawayTaskResponse> confirm(Long taskId, PutawayConfirmRequest request, Long userId) {
        PutawayTaskEntity task = findTask(taskId);

        if (!"PENDING".equals(task.getStatus()) && !"OPEN".equals(task.getStatus())
                && !"IN_PROGRESS".equals(task.getStatus())) {
            throw new RuntimeException("Putaway task " + taskId + " is in invalid status: " + task.getStatus());
        }

        task.setStatus("IN_PROGRESS");
        task.setAssignedTo(userId);
        task.setStartedAt(LocalDateTime.now());

        for (PutawayConfirmRequest.PutawayItemConfirm confirm : request.getItems()) {
            PutawayTaskItemEntity item = putawayTaskItemRepo.findById(confirm.getPutawayTaskItemId())
                    .orElseThrow(
                            () -> new RuntimeException("PutawayTaskItem not found: " + confirm.getPutawayTaskItemId()));

            Long fromLocationId = task.getFromLocationId();
            Long toLocationId = confirm.getLocationId();
            BigDecimal qty = confirm.getQty();

            // Validation: Prevent over-putaway
            BigDecimal remaining = item.getQuantity().subtract(item.getPutawayQty());
            if (qty.compareTo(remaining) > 0) {
                throw new RuntimeException(
                        "Cannot putaway " + qty + " units. Remaining for this item is only " + remaining);
            }

            // Decrease from staging location (Use Repository)
            if (fromLocationId != null) {
                inventorySnapshotRepo.decrementQuantity(
                        task.getWarehouseId(),
                        item.getSkuId(),
                        item.getLotId(),
                        fromLocationId,
                        qty);
            }

            // Upsert to target location (Use Repository for safety)
            inventorySnapshotRepo.upsertInventory(
                    task.getWarehouseId(),
                    item.getSkuId(),
                    item.getLotId(),
                    toLocationId,
                    qty);

            // Record PUTAWAY transaction
            jdbcTemplate.update(
                    "INSERT INTO inventory_transactions (warehouse_id, sku_id, lot_id, location_id, quantity, txn_type, reference_table, reference_id, created_by) "
                            +
                            "VALUES (?, ?, ?, ?, ?, 'PUTAWAY', 'putaway_tasks', ?, ?)",
                    task.getWarehouseId(), item.getSkuId(), item.getLotId(), toLocationId, qty, taskId, userId);

            // Update putaway item
            item.setPutawayQty(item.getPutawayQty().add(qty));
            item.setActualLocationId(toLocationId);
            putawayTaskItemRepo.save(item);
        }

        // Check if all items are done
        List<PutawayTaskItemEntity> allItems = putawayTaskItemRepo.findByPutawayTaskPutawayTaskId(taskId);
        boolean allDone = allItems.stream().allMatch(i -> i.getPutawayQty().compareTo(i.getQuantity()) >= 0);
        if (allDone) {
            task.setStatus("DONE");
            task.setCompletedAt(LocalDateTime.now());
        }

        putawayTaskRepo.save(task);
        log.info("Putaway task {} confirmed by userId={}, status={}", taskId, userId, task.getStatus());

        return ApiResponse.success("Putaway confirmed. Status: " + task.getStatus(), toResponse(task));
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private PutawayTaskEntity findTask(Long id) {
        return putawayTaskRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Putaway task not found: " + id));
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

    /**
     * Enriched item DTO: resolves suggestedLocationId → location code, zone, aisle,
     * rack, capacity.
     */
    private PutawayTaskResponse.PutawayTaskItemDto toItemDtoEnriched(PutawayTaskItemEntity i) {
        PutawayTaskResponse.PutawayTaskItemDto.PutawayTaskItemDtoBuilder builder = PutawayTaskResponse.PutawayTaskItemDto
                .builder()
                .putawayTaskItemId(i.getPutawayTaskItemId())
                .skuId(i.getSkuId())
                .lotId(i.getLotId())
                .quantity(i.getQuantity())
                .putawayQty(i.getPutawayQty())
                .suggestedLocationId(i.getSuggestedLocationId())
                .actualLocationId(i.getActualLocationId());

        // Enrich suggestion details from location hierarchy
        if (i.getSuggestedLocationId() != null) {
            locationRepo.findById(i.getSuggestedLocationId()).ifPresent(loc -> {
                builder.suggestedLocationCode(loc.getLocationCode());

                // Resolve parent rack → aisle
                if (loc.getParentLocationId() != null) {
                    locationRepo.findById(loc.getParentLocationId()).ifPresent(rack -> {
                        builder.suggestedRack(rack.getLocationCode());
                        if (rack.getParentLocationId() != null) {
                            locationRepo.findById(rack.getParentLocationId()).ifPresent(aisle -> {
                                builder.suggestedAisle(aisle.getLocationCode());
                            });
                        }
                    });
                }

                // Resolve zone code
                if (loc.getZoneId() != null) {
                    zoneRepo.findById(loc.getZoneId()).ifPresent(zone -> {
                        builder.suggestedZoneCode(zone.getZoneCode());
                    });
                }

                // Capacity info
                BigDecimal currentQty = locationRepo.getCurrentOccupiedQty(loc.getLocationId());
                BigDecimal maxCap = loc.getMaxWeightKg() != null ? loc.getMaxWeightKg() : BigDecimal.ZERO;
                builder.binCurrentQty(currentQty);
                builder.binMaxCapacity(maxCap);
                builder.binAvailableCapacity(maxCap.subtract(currentQty));
            });
        }

        return builder.build();
    }
}
