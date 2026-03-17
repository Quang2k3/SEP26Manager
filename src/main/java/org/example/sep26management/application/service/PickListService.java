package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.MessageConstants;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRUM-511: UC-WXE-06 Generate Pick List
 * BR-WXE-22: only from allocated stock
 * BR-WXE-23: optimal picking route (zone → location code order)
 * BR-WXE-24: SKU, lot, location traceability
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

    @Transactional
    public ApiResponse<PickListResponse> generatePickList(
            GeneratePickListRequest request,
            Long userId, String ip, String ua) {

        log.info("Generating pick list for documentId={}, type={}", request.getDocumentId(), request.getOrderType());

        Long warehouseId;
        String documentCode;
        String refTable;

        // Validate document exists and is APPROVED
        if (request.getOrderType() == OutboundType.SALES_ORDER) {
            SalesOrderEntity so = soRepository.findById(request.getDocumentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            String.format(MessageConstants.OUTBOUND_NOT_FOUND, request.getDocumentId())));

            if (!"APPROVED".equals(so.getStatus()) && !"ALLOCATED".equals(so.getStatus())) {
                throw new BusinessException(MessageConstants.PICKLIST_MUST_BE_ALLOCATED);
            }
            warehouseId = so.getWarehouseId();
            documentCode = so.getSoCode();
            refTable = "sales_orders";
        } else {
            TransferEntity transfer = transferRepository.findById(request.getDocumentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            String.format(MessageConstants.OUTBOUND_NOT_FOUND, request.getDocumentId())));

            if (!"APPROVED".equals(transfer.getStatus())) {
                throw new BusinessException(MessageConstants.PICKLIST_MUST_BE_ALLOCATED);
            }
            warehouseId = transfer.getFromWarehouseId();
            documentCode = transfer.getTransferCode();
            refTable = "transfers";
        }

        // BR-WXE-22: fetch OPEN reservations for this document (allocated stock)
        List<ReservationEntity> reservations = revReservationQueryRepository
                .findByReferenceTableAndReferenceIdAndStatus(refTable, request.getDocumentId(), "OPEN");

        if (reservations.isEmpty()) {
            throw new BusinessException(MessageConstants.PICKLIST_NO_ALLOCATION);
        }

        // Invalidate existing pick tasks for this document (BR-WXE alternative 4b)
        List<PickingTaskEntity> existing = pickingTaskRepository
                .findByWarehouseIdAndSoId(warehouseId, request.getDocumentId());
        existing.forEach(t -> {
            t.setStatus("CANCELLED");
            pickingTaskRepository.save(t);
        });

        // Create new picking task
        String taskCode = generatePickTaskCode(warehouseId);
        PickingTaskEntity task = PickingTaskEntity.builder()
                .warehouseId(warehouseId)
                .soId(request.getOrderType() == OutboundType.SALES_ORDER ? request.getDocumentId() : null)
                .status("OPEN")
                .priority(3)
                .assignedTo(request.getAssignedTo())
                .build();
        PickingTaskEntity savedTask = pickingTaskRepository.save(task);

        // Build pick items from reservations
        // The locationMap is not used directly here since each item resolves its location via resolveLocationForReservation
        // Remove the broken locationMap that incorrectly used lotId as locationId

        List<PickingTaskItemEntity> taskItems = new ArrayList<>();
        List<PickListResponse.PickListItem> pickItems = new ArrayList<>();

        for (ReservationEntity res : reservations) {
            PickingTaskItemEntity item = PickingTaskItemEntity.builder()
                    .pickingTaskId(savedTask.getPickingTaskId())
                    .skuId(res.getSkuId())
                    .lotId(res.getLotId())
                    .fromLocationId(resolveLocationForReservation(res, warehouseId))
                    .requiredQty(res.getQuantity())
                    .pickedQty(java.math.BigDecimal.ZERO)
                    .build();
            taskItems.add(item);
        }

        List<PickingTaskItemEntity> savedItems = pickingTaskItemRepository.saveAll(taskItems);

        // Build response with traceability — BR-WXE-24
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

            String skuCode = skuRepository.findById(item.getSkuId()).map(s -> s.getSkuCode()).orElse(null);
            String skuName = skuRepository.findById(item.getSkuId()).map(s -> s.getSkuName()).orElse(null);
            String lotNumber = null;
            java.time.LocalDate expiryDate = null;
            if (item.getLotId() != null) {
                var lot = lotRepository.findById(item.getLotId()).orElse(null);
                if (lot != null) {
                    lotNumber = lot.getLotNumber();
                    expiryDate = lot.getExpiryDate();
                }
            }

            responseItems.add(PickListResponse.PickListItem.builder()
                    .sequence(seq++)
                    .pickingTaskItemId(item.getPickingTaskItemId())
                    .locationId(item.getFromLocationId())
                    .locationCode(loc != null ? loc.getLocationCode() : null)
                    .zoneCode(zoneCode)
                    .rackCode(rackCode)
                    .skuId(item.getSkuId()).skuCode(skuCode).skuName(skuName)
                    .lotId(item.getLotId()).lotNumber(lotNumber).expiryDate(expiryDate)
                    .requiredQty(item.getRequiredQty())
                    .pickedQty(item.getPickedQty())
                    .status("PENDING")
                    .build());
        }

        // BR-WXE-23: sort by optimal picking route — zone → location code
        responseItems.sort(Comparator
                .comparing((PickListResponse.PickListItem r) -> r.getZoneCode() != null ? r.getZoneCode() : "")
                .thenComparing(r -> r.getLocationCode() != null ? r.getLocationCode() : ""));

        // Re-sequence after sort
        for (int i = 0; i < responseItems.size(); i++) {
            responseItems.get(i).setSequence(i + 1);
        }

        // ── Update SO status → PICKING ────────────────────────────────────────────
        if (request.getOrderType() == OutboundType.SALES_ORDER) {
            soRepository.findById(request.getDocumentId()).ifPresent(so -> {
                so.setStatus("PICKING");
                soRepository.save(so);
                log.info("SO {} status → PICKING after pick list generated", so.getSoCode());
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
                    ? zoneRepository.findById(loc.getZoneId()).map(z -> z.getZoneCode()).orElse(null)
                    : null;
            String skuCode = skuRepository.findById(item.getSkuId()).map(s -> s.getSkuCode()).orElse(null);
            String skuName = skuRepository.findById(item.getSkuId()).map(s -> s.getSkuName()).orElse(null);
            String lotNumber = null;
            java.time.LocalDate expiryDate = null;
            if (item.getLotId() != null) {
                var lot = lotRepository.findById(item.getLotId()).orElse(null);
                if (lot != null) { lotNumber = lot.getLotNumber(); expiryDate = lot.getExpiryDate(); }
            }

            responseItems.add(PickListResponse.PickListItem.builder()
                    .sequence(seq++).pickingTaskItemId(item.getPickingTaskItemId())
                    .locationId(item.getFromLocationId())
                    .locationCode(loc != null ? loc.getLocationCode() : null)
                    .zoneCode(zoneCode)
                    .skuId(item.getSkuId()).skuCode(skuCode).skuName(skuName)
                    .lotId(item.getLotId()).lotNumber(lotNumber).expiryDate(expiryDate)
                    .requiredQty(item.getRequiredQty()).pickedQty(item.getPickedQty())
                    .status(item.getPickedQty().compareTo(item.getRequiredQty()) >= 0 ? "PICKED" : "PENDING")
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
                .items(responseItems)
                .build());
    }


    /**
     * Keeper xác nhận đã lấy đủ hàng → picking task OPEN/IN_PROGRESS → PICKED.
     * Bắt buộc trước khi QC có thể gọi start-qc (yêu cầu task PICKED).
     */
    @Transactional
    public ApiResponse<PickListResponse> confirmPicked(Long taskId, Long userId, String ip, String ua) {
        log.info("confirmPicked: taskId={}, userId={}", taskId, userId);

        PickingTaskEntity task = pickingTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(MessageConstants.PICKLIST_NOT_FOUND, taskId)));

        if (!("OPEN".equals(task.getStatus()) || "IN_PROGRESS".equals(task.getStatus()))) {
            throw new BusinessException(
                    "Không thể xác nhận: task phải ở OPEN hoặc IN_PROGRESS. Hiện: " + task.getStatus());
        }

        // Set pickedQty = requiredQty cho item chưa được đánh dấu
        List<PickingTaskItemEntity> items = pickingTaskItemExtendedRepository.findByPickingTaskId(taskId);
        for (PickingTaskItemEntity item : items) {
            if (item.getPickedQty() == null || item.getPickedQty().compareTo(java.math.BigDecimal.ZERO) == 0) {
                item.setPickedQty(item.getRequiredQty());
                pickingTaskItemRepository.save(item);
            }
        }

        task.setStatus("PICKED");
        if (task.getStartedAt() == null) task.setStartedAt(java.time.LocalDateTime.now());
        pickingTaskRepository.save(task);

        // Update SO status → QC_SCAN
        if (task.getSoId() != null) {
            soRepository.findById(task.getSoId()).ifPresent(so -> {
                so.setStatus("QC_SCAN");
                soRepository.save(so);
                log.info("SO {} status → QC_SCAN after confirm picked", so.getSoCode());
            });
        }

        auditLogService.logAction(userId, "PICKING_CONFIRMED", "picking_tasks", taskId,
                "Pick task " + taskId + " confirmed PICKED by keeper", ip, ua);

        log.info("confirmPicked OK: taskId={} → PICKED", taskId);
        return getPickList(taskId);
    }

    /**
     * Lấy pick list active theo documentId (soId).
     * Dùng khi FE mở lại modal và không có taskId trong state.
     */
    @Transactional(readOnly = true)
    public ApiResponse<PickListResponse> getPickListByDocument(Long documentId, Long warehouseId) {
        List<PickingTaskEntity> tasks = pickingTaskRepository
                .findByWarehouseIdAndSoId(warehouseId, documentId);

        // Lấy task active nhất (không phải CANCELLED/COMPLETED)
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
        // Find the location that has this reservation's stock
        // Query snapshot for the specific sku+lot combination
        return revReservationQueryRepository.findLocationForReservation(
                warehouseId, res.getSkuId(), res.getLotId());
    }

    private String generatePickTaskCode(Long warehouseId) {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        long count = pickingTaskRepository.countTodayByWarehouse(
                warehouseId,
                start,
                end
        );
        return String.format("PKL-%s-%04d", date, count);
    }
}