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

        log.info("listOutbound: warehouseId={}, status={}, orderType={}, page={}", warehouseId, status, orderType, page);

        try {
            final LocalDateTime effectiveFromDate = (fromDate != null) ? fromDate : LocalDateTime.now().minusDays(30);
            final LocalDateTime effectiveToDate = toDate;
            final String effectiveKeyword = keyword;
            if (size <= 0) size = 20;

            List<OutboundListResponse> combined = new ArrayList<>();

            // SALES_ORDER
            if (orderType == null || orderType == OutboundType.SALES_ORDER) {
                Page<SalesOrderEntity> soPage = soQueryRepository.searchSalesOrders(
                        warehouseId, status, createdBy, effectiveFromDate, effectiveToDate, effectiveKeyword,
                        PageRequest.of(0, 1000));

                soPage.getContent().forEach(so -> {
                    try {
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
                                .canEdit(isDraft(so.getStatus()) && so.getCreatedBy() != null && so.getCreatedBy().equals(currentUserId))
                                .canDelete(isDraft(so.getStatus()) && so.getCreatedBy() != null && so.getCreatedBy().equals(currentUserId))
                                .canSubmit(isDraft(so.getStatus()) && so.getCreatedBy() != null && so.getCreatedBy().equals(currentUserId))
                                .canApprove(isPending(so.getStatus()) && isManager(currentUserRole))
                                .canConfirm(isApproved(so.getStatus()) && isKeeper(currentUserRole))
                                .build());
                    } catch (Exception e) {
                        log.warn("Skip SO {}: {}", so.getSoId(), e.getMessage());
                    }
                });
            }

            // INTERNAL_TRANSFER
            if (orderType == null || orderType == OutboundType.INTERNAL_TRANSFER) {
                // Nếu status null → lấy tất cả transfer theo warehouse
                List<TransferEntity> transfers;
                if (status == null || status.isBlank()) {
                    transfers = transferRepository.findAll().stream()
                            .filter(t -> warehouseId.equals(t.getFromWarehouseId()))
                            .toList();
                } else {
                    transfers = transferRepository.findByFromWarehouseIdAndStatus(warehouseId, status);
                }

                transfers.stream()
                        .filter(t -> t.getCreatedAt() != null && t.getCreatedAt().isAfter(effectiveFromDate))
                        .filter(t -> effectiveKeyword == null || effectiveKeyword.isBlank()
                                || t.getTransferCode().toLowerCase().contains(effectiveKeyword.toLowerCase()))
                        .forEach(t -> {
                            try {
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
                                        .canEdit(isDraft(t.getStatus()) && t.getCreatedBy() != null && t.getCreatedBy().equals(currentUserId))
                                        .canDelete(isDraft(t.getStatus()) && t.getCreatedBy() != null && t.getCreatedBy().equals(currentUserId))
                                        .canSubmit(isDraft(t.getStatus()) && t.getCreatedBy() != null && t.getCreatedBy().equals(currentUserId))
                                        .canApprove(isPending(t.getStatus()) && isManager(currentUserRole))
                                        .canConfirm(isApproved(t.getStatus()) && isKeeper(currentUserRole))
                                        .build());
                            } catch (Exception e) {
                                log.warn("Skip Transfer {}: {}", t.getTransferId(), e.getMessage());
                            }
                        });
            }

            // Sort null-safe
            combined.sort((a, b) -> {
                if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                if (a.getCreatedAt() == null) return 1;
                if (b.getCreatedAt() == null) return -1;
                return b.getCreatedAt().compareTo(a.getCreatedAt());
            });

            int total = combined.size();
            int start = page * size;
            int end = Math.min(start + size, total);
            List<OutboundListResponse> pageContent = start >= total ? List.of() : combined.subList(start, end);

            log.info("listOutbound result: total={}, returned={}", total, pageContent.size());

            return ApiResponse.success(
                    total == 0 ? MessageConstants.OUTBOUND_LIST_EMPTY : MessageConstants.OUTBOUND_LIST_SUCCESS,
                    PageResponse.<OutboundListResponse>builder()
                            .content(pageContent)
                            .page(page).size(size)
                            .totalElements(total)
                            .totalPages((int) Math.ceil((double) total / size))
                            .last(end >= total)
                            .build());

        } catch (Exception e) {
            log.error("listOutbound FAILED: warehouseId={}, error={}", warehouseId, e.getMessage(), e);
            throw e;
        }
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