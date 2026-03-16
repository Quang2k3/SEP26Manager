package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.GrnItemResponse;
import org.example.sep26management.application.dto.response.GrnResponse;
import org.example.sep26management.application.dto.response.PageResponse;
import org.example.sep26management.infrastructure.exception.BusinessException;
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
        return ApiResponse.success("GRN details retrieved", toSummaryResponse(grn));
    }

    @Transactional
    public ApiResponse<GrnResponse> approve(Long id, Long managerId) {
        GrnEntity grn = findGrn(id);
        if (!"PENDING_APPROVAL".equals(grn.getStatus())) {
            throw new BusinessException("Không thể duyệt GRN đang ở trạng thái: " + grn.getStatus());
        }
        grn.setStatus("APPROVED");
        grn.setApprovedBy(managerId);
        grn.setApprovedAt(LocalDateTime.now());
        grnRepo.save(grn);

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
            throw new BusinessException("Không thể từ chối GRN đang ở trạng thái: " + grn.getStatus());
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("Lý do từ chối không được để trống");
        }
        grn.setStatus("REJECTED");
        grn.setNote((grn.getNote() == null ? "" : grn.getNote() + " | ") + "Rejected by " + managerId + ": " + reason);
        grnRepo.save(grn);

        receivingOrderRepo.findById(grn.getReceivingId()).ifPresent(order -> {
            order.setStatus("GRN_CREATED");
            receivingOrderRepo.save(order);
        });

        return ApiResponse.success("GRN rejected", toSummaryResponse(grn));
    }

    @Transactional
    public ApiResponse<GrnResponse> post(Long id, Long userId) {
        GrnEntity grn = findGrn(id);

        // Guard: chỉ APPROVED mới được post
        if (!"APPROVED".equals(grn.getStatus())) {
            throw new BusinessException("Không thể nhập kho GRN đang ở trạng thái: " + grn.getStatus()
                    + ". Chỉ GRN đã được duyệt (APPROVED) mới được nhập kho.");
        }

        List<GrnItemEntity> items = grnItemRepo.findByGrnGrnId(id);
        if (items.isEmpty()) {
            throw new BusinessException("GRN không có dòng hàng nào để nhập kho.");
        }

        // BUG FIX 1: stagingLocationId có thể null nếu warehouse chưa có staging location
        // -> locationId NOT NULL trong inventory_transactions -> DataIntegrityViolationException
        Long stagingLocationId = getFirstStagingLocation(grn.getWarehouseId());
        if (stagingLocationId == null) {
            throw new BusinessException(
                    "Kho #" + grn.getWarehouseId() + " chưa có vị trí Staging. "
                            + "Vui lòng tạo ít nhất 1 location với is_staging=true trước khi nhập kho.");
        }

        List<ReceivingItemEntity> rcvItems =
                receivingItemRepo.findByReceivingOrderReceivingId(grn.getReceivingId());

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
            // Match receiving item by SKU + lot + expiry, fallback to SKU only
            ReceivingItemEntity matchedReceivingItem = rcvItems.stream()
                    .filter(r -> r.getSkuId().equals(item.getSkuId())
                            && java.util.Objects.equals(r.getLotNumber(), item.getLotNumber())
                            && java.util.Objects.equals(r.getExpiryDate(), item.getExpiryDate()))
                    .findFirst()
                    .orElseGet(() -> rcvItems.stream()
                            .filter(r -> r.getSkuId().equals(item.getSkuId()))
                            .findFirst()
                            .orElse(null));

            // BUG FIX 3: recItemId = null -> receiving_item_id NOT NULL violation
            // Throw BusinessException ro rang thay vi de DB throw constraint error
            if (matchedReceivingItem == null) {
                throw new BusinessException(
                        "Không tìm thấy receiving item cho SKU " + item.getSkuId()
                                + " trong phiếu nhận hàng #" + grn.getReceivingId()
                                + ". Vui lòng kiểm tra lại phiếu nhận hàng.");
            }
            Long recItemId = matchedReceivingItem.getReceivingItemId();

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

            inventorySnapshotRepo.upsertInventory(
                    grn.getWarehouseId(),
                    item.getSkuId(),
                    lotId,
                    stagingLocationId,
                    item.getQuantity());

            Long destLocationId = null;
            Optional<org.example.sep26management.application.dto.response.PutawaySuggestion> sug =
                    putawaySuggestionService.suggestLocation(grn.getWarehouseId(), item.getSkuId(), item.getQuantity());
            if (sug.isPresent()) {
                destLocationId = sug.get().getSuggestedLocationId();
            }

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

        receivingOrderRepo.findById(grn.getReceivingId()).ifPresent(order -> {
            order.setStatus("POSTED");
            receivingOrderRepo.save(order);
        });

        return ApiResponse.success("GRN posted, Putaway task created successfully.", toSummaryResponse(grn));
    }

    @Transactional
    public ApiResponse<GrnResponse> submitToManager(Long grnId) {
        GrnEntity grn = findGrn(grnId);
        // BUG-04 FIX: GRN mới tạo mang status "GRN_CREATED", không phải "PENDING_APPROVAL"
        // Guard cũ kiểm tra sai → luôn throw, không submit được
        if (!"GRN_CREATED".equals(grn.getStatus())) {
            throw new BusinessException("GRN không ở trạng thái GRN_CREATED: " + grn.getStatus()
                    + ". Chỉ có thể submit GRN vừa tạo xong.");
        }
        // Cập nhật GRN → PENDING_APPROVAL
        grn.setStatus("PENDING_APPROVAL");
        grnRepo.save(grn);

        receivingOrderRepo.findById(grn.getReceivingId()).ifPresent(order -> {
            order.setStatus("PENDING_APPROVAL");
            receivingOrderRepo.save(order);
        });
        return ApiResponse.success("GRN submitted to manager for approval", toSummaryResponse(grn));
    }

    public ApiResponse<GrnResponse> getByReceivingId(Long receivingId) {
        List<GrnEntity> grns = grnRepo.findByReceivingIdOrderByCreatedAtDesc(receivingId);
        if (grns.isEmpty()) {
            throw new BusinessException("Chưa có GRN cho đơn nhập kho này (receivingId=" + receivingId + ")");
        }
        return ApiResponse.success("OK", toSummaryResponse(grns.get(0)));
    }

    private GrnEntity findGrn(Long id) {
        return grnRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy GRN với id=" + id));
    }

    private GrnResponse toSummaryResponse(GrnEntity grn) {
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