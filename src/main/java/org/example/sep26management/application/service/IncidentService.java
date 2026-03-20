package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.request.CreateIncidentRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.IncidentResponse;
import org.example.sep26management.application.dto.response.PageResponse;
import org.example.sep26management.infrastructure.persistence.entity.IncidentEntity;
import org.example.sep26management.infrastructure.persistence.entity.IncidentItemEntity;
import org.example.sep26management.infrastructure.persistence.entity.ReceivingOrderEntity;
import org.example.sep26management.infrastructure.persistence.entity.UserEntity;
import org.example.sep26management.infrastructure.persistence.entity.SkuEntity;
import org.example.sep26management.infrastructure.persistence.repository.IncidentItemJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.IncidentJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.ReceivingItemJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.ReceivingOrderJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.UserJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.SkuJpaRepository;
import org.example.sep26management.application.dto.response.IncidentItemResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class IncidentService {

    private final IncidentJpaRepository incidentRepo;
    private final IncidentItemJpaRepository incidentItemRepo;
    private final ReceivingOrderJpaRepository receivingOrderRepo;
    private final ReceivingItemJpaRepository receivingItemRepo;
    private final UserJpaRepository userRepo;
    private final SkuJpaRepository skuRepo;

    // ─── Create Incident (Keeper báo sự cố Gate Check) ──────────────────────

    @Transactional
    public ApiResponse<IncidentResponse> createIncident(CreateIncidentRequest request, Long userId) {
        // Generate incident code
        String code = "INC-" + System.currentTimeMillis() % 1_000_000;

        IncidentEntity incident = IncidentEntity.builder()
                .warehouseId(request.getWarehouseId())
                .incidentCode(code)
                .incidentType(request.getIncidentType())
                .category(request.getCategory())
                .severity("HIGH")
                .occurredAt(LocalDateTime.now())
                .description(request.getDescription())
                .reportedBy(userId)
                .attachmentId(request.getAttachmentId())
                .status("OPEN")
                .receivingId(request.getReceivingId())
                .build();

        IncidentEntity saved = incidentRepo.save(incident);

        if (request.getItems() != null && !request.getItems().isEmpty()) {
            for (CreateIncidentRequest.IncidentItemDto itemDto : request.getItems()) {
                IncidentItemEntity itemEntity = IncidentItemEntity.builder()
                        .incident(saved)
                        .skuId(itemDto.getSkuId())
                        .damagedQty(itemDto.getDamagedQty())
                        .expectedQty(itemDto.getExpectedQty())
                        .actualQty(itemDto.getActualQty())
                        .reasonCode(itemDto.getReasonCode())
                        .note(itemDto.getNote())
                        .build();
                incidentItemRepo.save(itemEntity);
            }
        }

        log.info("Incident created: {} type={} by userId={}", code, request.getIncidentType().name(), userId);

        return ApiResponse.success("Incident reported successfully", toResponse(saved));
    }

    // ─── List Incidents ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<PageResponse<IncidentResponse>> listIncidents(String status,
                                                                     org.example.sep26management.application.enums.IncidentCategory category, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<IncidentEntity> incidentsPage;

        if (status != null && !status.isBlank() && category != null) {
            incidentsPage = incidentRepo.findByStatusAndCategoryOrderByCreatedAtDesc(status, category, pageable);
        } else if (status != null && !status.isBlank()) {
            incidentsPage = incidentRepo.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else if (category != null) {
            incidentsPage = incidentRepo.findByCategoryOrderByCreatedAtDesc(category, pageable);
        } else {
            incidentsPage = incidentRepo.findAllByOrderByCreatedAtDesc(pageable);
        }

        List<IncidentResponse> content = incidentsPage.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        PageResponse<IncidentResponse> pageResponse = PageResponse.<IncidentResponse>builder()
                .content(content)
                .page(incidentsPage.getNumber())
                .size(incidentsPage.getSize())
                .totalElements(incidentsPage.getTotalElements())
                .totalPages(incidentsPage.getTotalPages())
                .last(incidentsPage.isLast())
                .build();

        return ApiResponse.success("OK", pageResponse);
    }

    // ─── Get Incident ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<IncidentResponse> getIncident(Long id) {
        IncidentEntity incident = incidentRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Incident not found: " + id));
        return ApiResponse.success("OK", toResponse(incident));
    }

    // ─── Approve Incident (Manager cho phép dỡ hàng) ────────────────────────

    @Transactional
    public ApiResponse<IncidentResponse> approveIncident(Long id, Long managerId) {
        IncidentEntity incident = incidentRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Incident not found: " + id));

        if (!"OPEN".equals(incident.getStatus())) {
            throw new RuntimeException("Incident is not in OPEN status. Current: " + incident.getStatus());
        }

        incident.setStatus("APPROVED");
        incidentRepo.save(incident);

        log.info("Incident {} approved by managerId={}", incident.getIncidentCode(), managerId);

        return ApiResponse.success("Incident approved. Keeper can start unloading.", toResponse(incident));
    }

    // ─── Reject Incident (Manager từ chối nhận xe) ──────────────────────────

    @Transactional
    public ApiResponse<IncidentResponse> rejectIncident(Long id, String reason, Long managerId) {
        IncidentEntity incident = incidentRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Incident not found: " + id));

        if (!"OPEN".equals(incident.getStatus())) {
            throw new RuntimeException("Incident is not in OPEN status. Current: " + incident.getStatus());
        }

        incident.setStatus("REJECTED");
        // Append manager's rejection note to the description
        if (reason != null && !reason.isBlank()) {
            incident.setDescription(incident.getDescription() + "\n[Manager Reject] " + reason);
        }
        incidentRepo.save(incident);

        log.info("Incident {} rejected by managerId={}, reason: {}", incident.getIncidentCode(), managerId, reason);

        return ApiResponse.success("Incident rejected. Truck will not be unloaded.", toResponse(incident));
    }

    // ─── Resolve Incident (Manager chốt Pass/Fail) ──────────────────────────

    @Transactional
    public ApiResponse<IncidentResponse> resolveIncident(Long id,
                                                         org.example.sep26management.application.dto.request.ResolveIncidentRequest request, Long managerId) {
        IncidentEntity incident = incidentRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Incident not found: " + id));

        if (!"OPEN".equals(incident.getStatus()) && !"APPROVED".equals(incident.getStatus())) {
            throw new RuntimeException("Incident cannot be resolved in current status: " + incident.getStatus());
        }

        // Apply item resolutions if provided
        if (request.getResolutions() != null && !request.getResolutions().isEmpty()) {
            for (org.example.sep26management.application.dto.request.ResolveIncidentRequest.ResolutionItemDto res : request
                    .getResolutions()) {
                IncidentItemEntity item = incidentItemRepo.findById(res.getIncidentItemId())
                        .orElseThrow(() -> new RuntimeException("Incident item not found: " + res.getIncidentItemId()));
                if (!item.getIncident().getIncidentId().equals(id)) {
                    throw new RuntimeException("Incident item does not belong to this incident");
                }

                if ("PASS".equalsIgnoreCase(res.getAction())) {
                    if (item.getActionPassQty() == null)
                        item.setActionPassQty(java.math.BigDecimal.ZERO);
                    item.setActionPassQty(item.getActionPassQty().add(res.getQuantity()));
                } else if ("RETURN".equalsIgnoreCase(res.getAction())) {
                    if (item.getActionReturnQty() == null)
                        item.setActionReturnQty(java.math.BigDecimal.ZERO);
                    item.setActionReturnQty(item.getActionReturnQty().add(res.getQuantity()));
                } else if ("SCRAP".equalsIgnoreCase(res.getAction()) || "DOWNGRADE".equalsIgnoreCase(res.getAction())) {
                    if (item.getActionScrapQty() == null)
                        item.setActionScrapQty(java.math.BigDecimal.ZERO);
                    item.setActionScrapQty(item.getActionScrapQty().add(res.getQuantity()));
                }

                // Cập nhật lại note/reason cho item dựa trên phán quyết của manager
                item.setNote(item.getNote() != null
                        ? item.getNote() + " | [Manager Decision]: " + res.getAction() + " (Qty: " + res.getQuantity()
                        + ")"
                        : "[Manager Decision]: " + res.getAction() + " (Qty: " + res.getQuantity() + ")");
                incidentItemRepo.save(item);
            }
        }

        incident.setStatus("RESOLVED");
        if (request.getNote() != null && !request.getNote().isBlank()) {
            incident.setDescription(incident.getDescription() + "\n[Manager Resolution Note] " + request.getNote());
        }
        incidentRepo.save(incident);

        log.info("Incident {} resolved by managerId={}", incident.getIncidentCode(), managerId);

        return ApiResponse.success("Incident resolved successfully.", toResponse(incident));
    }

    // ─── Resolve Discrepancy Incident (Manager xử lý từng item thừa/thiếu) ──────

    @Transactional
    public ApiResponse<IncidentResponse> resolveDiscrepancy(Long id,
                                                            org.example.sep26management.application.dto.request.ResolveDiscrepancyRequest request,
                                                            Long managerId) {
        IncidentEntity incident = incidentRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Incident not found: " + id));

        if (!"OPEN".equals(incident.getStatus())) {
            throw new RuntimeException(
                    "Incident is not in OPEN status. Current: " + incident.getStatus());
        }

        if (!org.example.sep26management.application.enums.IncidentType.SHORTAGE.equals(incident.getIncidentType())
                && !org.example.sep26management.application.enums.IncidentType.OVERAGE
                .equals(incident.getIncidentType())
                && !org.example.sep26management.application.enums.IncidentType.UNEXPECTED_ITEM
                .equals(incident.getIncidentType())
                && !org.example.sep26management.application.enums.IncidentType.DISCREPANCY
                .equals(incident.getIncidentType())) {
            throw new RuntimeException(
                    "This API is only for resolving quantity discrepancy incidents (SHORTAGE/OVERAGE/UNEXPECTED_ITEM/DISCREPANCY).");
        }

        ReceivingOrderEntity order = receivingOrderRepo.findById(incident.getReceivingId())
                .orElseThrow(() -> new RuntimeException("Receiving order not found: " + incident.getReceivingId()));

        for (org.example.sep26management.application.dto.request.ResolveDiscrepancyRequest.ItemResolution res : request
                .getItems()) {
            IncidentItemEntity incItem = incidentItemRepo.findById(res.getIncidentItemId())
                    .orElseThrow(
                            () -> new RuntimeException("Incident item not found: " + res.getIncidentItemId()));

            if (!incItem.getIncident().getIncidentId().equals(id)) {
                throw new RuntimeException("Incident item " + res.getIncidentItemId()
                        + " does not belong to incident " + id);
            }

            // Find corresponding receiving item
            org.example.sep26management.infrastructure.persistence.entity.ReceivingItemEntity rcItem = receivingItemRepo
                    .findByReceivingOrderReceivingId(order.getReceivingId()).stream()
                    .filter(itm -> itm.getSkuId().equals(incItem.getSkuId()))
                    .findFirst().orElse(null);

            String action = res.getAction().toUpperCase();

            switch (action) {
                case "CLOSE_SHORT":
                    // Chốt thiếu: expectedQty = receivedQty (accept what was received)
                    if (rcItem != null) {
                        rcItem.setExpectedQty(rcItem.getReceivedQty());
                        receivingItemRepo.save(rcItem);
                    }
                    incItem.setNote(appendNote(incItem.getNote(),
                            "[Manager]: CLOSE_SHORT — Chốt thiếu, chấp nhận số lượng nhận được"));
                    break;

                case "WAIT_BACKORDER":
                    // Nhập số đã có, đánh dấu thiếu LOT để lần sau giao bù
                    if (rcItem != null) {
                        java.math.BigDecimal shortageQty = rcItem.getExpectedQty()
                                .subtract(rcItem.getReceivedQty() != null ? rcItem.getReceivedQty() : java.math.BigDecimal.ZERO);
                        String lotInfo = rcItem.getLotNumber() != null ? rcItem.getLotNumber() : "N/A";
                        String skuCode = skuRepo.findById(rcItem.getSkuId())
                                .map(SkuEntity::getSkuCode).orElse("SKU-" + rcItem.getSkuId());

                        // Accept current received qty — set expectedQty = receivedQty
                        rcItem.setExpectedQty(rcItem.getReceivedQty());
                        receivingItemRepo.save(rcItem);

                        incItem.setNote(appendNote(incItem.getNote(),
                                "[Manager]: WAIT_BACKORDER — Nhập " + rcItem.getReceivedQty()
                                        + " đã có. Thiếu " + shortageQty + " ("
                                        + skuCode + ", LOT: " + lotInfo
                                        + ") — chờ NCC giao bù lần sau"));
                    } else {
                        incItem.setNote(appendNote(incItem.getNote(),
                                "[Manager]: WAIT_BACKORDER — Chờ giao bù cho phần thiếu"));
                    }
                    break;

                case "ACCEPT":
                    // Nhận hàng thừa: expectedQty = receivedQty (accept all received)
                    if (rcItem != null) {
                        rcItem.setExpectedQty(rcItem.getReceivedQty());
                        receivingItemRepo.save(rcItem);
                    }
                    incItem.setActionPassQty(incItem.getDamagedQty()); // pass the overage qty
                    incItem.setNote(appendNote(incItem.getNote(),
                            "[Manager]: ACCEPT — Nhận hàng thừa, nhập kho tất cả"));
                    break;

                case "RETURN":
                    // Trả hàng thừa hoặc hàng hỏng
                    if (rcItem != null) {
                        incItem.setActionReturnQty(incItem.getDamagedQty()); // return the overage/damaged qty
                        rcItem.setReceivedQty(rcItem.getExpectedQty());
                        receivingItemRepo.save(rcItem);
                    }
                    incItem.setNote(appendNote(incItem.getNote(),
                            "[Manager]: RETURN — Hoàn hàng"));
                    break;

                case "SCRAP":
                    // Huỷ bỏ hàng hỏng
                    if (rcItem != null) {
                        incItem.setActionScrapQty(incItem.getDamagedQty());
                        // Giảm receivedQty bớt phần huỷ
                        java.math.BigDecimal newReceived = rcItem.getReceivedQty()
                                .subtract(incItem.getDamagedQty() != null ? incItem.getDamagedQty() : java.math.BigDecimal.ZERO);
                        if (newReceived.compareTo(java.math.BigDecimal.ZERO) < 0) newReceived = java.math.BigDecimal.ZERO;
                        rcItem.setReceivedQty(newReceived);
                        receivingItemRepo.save(rcItem);
                    }
                    incItem.setNote(appendNote(incItem.getNote(),
                            "[Manager]: SCRAP — Huỷ bỏ hàng hỏng"));
                    break;

                default:
                    throw new IllegalArgumentException(
                            "Invalid action: " + action
                                    + ". Must be CLOSE_SHORT, WAIT_BACKORDER, ACCEPT, RETURN, or SCRAP");
            }

            incidentItemRepo.save(incItem);
        }

        // Resolve incident and move order to SUBMITTED → QC tiếp tục
        incident.setStatus("RESOLVED");
        if (request.getNote() != null && !request.getNote().isBlank()) {
            incident.setDescription(
                    incident.getDescription() + "\n[Manager Resolution Note] " + request.getNote());
        }
        incidentRepo.save(incident);

        // QC đã scan xong + Manager đã xử lý incident → chuyển QC_APPROVED để tạo GRN
        order.setStatus("QC_APPROVED");
        order.setUpdatedAt(LocalDateTime.now());
        log.info("Discrepancy Incident {} resolved — order moved to QC_APPROVED (receivingId={})",
                incident.getIncidentCode(), order.getReceivingId());
        receivingOrderRepo.save(order);

        log.info("Discrepancy Incident {} resolved by managerId={}",
                incident.getIncidentCode(), managerId);
        return ApiResponse.success("Discrepancy incident resolved successfully.", toResponse(incident));
    }

    private String appendNote(String existing, String newNote) {
        return existing != null ? existing + " | " + newNote : newNote;
    }

    // ─── Check if receiving order has pending incidents ──────────────────────

    @Transactional(readOnly = true)
    public boolean hasPendingIncident(Long receivingId) {
        List<IncidentEntity> incidents = incidentRepo.findByReceivingIdOrderByCreatedAtDesc(receivingId);
        return incidents.stream().anyMatch(i -> "OPEN".equals(i.getStatus()));
    }

    // ─── Helper: convert to response ────────────────────────────────────────

    private IncidentResponse toResponse(IncidentEntity e) {
        String reportedByName = null;
        if (e.getReportedBy() != null) {
            reportedByName = userRepo.findById(e.getReportedBy())
                    .map(UserEntity::getFullName).orElse(null);
        }

        String receivingCode = null;
        if (e.getReceivingId() != null) {
            receivingCode = receivingOrderRepo.findById(e.getReceivingId())
                    .map(ReceivingOrderEntity::getReceivingCode).orElse(null);
        }

        List<IncidentItemEntity> items = incidentItemRepo.findByIncidentIncidentId(e.getIncidentId());
        List<IncidentItemResponse> itemResponses = items.stream().map(item -> {
            SkuEntity sku = skuRepo.findById(item.getSkuId()).orElse(null);
            return IncidentItemResponse.builder()
                    .incidentItemId(item.getIncidentItemId())
                    .skuId(item.getSkuId())
                    .skuName(sku != null ? sku.getSkuName() : null)
                    .skuCode(sku != null ? sku.getSkuCode() : null)
                    .damagedQty(item.getDamagedQty())
                    .expectedQty(item.getExpectedQty())
                    .actualQty(item.getActualQty())
                    .reasonCode(item.getReasonCode())
                    .note(item.getNote())
                    .build();
        }).collect(Collectors.toList());

        return IncidentResponse.builder()
                .incidentId(e.getIncidentId())
                .warehouseId(e.getWarehouseId())
                .incidentCode(e.getIncidentCode())
                .incidentType(e.getIncidentType())
                .category(e.getCategory())
                .severity(e.getSeverity())
                .occurredAt(e.getOccurredAt())
                .description(e.getDescription())
                .reportedBy(e.getReportedBy())
                .reportedByName(reportedByName)
                .attachmentId(e.getAttachmentId())
                .status(e.getStatus())
                .receivingId(e.getReceivingId())
                .receivingCode(receivingCode)
                .createdAt(e.getCreatedAt())
                .items(itemResponses)
                .build();
    }
}