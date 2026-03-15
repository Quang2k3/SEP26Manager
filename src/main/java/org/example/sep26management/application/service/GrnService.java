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
    private final ReceivingOrderJpaRepository receivingOrderRepo;
    private final SupplierJpaRepository supplierRepo;
    private final InventoryLotJpaRepository inventoryLotRepo;
    private final InventoryTransactionJpaRepository inventoryTransactionRepo;
    private final InventorySnapshotJpaRepository inventorySnapshotRepo;

    public ApiResponse<PageResponse<GrnResponse>> listGrns(Long warehouseId, String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<GrnEntity> entityPage;
        if (warehouseId != null && status != null && !status.isBlank()) {
            entityPage = grnRepo.findByWarehouseIdAndStatusOrderByCreatedAtDesc(warehouseId, status, pageable);
        } else if (warehouseId != null) {
            entityPage = grnRepo.findByWarehouseIdOrderByCreatedAtDesc(warehouseId, pageable);
        } else if (status != null && !status.isBlank()) {
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
        // items đã được load trong toSummaryResponse
        return ApiResponse.success("GRN details retrieved", toSummaryResponse(grn));
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

        // Cập nhật ReceivingOrder status
        receivingOrderRepo.findById(grn.getReceivingId()).ifPresent(order -> {
            order.setStatus("GRN_APPROVED");
            receivingOrderRepo.save(order);
        });

        return ApiResponse.success("GRN approved.", toSummaryResponse(grn));
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

        // Cập nhật ReceivingOrder về GRN_CREATED để Keeper xem lại
        receivingOrderRepo.findById(grn.getReceivingId()).ifPresent(order -> {
            order.setStatus("GRN_CREATED");
            receivingOrderRepo.save(order);
        });

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

        // Lấy Staging Location — không bắt buộc, nếu không có thì để null
        Long stagingLocationId = getFirstStagingLocation(grn.getWarehouseId());
        // Không throw nếu thiếu staging — putaway task vẫn tạo được

        // Lấy danh sách items bên receiving để map receivingItemId sang putawayTaskItem
        List<ReceivingItemEntity> rcvItems = receivingItemRepo.findByReceivingOrderReceivingId(grn.getReceivingId());

        // Tạo Putaway Task
        PutawayTaskEntity task = PutawayTaskEntity.builder()
                .warehouseId(grn.getWarehouseId())
                .receivingId(grn.getReceivingId())
                .grnId(grn.getGrnId())
                .fromLocationId(stagingLocationId)
                .status("PENDING")
                .createdBy(userId)
                .build();
        task = putawayTaskRepo.save(task);

        for (GrnItemEntity item : items) {
            // Tìm receivingItem tương ứng (ưu tiên match theo SKU + lotNumber + expiryDate nếu có)
            ReceivingItemEntity matchedReceivingItem = rcvItems.stream()
                    .filter(r -> r.getSkuId().equals(item.getSkuId())
                            && java.util.Objects.equals(r.getLotNumber(), item.getLotNumber())
                            && java.util.Objects.equals(r.getExpiryDate(), item.getExpiryDate()))
                    .findFirst()
                    // fallback: match theo SKU nếu không tìm thấy bản ghi khớp lot/expiry
                    .orElseGet(() -> rcvItems.stream()
                            .filter(r -> r.getSkuId().equals(item.getSkuId()))
                            .findFirst()
                            .orElse(null));

            Long recItemId = matchedReceivingItem != null ? matchedReceivingItem.getReceivingItemId() : null;

            // 0. Tạo hoặc lấy InventoryLot cho dòng này (để bám FEFO theo lot/expiry)
            Long lotId = null;
            if (item.getLotNumber() != null && !item.getLotNumber().isBlank()) {
                Optional<InventoryLotEntity> lotOpt = inventoryLotRepo
                        .findBySkuIdAndLotNumberAndExpiryDate(
                                item.getSkuId(),
                                item.getLotNumber(),
                                item.getExpiryDate());

                InventoryLotEntity lot = lotOpt.orElseGet(() -> {
                    InventoryLotEntity newLot = InventoryLotEntity.builder()
                            .skuId(item.getSkuId())
                            .lotNumber(item.getLotNumber())
                            .manufactureDate(item.getManufactureDate())
                            .expiryDate(item.getExpiryDate())
                            .receivingItemId(recItemId)
                            .build();
                    return inventoryLotRepo.save(newLot);
                });
                lotId = lot.getLotId();
            }
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

            // 2. Cập nhật Snapshot (Sử dụng Native Upsert đã sửa) — bám theo lotId để phục vụ FEFO
            inventorySnapshotRepo.upsertInventory(
                    grn.getWarehouseId(),
                    item.getSkuId(),
                    lotId,
                    stagingLocationId,
                    item.getQuantity());

            // 3. Gợi ý Putaway (Suggest destination location)
            Long destLocationId = null;
            Optional<org.example.sep26management.application.dto.response.PutawaySuggestion> sug = putawaySuggestionService
                    .suggestLocation(grn.getWarehouseId(), item.getSkuId(), item.getQuantity());
            if (sug.isPresent()) {
                destLocationId = sug.get().getSuggestedLocationId();
            }

            // 4. Sinh Putaway Task Item
            PutawayTaskItemEntity taskItem = PutawayTaskItemEntity.builder()
                    .putawayTask(task)
                    .receivingItemId(recItemId)
                    .skuId(item.getSkuId())
                    .lotId(lotId)
                    .quantity(item.getQuantity())
                    .putawayQty(java.math.BigDecimal.ZERO)
                    .suggestedLocationId(destLocationId)
                    .build();
            putawayTaskItemRepo.save(taskItem);
        }

        grn.setStatus("POSTED");
        grnRepo.save(grn);

        // Cập nhật ReceivingOrder status → POSTED
        receivingOrderRepo.findById(grn.getReceivingId()).ifPresent(order -> {
            order.setStatus("POSTED");
            receivingOrderRepo.save(order);
        });

        return ApiResponse.success("GRN posted, Putaway task created successfully.", toSummaryResponse(grn));
    }

    /**
     * Keeper gọi khi bấm "Gửi Manager duyệt"
     * - GRN phải đang ở PENDING_APPROVAL (đã được tạo đúng status)
     * - Cập nhật ReceivingOrder.status = "PENDING_APPROVAL" để FE hiển thị đúng
     */
    @Transactional
    public ApiResponse<GrnResponse> submitToManager(Long grnId) {
        GrnEntity grn = findGrn(grnId);
        if (!"PENDING_APPROVAL".equals(grn.getStatus())) {
            throw new RuntimeException("GRN is not in PENDING_APPROVAL status: " + grn.getStatus());
        }
        // Cập nhật ReceivingOrder status để FE hiển thị đúng
        receivingOrderRepo.findById(grn.getReceivingId()).ifPresent(order -> {
            order.setStatus("PENDING_APPROVAL");
            receivingOrderRepo.save(order);
        });
        return ApiResponse.success("GRN submitted to manager for approval", toSummaryResponse(grn));
    }

    public ApiResponse<GrnResponse> getByReceivingId(Long receivingId) {
        List<GrnEntity> grns = grnRepo.findByReceivingIdOrderByCreatedAtDesc(receivingId);
        if (grns.isEmpty()) {
            throw new RuntimeException("No GRN found for receiving order: " + receivingId);
        }
        return ApiResponse.success("OK", toSummaryResponse(grns.get(0)));
    }

    private GrnEntity findGrn(Long id) {
        return grnRepo.findById(id).orElseThrow(() -> new RuntimeException("GRN not found: " + id));
    }

    private GrnResponse toSummaryResponse(GrnEntity grn) {
        // Load items cho mọi response — FE cần để hiển thị số lượng SKU/thùng
        List<GrnItemEntity> itemEntities = grnItemRepo.findByGrnGrnId(grn.getGrnId());
        List<GrnItemResponse> items = itemEntities.stream().map(gi -> {
            String skuCode = skuRepo.findById(gi.getSkuId())
                    .map(s -> s.getSkuCode()).orElse("SKU-" + gi.getSkuId());
            String skuName = skuRepo.findById(gi.getSkuId())
                    .map(s -> s.getSkuName()).orElse("");
            return GrnItemResponse.builder()
                    .grnItemId(gi.getGrnItemId())
                    .skuId(gi.getSkuId())
                    .skuCode(skuCode)
                    .skuName(skuName)
                    .quantity(gi.getQuantity())
                    .lotNumber(gi.getLotNumber())
                    .manufactureDate(gi.getManufactureDate())
                    .expiryDate(gi.getExpiryDate())
                    .build();
        }).collect(java.util.stream.Collectors.toList());

        return GrnResponse.builder()
                .grnId(grn.getGrnId())
                .grnCode(grn.getGrnCode())
                .receivingId(grn.getReceivingId())
                .warehouseId(grn.getWarehouseId())
                .sourceType(grn.getSourceType())
                .sourceReferenceCode(grn.getSourceReferenceCode())
                .status(grn.getStatus())
                .createdBy(grn.getCreatedBy())
                .createdAt(grn.getCreatedAt())
                .updatedAt(grn.getUpdatedAt())
                .approvedBy(grn.getApprovedBy())
                .approvedAt(grn.getApprovedAt())
                .note(grn.getNote())
                .supplierId(grn.getSupplierId())
                .supplierName(grn.getSupplierId() != null
                        ? supplierRepo.findById(grn.getSupplierId())
                        .map(s -> s.getSupplierName()).orElse(null)
                        : null)
                .items(items)
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