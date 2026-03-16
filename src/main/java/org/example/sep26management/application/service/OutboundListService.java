package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.response.*;
import org.example.sep26management.application.enums.OutboundType;
import org.example.sep26management.infrastructure.persistence.entity.SalesOrderEntity;
import org.example.sep26management.infrastructure.persistence.entity.TransferEntity;
import org.example.sep26management.infrastructure.persistence.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
        log.info("listOutbound: warehouseId={}, status={}, orderType={}, page={}", warehouseId, status, orderType, page);

        List<OutboundListResponse> combined = new ArrayList<>();

        // ── SALES ORDERS ────────────────────────────────────────────────────────
        if (orderType == null || orderType == OutboundType.SALES_ORDER) {
            try {
                List<SalesOrderEntity> soList = soQueryRepository.searchSalesOrders(
                        warehouseId, status, createdBy, fromDate, toDate, keyword,
                        PageRequest.of(0, 1000)).getContent();

                for (SalesOrderEntity so : soList) {
                    try {
                        int itemCount = soItemRepository.findBySoId(so.getSoId()).size();
                        String destination = resolveCustomerName(so.getCustomerId());

                        combined.add(OutboundListResponse.builder()
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
                                .canEdit(isDraft(so.getStatus()) && owns(so.getCreatedBy(), currentUserId))
                                .canDelete(isDraft(so.getStatus()) && owns(so.getCreatedBy(), currentUserId))
                                .canSubmit(isDraft(so.getStatus()) && owns(so.getCreatedBy(), currentUserId))
                                .canApprove(isPending(so.getStatus()) && isManager(currentUserRole))
                                .canConfirm(isApproved(so.getStatus()) && isKeeper(currentUserRole))
                                .build());
                    } catch (Exception e) {
                        log.warn("Skip SO id={}: {}", so.getSoId(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("listOutbound SO query failed: {}", e.getMessage(), e);
            }
        }

        // ── INTERNAL TRANSFERS ──────────────────────────────────────────────────
        if (orderType == null || orderType == OutboundType.INTERNAL_TRANSFER) {
            try {
                List<TransferEntity> transfers = (warehouseId != null)
                        ? (status == null || status.isBlank()
                        ? transferRepository.findByFromWarehouseIdOrderByCreatedAtDesc(warehouseId)
                        : transferRepository.findByFromWarehouseIdAndStatus(warehouseId, status))
                        : transferRepository.findAll();

                for (TransferEntity t : transfers) {
                    try {
                        // Apply keyword filter
                        if (keyword != null && !keyword.isBlank()
                                && !t.getTransferCode().toLowerCase().contains(keyword.toLowerCase())) continue;
                        // Apply status filter for null-warehouseId path
                        if (warehouseId == null && status != null && !status.isBlank()
                                && !status.equals(t.getStatus())) continue;

                        int itemCount = transferItemRepository.findByTransferId(t.getTransferId()).size();
                        String destName = (t.getToWarehouseId() != null)
                                ? warehouseRepository.findById(t.getToWarehouseId())
                                .map(w -> w.getWarehouseName()).orElse("N/A")
                                : "N/A";

                        combined.add(OutboundListResponse.builder()
                                .documentId(t.getTransferId())
                                .documentCode(t.getTransferCode())
                                .orderType(OutboundType.INTERNAL_TRANSFER)
                                .destination(destName)
                                .status(t.getStatus())
                                .warehouseId(t.getFromWarehouseId())
                                .totalItems(itemCount)
                                .createdBy(t.getCreatedBy())
                                .createdAt(t.getCreatedAt())
                                .canEdit(isDraft(t.getStatus()) && owns(t.getCreatedBy(), currentUserId))
                                .canDelete(isDraft(t.getStatus()) && owns(t.getCreatedBy(), currentUserId))
                                .canSubmit(isDraft(t.getStatus()) && owns(t.getCreatedBy(), currentUserId))
                                .canApprove(isPending(t.getStatus()) && isManager(currentUserRole))
                                .canConfirm(isApproved(t.getStatus()) && isKeeper(currentUserRole))
                                .build());
                    } catch (Exception e) {
                        log.warn("Skip Transfer id={}: {}", t.getTransferId(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("listOutbound Transfer query failed: {}", e.getMessage(), e);
            }
        }

        // ── Sort + Paginate ─────────────────────────────────────────────────────
        combined.sort((a, b) -> {
            if (a.getCreatedAt() == null) return 1;
            if (b.getCreatedAt() == null) return -1;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });

        int total = combined.size();
        int start = Math.min(page * size, total);
        int end   = Math.min(start + size, total);
        List<OutboundListResponse> pageContent = combined.subList(start, end);

        log.info("listOutbound OK: total={}, page={}, returned={}", total, page, pageContent.size());

        return ApiResponse.success(
                total == 0 ? "Không có lệnh xuất kho nào." : "Danh sách lệnh xuất kho.",
                PageResponse.<OutboundListResponse>builder()
                        .content(pageContent)
                        .page(page)
                        .size(size)
                        .totalElements(total)
                        .totalPages(size > 0 ? (int) Math.ceil((double) total / size) : 0)
                        .last(end >= total)
                        .build());
    }

    // ── Summary ─────────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public ApiResponse<OutboundSummaryResponse> getSummary(Long warehouseId) {
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
                .draft(draft)
                .pendingApproval(pending)
                .approved(approved)
                .allocated(allocated)
                .picking(picking)
                .qcScan(qcScan)
                .dispatched(dispatched)
                .rejected(rejected)
                .build());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────
    private String resolveCustomerName(Long customerId) {
        if (customerId == null) return "N/A";
        try {
            return customerRepository.findById(customerId)
                    .map(c -> c.getCustomerName())
                    .orElse("Khách hàng #" + customerId);
        } catch (Exception e) {
            return "Khách hàng #" + customerId;
        }
    }

    private long safeCount(Long warehouseId, String status) {
        try {
            return soQueryRepository.countByStatusAllTime(warehouseId, status);
        } catch (Exception e) {
            log.warn("safeCount({}, {}) failed: {}", warehouseId, status, e.getMessage());
            return 0L;
        }
    }

    private boolean isDraft(String s)   { return "DRAFT".equals(s); }
    private boolean isPending(String s) { return "PENDING_APPROVAL".equals(s); }
    private boolean isApproved(String s){ return "APPROVED".equals(s); }
    private boolean owns(Long creator, Long currentUser) {
        return creator != null && creator.equals(currentUser);
    }
    private boolean isManager(String r) { return "MANAGER".equals(r) || "ROLE_MANAGER".equals(r); }
    private boolean isKeeper(String r)  { return "KEEPER".equals(r)  || "ROLE_KEEPER".equals(r);  }
}
