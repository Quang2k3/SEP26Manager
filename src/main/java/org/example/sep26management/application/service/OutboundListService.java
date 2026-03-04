package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.constants.MessageConstants;
import org.example.sep26management.application.dto.response.*;
import org.example.sep26management.application.enums.OutboundType;
import org.example.sep26management.infrastructure.persistence.entity.SalesOrderEntity;
import org.example.sep26management.infrastructure.persistence.entity.TransferEntity;
import org.example.sep26management.infrastructure.persistence.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SCRUM-509: UC-OUT-06 View Outbound List
 * BR-OUT-24: default last 30 days, newest first
 * BR-OUT-25: 20 per page
 * BR-OUT-26: actions based on role + status
 */
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

        // BR-OUT-24: default last 30 days
        final LocalDateTime effectiveFromDate = (fromDate != null) ? fromDate : LocalDateTime.now().minusDays(30);
        final LocalDateTime effectiveToDate = toDate;
        final String effectiveKeyword = keyword;
        if (size <= 0) size = 20; // BR-OUT-25

        List<OutboundListResponse> combined = new ArrayList<>();

        // Fetch SALES_ORDER if type is null or SALES_ORDER
        if (orderType == null || orderType == OutboundType.SALES_ORDER) {
            Page<SalesOrderEntity> soPage = soQueryRepository.searchSalesOrders(
                    warehouseId, status, createdBy, effectiveFromDate, effectiveToDate, effectiveKeyword,
                    PageRequest.of(0, 1000)); // fetch all then merge

            soPage.getContent().forEach(so -> {
                List<?> items = soItemRepository.findBySoId(so.getSoId());
                String destination = customerRepository.findById(so.getCustomerId())
                        .map(c -> c.getCustomerName()).orElse("N/A");

                combined.add(OutboundListResponse.builder()
                        .documentId(so.getSoId())
                        .documentCode(so.getSoCode())
                        .orderType(OutboundType.SALES_ORDER)
                        .destination(destination)
                        .status(so.getStatus())
                        .totalItems(items.size())
                        .createdBy(so.getCreatedBy())
                        .createdAt(so.getCreatedAt())
                        .shipmentDate(so.getRequiredShipDate())
                        // BR-OUT-26: actions
                        .canEdit(isDraft(so.getStatus()) && so.getCreatedBy().equals(currentUserId))
                        .canDelete(isDraft(so.getStatus()) && so.getCreatedBy().equals(currentUserId))
                        .canSubmit(isDraft(so.getStatus()) && so.getCreatedBy().equals(currentUserId))
                        .canApprove(isPending(so.getStatus()) && isManager(currentUserRole))
                        .canConfirm(isApproved(so.getStatus()) && isKeeper(currentUserRole))
                        .build());
            });
        }

        // Fetch INTERNAL_TRANSFER if type is null or INTERNAL_TRANSFER
        if (orderType == null || orderType == OutboundType.INTERNAL_TRANSFER) {
            List<TransferEntity> transfers = transferRepository
                    .findByFromWarehouseIdAndStatus(warehouseId, status != null ? status : "");

            transfers.stream()
                    .filter(t -> t.getCreatedAt().isAfter(effectiveFromDate))
                    .filter(t -> effectiveKeyword == null || effectiveKeyword.isBlank()
                            || t.getTransferCode().toLowerCase().contains(effectiveKeyword.toLowerCase()))
                    .forEach(t -> {
                        List<?> items = transferItemRepository.findByTransferId(t.getTransferId());
                        String destName = warehouseRepository.findById(t.getToWarehouseId())
                                .map(w -> w.getWarehouseName()).orElse("N/A");

                        combined.add(OutboundListResponse.builder()
                                .documentId(t.getTransferId())
                                .documentCode(t.getTransferCode())
                                .orderType(OutboundType.INTERNAL_TRANSFER)
                                .destination(destName)
                                .status(t.getStatus())
                                .totalItems(items.size())
                                .createdBy(t.getCreatedBy())
                                .createdAt(t.getCreatedAt())
                                .canEdit(isDraft(t.getStatus()) && t.getCreatedBy().equals(currentUserId))
                                .canDelete(isDraft(t.getStatus()) && t.getCreatedBy().equals(currentUserId))
                                .canSubmit(isDraft(t.getStatus()) && t.getCreatedBy().equals(currentUserId))
                                .canApprove(isPending(t.getStatus()) && isManager(currentUserRole))
                                .canConfirm(isApproved(t.getStatus()) && isKeeper(currentUserRole))
                                .build());
                    });
        }

        // Sort by createdAt DESC (BR-OUT-24: newest first)
        combined.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        // Manual pagination
        int total = combined.size();
        int start = page * size;
        int end = Math.min(start + size, total);
        List<OutboundListResponse> pageContent = start >= total
                ? List.of() : combined.subList(start, end);

        return ApiResponse.success(
                total == 0 ? MessageConstants.OUTBOUND_LIST_EMPTY : MessageConstants.OUTBOUND_LIST_SUCCESS,
                PageResponse.<OutboundListResponse>builder()
                        .content(pageContent)
                        .page(page).size(size)
                        .totalElements(total)
                        .totalPages((int) Math.ceil((double) total / size))
                        .last(end >= total)
                        .build());
    }

    @Transactional(readOnly = true)
    public ApiResponse<OutboundSummaryResponse> getSummary(Long warehouseId) {
        LocalDateTime from = LocalDateTime.now().minusDays(30);
        return ApiResponse.success("Summary loaded", OutboundSummaryResponse.builder()
                .total(soQueryRepository.countTotal(warehouseId, from))
                .pendingApproval(soQueryRepository.countByStatus(warehouseId, "PENDING_APPROVAL", from))
                .approved(soQueryRepository.countByStatus(warehouseId, "APPROVED", from))
                .confirmedToday(soQueryRepository.countConfirmedToday(warehouseId, java.time.LocalDate.now().atStartOfDay(), java.time.LocalDate.now().plusDays(1).atStartOfDay()))
                .draft(soQueryRepository.countByStatus(warehouseId, "DRAFT", from))
                .rejected(soQueryRepository.countByStatus(warehouseId, "REJECTED", from))
                .build());
    }

    private boolean isDraft(String s) { return "DRAFT".equals(s); }
    private boolean isPending(String s) { return "PENDING_APPROVAL".equals(s); }
    private boolean isApproved(String s) { return "APPROVED".equals(s); }
    private boolean isManager(String role) { return "MANAGER".equals(role) || "ROLE_MANAGER".equals(role); }
    private boolean isKeeper(String role) { return "KEEPER".equals(role) || "ROLE_KEEPER".equals(role); }
}