package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.response.*;
import org.example.sep26management.application.enums.OutboundType;
import org.example.sep26management.infrastructure.persistence.entity.SalesOrderEntity;
import org.example.sep26management.infrastructure.persistence.entity.TransferEntity;
import org.example.sep26management.infrastructure.persistence.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboundListService {

    private final OutboundQueryRepository soQueryRepository;
    private final TransferJpaRepository transferRepository;
    private final SalesOrderItemJpaRepository soItemRepository;
    private final TransferItemJpaRepository transferItemRepository;
    private final CustomerJpaRepository customerRepository;
    private final WarehouseJpaRepository warehouseRepository;

    @Transactional(readOnly = true)
    public ApiResponse<PageResponse<OutboundListResponse>> listOutbound(
            Long warehouseId,
            String status,
            OutboundType orderType,
            String keyword,
            Long createdBy,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            Long currentUserId,
            String currentUserRole,
            int page, int size) {

        if (size <= 0) size = 20;
        log.info("listOutbound: warehouseId={}, status={}, orderType={}", warehouseId, status, orderType);

        if (warehouseId == null) {
            log.warn("listOutbound: warehouseId is null");
            return emptyPage(page, size);
        }

        List<OutboundListResponse> combined = new ArrayList<>();
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasStatus  = status  != null && !status.isBlank();

        // ── SALES ORDERS ────────────────────────────────────────────────────────
        if (orderType == null || orderType == OutboundType.SALES_ORDER) {
            List<SalesOrderEntity> soList = fetchSalesOrders(warehouseId, status, keyword, hasStatus, hasKeyword);
            for (SalesOrderEntity so : soList) {
                try {
                    combined.add(buildSoResponse(so, currentUserId, currentUserRole));
                } catch (Exception e) {
                    log.warn("Skip SO {}: {}", so.getSoId(), e.getMessage());
                }
            }
        }

        // ── INTERNAL TRANSFERS ──────────────────────────────────────────────────
        if (orderType == null || orderType == OutboundType.INTERNAL_TRANSFER) {
            List<TransferEntity> transfers = fetchTransfers(warehouseId, status, hasStatus);
            for (TransferEntity t : transfers) {
                try {
                    if (hasKeyword && !t.getTransferCode().toLowerCase().contains(keyword.toLowerCase())) continue;
                    combined.add(buildTransferResponse(t, currentUserId, currentUserRole));
                } catch (Exception e) {
                    log.warn("Skip Transfer {}: {}", t.getTransferId(), e.getMessage());
                }
            }
        }

        combined.sort((a, b) -> {
            if (a.getCreatedAt() == null) return 1;
            if (b.getCreatedAt() == null) return -1;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });

        int total = combined.size();
        int start = Math.min(page * size, total);
        int end   = Math.min(start + size, total);

        log.info("listOutbound OK: total={}, returned={}", total, end - start);

        return ApiResponse.success(
                total == 0 ? "Không có lệnh xuất kho nào." : "Danh sách lệnh xuất kho.",
                PageResponse.<OutboundListResponse>builder()
                        .content(combined.subList(start, end))
                        .page(page).size(size)
                        .totalElements(total)
                        .totalPages(size > 0 ? (int) Math.ceil((double) total / size) : 0)
                        .last(end >= total)
                        .build());
    }

    @Transactional(readOnly = true)
    public ApiResponse<OutboundSummaryResponse> getSummary(Long warehouseId) {
        if (warehouseId == null) {
            return ApiResponse.success("OK", OutboundSummaryResponse.builder().build());
        }
        long draft      = safeCount(warehouseId, "DRAFT");
        long pending    = safeCount(warehouseId, "PENDING_APPROVAL");
        long approved   = safeCount(warehouseId, "APPROVED");
        long allocated  = safeCount(warehouseId, "ALLOCATED");
        long picking    = safeCount(warehouseId, "PICKING");
        long qcScan     = safeCount(warehouseId, "QC_SCAN");
        long dispatched = safeCount(warehouseId, "DISPATCHED");
        long rejected   = safeCount(warehouseId, "REJECTED");

        return ApiResponse.success("OK", OutboundSummaryResponse.builder()
                .total(draft + pending + approved + allocated + picking + qcScan + dispatched + rejected)
                .draft(draft).pendingApproval(pending).approved(approved)
                .allocated(allocated).picking(picking).qcScan(qcScan)
                .dispatched(dispatched).rejected(rejected)
                .build());
    }

    private List<SalesOrderEntity> fetchSalesOrders(Long warehouseId, String status, String keyword,
                                                    boolean hasStatus, boolean hasKeyword) {
        try {
            if (hasStatus && hasKeyword)
                return soQueryRepository.findByWarehouseIdAndStatusAndSoCodeContainingIgnoreCaseOrderByCreatedAtDesc(warehouseId, status, keyword);
            if (hasStatus)
                return soQueryRepository.findByWarehouseIdAndStatusOrderByCreatedAtDesc(warehouseId, status);
            if (hasKeyword)
                return soQueryRepository.findByWarehouseIdAndSoCodeContainingIgnoreCaseOrderByCreatedAtDesc(warehouseId, keyword);
            return soQueryRepository.findByWarehouseIdOrderByCreatedAtDesc(warehouseId);
        } catch (Exception e) {
            log.error("fetchSalesOrders failed: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<TransferEntity> fetchTransfers(Long warehouseId, String status, boolean hasStatus) {
        try {
            if (hasStatus)
                return transferRepository.findByFromWarehouseIdAndStatus(warehouseId, status);
            return transferRepository.findByFromWarehouseIdOrderByCreatedAtDesc(warehouseId);
        } catch (Exception e) {
            log.error("fetchTransfers failed: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private OutboundListResponse buildSoResponse(SalesOrderEntity so, Long currentUserId, String currentUserRole) {
        int itemCount = soItemRepository.findBySoId(so.getSoId()).size();
        String destination = resolveCustomerName(so.getCustomerId());
        return OutboundListResponse.builder()
                .documentId(so.getSoId())
                .documentCode(so.getSoCode())
                .orderType(OutboundType.SALES_ORDER)
                .destination(destination)
                .status(so.getStatus())
                .warehouseId(so.getWarehouseId())
                .totalItems(itemCount)
                .createdBy(so.getCreatedBy())
                .createdAt(so.getCreatedAt())
                .shipmentDate(so.getRequiredShipDate())
                .canEdit(isDraft(so.getStatus())    && owns(so.getCreatedBy(), currentUserId))
                .canDelete(isDraft(so.getStatus())  && owns(so.getCreatedBy(), currentUserId))
                .canSubmit(isDraft(so.getStatus())  && owns(so.getCreatedBy(), currentUserId))
                .canApprove(isPending(so.getStatus()) && isManager(currentUserRole))
                .canConfirm(isApproved(so.getStatus()) && isKeeper(currentUserRole))
                .build();
    }

    private OutboundListResponse buildTransferResponse(TransferEntity t, Long currentUserId, String currentUserRole) {
        int itemCount = transferItemRepository.findByTransferId(t.getTransferId()).size();
        String destName = (t.getToWarehouseId() != null)
                ? warehouseRepository.findById(t.getToWarehouseId()).map(w -> w.getWarehouseName()).orElse("N/A")
                : "N/A";
        return OutboundListResponse.builder()
                .documentId(t.getTransferId())
                .documentCode(t.getTransferCode())
                .orderType(OutboundType.INTERNAL_TRANSFER)
                .destination(destName)
                .status(t.getStatus())
                .warehouseId(t.getFromWarehouseId())
                .totalItems(itemCount)
                .createdBy(t.getCreatedBy())
                .createdAt(t.getCreatedAt())
                .canEdit(isDraft(t.getStatus())    && owns(t.getCreatedBy(), currentUserId))
                .canDelete(isDraft(t.getStatus())  && owns(t.getCreatedBy(), currentUserId))
                .canSubmit(isDraft(t.getStatus())  && owns(t.getCreatedBy(), currentUserId))
                .canApprove(isPending(t.getStatus()) && isManager(currentUserRole))
                .canConfirm(isApproved(t.getStatus()) && isKeeper(currentUserRole))
                .build();
    }

    private String resolveCustomerName(Long customerId) {
        if (customerId == null) return "N/A";
        try {
            return customerRepository.findById(customerId).map(c -> c.getCustomerName()).orElse("Khách #" + customerId);
        } catch (Exception e) {
            return "Khách #" + customerId;
        }
    }

    private long safeCount(Long warehouseId, String status) {
        try {
            return soQueryRepository.countByWarehouseIdAndStatus(warehouseId, status);
        } catch (Exception e) {
            log.warn("safeCount({},{}) failed: {}", warehouseId, status, e.getMessage());
            return 0L;
        }
    }

    private ApiResponse<PageResponse<OutboundListResponse>> emptyPage(int page, int size) {
        return ApiResponse.success("Không có dữ liệu.", PageResponse.<OutboundListResponse>builder()
                .content(Collections.emptyList()).page(page).size(size)
                .totalElements(0).totalPages(0).last(true).build());
    }

    private boolean isDraft(String s)    { return "DRAFT".equals(s); }
    private boolean isPending(String s)  { return "PENDING_APPROVAL".equals(s); }
    private boolean isApproved(String s) { return "APPROVED".equals(s); }
    private boolean owns(Long c, Long u) { return c != null && c.equals(u); }
    private boolean isManager(String r)  { return "MANAGER".equals(r) || "ROLE_MANAGER".equals(r); }
    private boolean isKeeper(String r)   { return "KEEPER".equals(r)  || "ROLE_KEEPER".equals(r); }
}