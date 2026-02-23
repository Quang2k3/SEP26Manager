package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.request.PutawayConfirmRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.PutawayTaskResponse;
import org.example.sep26management.infrastructure.persistence.entity.PutawayTaskEntity;
import org.example.sep26management.infrastructure.persistence.entity.PutawayTaskItemEntity;
import org.example.sep26management.infrastructure.persistence.repository.PutawayTaskItemJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.PutawayTaskJpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PutawayTaskService {

    private final PutawayTaskJpaRepository putawayTaskRepo;
    private final PutawayTaskItemJpaRepository putawayTaskItemRepo;
    private final JdbcTemplate jdbcTemplate;

    // ─── List tasks ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<List<PutawayTaskResponse>> listTasks(Long assignedTo, String status) {
        List<PutawayTaskEntity> tasks;
        if (assignedTo != null && status != null) {
            tasks = putawayTaskRepo.findByAssignedToAndStatusOrderByCreatedAtDesc(assignedTo, status);
        } else if (status != null) {
            tasks = putawayTaskRepo.findByWarehouseIdAndStatusOrderByCreatedAtDesc(null, status);
        } else {
            tasks = putawayTaskRepo.findAllByOrderByCreatedAtDesc();
        }
        return ApiResponse.success("OK", tasks.stream().map(t -> toResponse(t, false)).collect(Collectors.toList()));
    }

    // ─── Get task detail ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<PutawayTaskResponse> getTask(Long taskId) {
        PutawayTaskEntity task = findTask(taskId);
        List<PutawayTaskItemEntity> items = putawayTaskItemRepo.findByPutawayTaskPutawayTaskId(taskId);
        PutawayTaskResponse response = toResponse(task, false);
        response.setItems(items.stream().map(this::toItemDto).collect(Collectors.toList()));
        return ApiResponse.success("OK", response);
    }

    // ─── Confirm putaway ───────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<PutawayTaskResponse> confirm(Long taskId, PutawayConfirmRequest request, Long userId) {
        PutawayTaskEntity task = findTask(taskId);

        if (!"OPEN".equals(task.getStatus()) && !"IN_PROGRESS".equals(task.getStatus())) {
            throw new RuntimeException("Putaway task " + taskId + " is already " + task.getStatus());
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

            // Decrease from staging location
            if (fromLocationId != null) {
                jdbcTemplate.update(
                        "UPDATE inventory_snapshot SET quantity = quantity - ?, last_updated = NOW() " +
                                "WHERE warehouse_id = ? AND sku_id = ? AND location_id = ? AND COALESCE(lot_id,0) = ?",
                        qty, task.getWarehouseId(), item.getSkuId(), fromLocationId,
                        item.getLotId() != null ? item.getLotId() : 0L);
            }

            // Upsert to target location
            jdbcTemplate.update(
                    "INSERT INTO inventory_snapshot (warehouse_id, sku_id, lot_id, location_id, quantity, last_updated) "
                            +
                            "VALUES (?, ?, ?, ?, ?, NOW()) " +
                            "ON CONFLICT (warehouse_id, sku_id, lot_id_safe, location_id) " +
                            "DO UPDATE SET quantity = inventory_snapshot.quantity + EXCLUDED.quantity, last_updated = NOW()",
                    task.getWarehouseId(), item.getSkuId(), item.getLotId(), toLocationId, qty);

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

        return ApiResponse.success("Putaway confirmed. Status: " + task.getStatus(), toResponse(task, false));
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private PutawayTaskEntity findTask(Long id) {
        return putawayTaskRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Putaway task not found: " + id));
    }

    private PutawayTaskResponse toResponse(PutawayTaskEntity t, boolean withItems) {
        return PutawayTaskResponse.builder()
                .putawayTaskId(t.getPutawayTaskId())
                .warehouseId(t.getWarehouseId())
                .receivingId(t.getReceivingId())
                .status(t.getStatus())
                .fromLocationId(t.getFromLocationId())
                .assignedTo(t.getAssignedTo())
                .createdAt(t.getCreatedAt())
                .completedAt(t.getCompletedAt())
                .note(t.getNote())
                .build();
    }

    private PutawayTaskResponse.PutawayTaskItemDto toItemDto(PutawayTaskItemEntity i) {
        return PutawayTaskResponse.PutawayTaskItemDto.builder()
                .putawayTaskItemId(i.getPutawayTaskItemId())
                .skuId(i.getSkuId())
                .lotId(i.getLotId())
                .quantity(i.getQuantity())
                .putawayQty(i.getPutawayQty())
                .suggestedLocationId(i.getSuggestedLocationId())
                .actualLocationId(i.getActualLocationId())
                .build();
    }
}
