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
