package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.GrnItemResponse;
import org.example.sep26management.application.dto.response.GrnResponse;
import org.example.sep26management.application.dto.response.PageResponse;
import org.example.sep26management.infrastructure.persistence.entity.*;
import org.example.sep26management.infrastructure.persistence.repository.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GrnService {

    private final GrnJpaRepository grnRepo;
    private final GrnItemJpaRepository grnItemRepo;
    private final SkuJpaRepository skuRepo;
    private final PutawayTaskJpaRepository putawayTaskRepo;
    private final PutawayTaskItemJpaRepository putawayTaskItemRepo;
    private final PutawaySuggestionService putawaySuggestionService;
    private final JdbcTemplate jdbcTemplate;
    private final ReceivingItemJpaRepository receivingItemRepo;
    private final InventoryTransactionJpaRepository inventoryTransactionRepo;
    private final InventorySnapshotJpaRepository inventorySnapshotRepo;

    public ApiResponse<PageResponse<GrnResponse>> listGrns(String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<GrnEntity> entityPage;
        if (status != null && !status.isBlank()) {
            entityPage = grnRepo.findByStatus(status, pageable);
        } else {
            entityPage = grnRepo.findAll(pageable);
        }

        List<GrnResponse> list = entityPage.getContent().stream()
                .map(this::toSummaryResponse)
                .collect(Collectors.toList());

        PageResponse<GrnResponse> res = PageResponse.<GrnResponse>builder()
                .totalElements(entityPage.getTotalElements())
                .totalPages(entityPage.getTotalPages())
                .page(entityPage.getNumber())
                .size(entityPage.getSize())
                .content(list)
                .build();
        return ApiResponse.success("Fetched GRNs successfully", res);
    }

    public ApiResponse<GrnResponse> getGrn(Long id) {
        GrnEntity grn = findGrn(id);
        List<GrnItemEntity> items = grnItemRepo.findByGrnGrnId(id);

        GrnResponse res = toSummaryResponse(grn);
        res.setItems(items.stream().map(gi -> {
            String skuCode = null;
            String skuName = null;
            SkuEntity sku = skuRepo.findById(gi.getSkuId()).orElse(null);
            if (sku != null) {
                skuCode = sku.getSkuCode();
                skuName = sku.getSkuName();
            }
            return GrnItemResponse.builder()
                    .grnItemId(gi.getGrnItemId())
                    .skuId(gi.getSkuId())
                    .skuCode(skuCode)
                    .skuName(skuName)
                    .quantity(gi.getQuantity())
                    .build();
        }).collect(Collectors.toList()));

        return ApiResponse.success("GRN details retrieved", res);
    }

    @Transactional
    public ApiResponse<GrnResponse> approve(Long id, Long managerId) {
        GrnEntity grn = findGrn(id);
        if (!"PENDING_APPROVAL".equals(grn.getStatus())) {
            throw new RuntimeException("Cannot approve GRN in status " + grn.getStatus());
        }
        grn.setStatus("APPROVED");
        grn.setApprovedBy(managerId);
        grn.setApprovedAt(LocalDateTime.now());
        grnRepo.save(grn);

        // Automated post upon manual approval by Manager
        this.post(id, managerId);

        return ApiResponse.success("GRN approved and posted successfully.", toSummaryResponse(grn));
    }

    @Transactional
    public ApiResponse<GrnResponse> reject(Long id, String reason, Long managerId) {
        GrnEntity grn = findGrn(id);
        if (!"PENDING_APPROVAL".equals(grn.getStatus())) {
            throw new RuntimeException("Cannot reject GRN in status " + grn.getStatus());
        }
        grn.setStatus("REJECTED");
        grn.setNote((grn.getNote() == null ? "" : grn.getNote() + " | ") + "Rejected by " + managerId + ": " + reason);
        grnRepo.save(grn);
        return ApiResponse.success("GRN rejected", toSummaryResponse(grn));
    }

    @Transactional
    public ApiResponse<GrnResponse> post(Long id, Long userId) {
        GrnEntity grn = findGrn(id);
        if (!"APPROVED".equals(grn.getStatus())) {
            throw new RuntimeException("Cannot POST GRN in status " + grn.getStatus());
        }

        List<GrnItemEntity> items = grnItemRepo.findByGrnGrnId(id);
        if (items.isEmpty()) {
            throw new RuntimeException("GRN has no items to post");
        }

        // Lấy Staging Location
        Long stagingLocationId = getFirstStagingLocation(grn.getWarehouseId());
        if (stagingLocationId == null) {
            throw new RuntimeException("No Staging location found for warehouse " + grn.getWarehouseId());
        }

        // Lấy danh sách items bên receiving để map receivingItemId sang putawayTaskItem
        List<ReceivingItemEntity> rcvItems = receivingItemRepo.findByReceivingOrderReceivingId(grn.getReceivingId());

        // Tạo Putaway Task
        PutawayTaskEntity task = PutawayTaskEntity.builder()
                .warehouseId(grn.getWarehouseId())
                .receivingId(grn.getReceivingId())
                .fromLocationId(stagingLocationId)
                .status("PENDING")
                .createdBy(userId)
                .build();
        task = putawayTaskRepo.save(task);

        for (GrnItemEntity item : items) {
            // 1. Ghi log Transaction (Nhập kho vào staging)
            InventoryTransactionEntity tx = InventoryTransactionEntity.builder()
                    .warehouseId(grn.getWarehouseId())
                    .locationId(stagingLocationId)
                    .skuId(item.getSkuId())
                    .txnType("RECEIVING")
                    .quantity(item.getQuantity())
                    .referenceTable("GRN")
                    .referenceId(id)
                    .createdBy(userId)
                    .build();
            inventoryTransactionRepo.save(tx);

            // 2. Cập nhật Snapshot (Sử dụng Native Upsert đã sửa)
            inventorySnapshotRepo.upsertInventory(
                    grn.getWarehouseId(),
                    item.getSkuId(),
                    null, // lotId = null cho luồng cơ bản
                    stagingLocationId,
                    item.getQuantity());

            // 3. Gợi ý Putaway (Suggest destination location)
            Long destLocationId = null;
            Optional<org.example.sep26management.application.dto.response.PutawaySuggestion> sug = putawaySuggestionService
                    .suggestLocation(grn.getWarehouseId(), item.getSkuId(), item.getQuantity());
            if (sug.isPresent()) {
                destLocationId = sug.get().getSuggestedLocationId();
            }

            // Tìm receivingItemId tương ứng
            Long recItemId = rcvItems.stream()
                    .filter(r -> r.getSkuId().equals(item.getSkuId()))
                    .map(ReceivingItemEntity::getReceivingItemId)
                    .findFirst()
                    .orElse(-1L);

            // 4. Sinh Putaway Task Item
            PutawayTaskItemEntity taskItem = PutawayTaskItemEntity.builder()
                    .putawayTask(task)
                    .receivingItemId(recItemId)
                    .skuId(item.getSkuId())
                    .quantity(item.getQuantity())
                    .suggestedLocationId(destLocationId)
                    .build();
            putawayTaskItemRepo.save(taskItem);
        }

        grn.setStatus("POSTED");
        grnRepo.save(grn);

        return ApiResponse.success("GRN posted, Putaway task created successfully.", toSummaryResponse(grn));
    }

    private GrnEntity findGrn(Long id) {
        return grnRepo.findById(id).orElseThrow(() -> new RuntimeException("GRN not found: " + id));
    }

    private GrnResponse toSummaryResponse(GrnEntity grn) {
        return GrnResponse.builder()
                .grnId(grn.getGrnId())
                .grnCode(grn.getGrnCode())
                .receivingId(grn.getReceivingId())
                .warehouseId(grn.getWarehouseId())
                .sourceType(grn.getSourceType())
                .sourceWarehouseId(grn.getSourceWarehouseId())
                .supplierId(grn.getSupplierId())
                .sourceReferenceCode(grn.getSourceReferenceCode())
                .status(grn.getStatus())
                .createdBy(grn.getCreatedBy())
                .createdAt(grn.getCreatedAt())
                .updatedAt(grn.getUpdatedAt())
                .approvedBy(grn.getApprovedBy())
                .approvedAt(grn.getApprovedAt())
                .note(grn.getNote())
                .build();
    }

    private Long getFirstStagingLocation(Long warehouseId) {
        try {
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT location_id FROM locations WHERE warehouse_id = ? AND is_staging = TRUE AND active = TRUE LIMIT 1",
                    Long.class, warehouseId);
            if (!ids.isEmpty())
                return ids.get(0);

            ids = jdbcTemplate.queryForList(
                    "SELECT location_id FROM locations WHERE warehouse_id = ? AND active = TRUE LIMIT 1",
                    Long.class, warehouseId);
            if (!ids.isEmpty())
                return ids.get(0);

            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
