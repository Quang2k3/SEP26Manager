package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.ReceivingItemResponse;
import org.example.sep26management.application.dto.response.ReceivingOrderResponse;
import org.example.sep26management.infrastructure.persistence.entity.PutawayTaskEntity;
import org.example.sep26management.infrastructure.persistence.entity.PutawayTaskItemEntity;
import org.example.sep26management.infrastructure.persistence.entity.ReceivingItemEntity;
import org.example.sep26management.infrastructure.persistence.entity.ReceivingOrderEntity;
import org.example.sep26management.infrastructure.persistence.repository.PutawayTaskItemJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.PutawayTaskJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.ReceivingItemJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.ReceivingOrderJpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceivingOrderService {

    private final ReceivingOrderJpaRepository receivingOrderRepo;
    private final ReceivingItemJpaRepository receivingItemRepo;
    private final PutawayTaskJpaRepository putawayTaskRepo;
    private final PutawayTaskItemJpaRepository putawayTaskItemRepo;
    private final JdbcTemplate jdbcTemplate;

    // ─── List ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<List<ReceivingOrderResponse>> listOrders(String status) {
        List<ReceivingOrderEntity> orders = status != null && !status.isBlank()
                ? receivingOrderRepo.findByStatusOrderByCreatedAtDesc(status)
                : receivingOrderRepo.findAllByOrderByCreatedAtDesc();

        List<ReceivingOrderResponse> result = orders.stream()
                .map(o -> toResponse(o, false))
                .collect(Collectors.toList());

        return ApiResponse.success("OK", result);
    }

    @Transactional(readOnly = true)
    public ApiResponse<ReceivingOrderResponse> getOrder(Long id) {
        ReceivingOrderEntity order = findOrder(id);
        List<ReceivingItemEntity> items = receivingItemRepo.findByReceivingOrderReceivingId(id);
        ReceivingOrderResponse response = toResponse(order, false);
        response.setItems(items.stream().map(this::toItemResponse).collect(Collectors.toList()));
        return ApiResponse.success("OK", response);
    }

    // ─── Submit ────────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<ReceivingOrderResponse> submit(Long id, Long userId) {
        ReceivingOrderEntity order = findOrder(id);
        validateStatus(order, "DRAFT", "submit");

        order.setStatus("SUBMITTED");
        order.setUpdatedAt(LocalDateTime.now());
        receivingOrderRepo.save(order);

        log.info("GRN {} submitted by userId={}", order.getReceivingCode(), userId);
        return ApiResponse.success("GRN submitted successfully", toResponse(order, false));
    }

    // ─── Approve ───────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<ReceivingOrderResponse> approve(Long id, Long managerId) {
        ReceivingOrderEntity order = findOrder(id);
        validateStatus(order, "SUBMITTED", "approve");

        order.setStatus("APPROVED");
        order.setApprovedBy(managerId);
        order.setApprovedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        receivingOrderRepo.save(order);

        log.info("GRN {} approved by managerId={}", order.getReceivingCode(), managerId);
        return ApiResponse.success("GRN approved successfully", toResponse(order, false));
    }

    // ─── Post ──────────────────────────────────────────────────────────────────

    @Transactional
    public ApiResponse<ReceivingOrderResponse> post(Long id, Long accountantId) {
        ReceivingOrderEntity order = findOrder(id);
        validateStatus(order, "APPROVED", "post");

        // 1. Load line items
        List<ReceivingItemEntity> items = receivingItemRepo.findByReceivingOrderReceivingId(id);
        if (items.isEmpty()) {
            return ApiResponse.error("No items found in GRN " + id);
        }

        // 2. Create inventory_lots for each line + record inventory_transactions
        List<Long> lotIds = new ArrayList<>();
        for (ReceivingItemEntity item : items) {
            // 2a. Insert into inventory_lots
            Long lotId = jdbcTemplate.queryForObject(
                    "INSERT INTO inventory_lots (sku_id, lot_number, expiry_date, manufacture_date, qc_status, quarantine_status, receiving_item_id) "
                            +
                            "VALUES (?, ?, ?, ?, 'RELEASED', 'NONE', ?) " +
                            "ON CONFLICT (sku_id, lot_number) DO UPDATE SET receiving_item_id = EXCLUDED.receiving_item_id "
                            +
                            "RETURNING lot_id",
                    Long.class,
                    item.getSkuId(),
                    item.getLotNumber() != null ? item.getLotNumber() : "LOT-" + id,
                    item.getExpiryDate(),
                    item.getManufactureDate(),
                    item.getReceivingItemId());
            lotIds.add(lotId);

            // 2b. Upsert inventory_snapshot (staging area — location_id from first staging
            // location)
            Long stagingLocationId = getFirstStagingLocation(order.getWarehouseId());
            jdbcTemplate.update(
                    "INSERT INTO inventory_snapshot (warehouse_id, sku_id, lot_id, location_id, quantity, last_updated) "
                            +
                            "VALUES (?, ?, ?, ?, ?, NOW()) " +
                            "ON CONFLICT (warehouse_id, sku_id, lot_id_safe, location_id) " +
                            "DO UPDATE SET quantity = inventory_snapshot.quantity + EXCLUDED.quantity, last_updated = NOW()",
                    order.getWarehouseId(), item.getSkuId(), lotId, stagingLocationId, item.getReceivedQty());

            // 2c. Insert inventory_transaction
            jdbcTemplate.update(
                    "INSERT INTO inventory_transactions (warehouse_id, sku_id, lot_id, location_id, quantity, txn_type, reference_table, reference_id, created_by) "
                            +
                            "VALUES (?, ?, ?, ?, ?, 'RECEIVE', 'receiving_orders', ?, ?)",
                    order.getWarehouseId(), item.getSkuId(), lotId, stagingLocationId,
                    item.getReceivedQty(), id, accountantId);
        }

        // 3. Update order status → POSTED
        order.setStatus("POSTED");
        order.setConfirmedBy(accountantId);
        order.setConfirmedAt(LocalDateTime.now());
        order.setReceivedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        receivingOrderRepo.save(order);

        // 4. Create PutawayTask + PutawayTaskItems
        Long stagingLocationId = getFirstStagingLocation(order.getWarehouseId());
        PutawayTaskEntity task = PutawayTaskEntity.builder()
                .warehouseId(order.getWarehouseId())
                .receivingId(id)
                .status("OPEN")
                .fromLocationId(stagingLocationId)
                .createdBy(accountantId)
                .build();
        PutawayTaskEntity savedTask = putawayTaskRepo.save(task);

        for (int i = 0; i < items.size(); i++) {
            ReceivingItemEntity item = items.get(i);
            PutawayTaskItemEntity taskItem = PutawayTaskItemEntity.builder()
                    .putawayTask(savedTask)
                    .receivingItemId(item.getReceivingItemId())
                    .skuId(item.getSkuId())
                    .lotId(lotIds.get(i))
                    .quantity(item.getReceivedQty())
                    .putawayQty(BigDecimal.ZERO)
                    .suggestedLocationId(stagingLocationId)
                    .build();
            putawayTaskItemRepo.save(taskItem);
        }

        order.setPutawayCreatedAt(LocalDateTime.now());
        receivingOrderRepo.save(order);

        log.info("GRN {} posted by accountantId={}, putawayTaskId={}", order.getReceivingCode(), accountantId,
                savedTask.getPutawayTaskId());
        return ApiResponse.success("GRN posted successfully. Putaway task created: " + savedTask.getPutawayTaskId(),
                toResponse(order, false));
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private ReceivingOrderEntity findOrder(Long id) {
        return receivingOrderRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Receiving order not found: " + id));
    }

    private void validateStatus(ReceivingOrderEntity order, String expectedStatus, String action) {
        if (!expectedStatus.equals(order.getStatus())) {
            throw new RuntimeException(
                    "Cannot " + action + " GRN in status '" + order.getStatus() + "'. Expected: " + expectedStatus);
        }
    }

    private Long getFirstStagingLocation(Long warehouseId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT location_id FROM locations WHERE warehouse_id = ? AND is_staging = TRUE AND active = TRUE LIMIT 1",
                    Long.class, warehouseId);
        } catch (Exception e) {
            // Fallback: any location in warehouse
            return jdbcTemplate.queryForObject(
                    "SELECT location_id FROM locations WHERE warehouse_id = ? AND active = TRUE LIMIT 1",
                    Long.class, warehouseId);
        }
    }

    private ReceivingOrderResponse toResponse(ReceivingOrderEntity o, boolean withItems) {
        return ReceivingOrderResponse.builder()
                .receivingId(o.getReceivingId())
                .warehouseId(o.getWarehouseId())
                .receivingCode(o.getReceivingCode())
                .status(o.getStatus())
                .sourceType(o.getSourceType())
                .supplierId(o.getSupplierId())
                .sourceReferenceCode(o.getSourceReferenceCode())
                .note(o.getNote())
                .createdBy(o.getCreatedBy())
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt())
                .approvedBy(o.getApprovedBy())
                .approvedAt(o.getApprovedAt())
                .confirmedBy(o.getConfirmedBy())
                .confirmedAt(o.getConfirmedAt())
                .build();
    }

    private ReceivingItemResponse toItemResponse(ReceivingItemEntity item) {
        return ReceivingItemResponse.builder()
                .receivingItemId(item.getReceivingItemId())
                .skuId(item.getSkuId())
                .receivedQty(item.getReceivedQty())
                .lotNumber(item.getLotNumber())
                .expiryDate(item.getExpiryDate())
                .manufactureDate(item.getManufactureDate())
                .note(item.getNote())
                .build();
    }
}
