package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.request.CreateIncidentRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.IncidentResponse;
import org.example.sep26management.application.dto.response.PageResponse;
import org.example.sep26management.infrastructure.persistence.entity.IncidentEntity;
import org.example.sep26management.infrastructure.persistence.entity.ReceivingOrderEntity;
import org.example.sep26management.infrastructure.persistence.entity.UserEntity;
import org.example.sep26management.infrastructure.persistence.repository.IncidentJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.ReceivingOrderJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.UserJpaRepository;
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
    private final ReceivingOrderJpaRepository receivingOrderRepo;
    private final UserJpaRepository userRepo;

    // ─── Create Incident (Keeper báo sự cố Gate Check) ──────────────────────

    @Transactional
    public ApiResponse<IncidentResponse> createIncident(CreateIncidentRequest request, Long userId) {
        // Generate incident code
        String code = "INC-" + System.currentTimeMillis() % 1_000_000;

        IncidentEntity incident = IncidentEntity.builder()
                .warehouseId(request.getWarehouseId())
                .incidentCode(code)
                .incidentType(request.getIncidentType())
                .severity("HIGH")
                .occurredAt(LocalDateTime.now())
                .description(request.getDescription())
                .reportedBy(userId)
                .attachmentId(request.getAttachmentId())
                .status("OPEN")
                .receivingId(request.getReceivingId())
                .build();

        IncidentEntity saved = incidentRepo.save(incident);

        log.info("Incident created: {} type={} by userId={}", code, request.getIncidentType(), userId);

        return ApiResponse.success("Incident reported successfully", toResponse(saved));
    }

    // ─── List Incidents ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<PageResponse<IncidentResponse>> listIncidents(String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<IncidentEntity> incidentsPage = status != null && !status.isBlank()
                ? incidentRepo.findByStatusOrderByCreatedAtDesc(status, pageable)
                : incidentRepo.findAllByOrderByCreatedAtDesc(pageable);

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

    // ─── Check if receiving order has pending incidents ──────────────────────

    @Transactional(readOnly = true)
    public boolean hasPendingIncident(Long receivingId) {
        List<IncidentEntity> incidents = incidentRepo.findByReceivingIdOrderByCreatedAtDesc(receivingId);
        return incidents.stream().anyMatch(i -> "OPEN".equals(i.getStatus()));
    }

    // ─── Helper: convert to response ────────────────────────────────────────

    private IncidentResponse toResponse(IncidentEntity e) {
        String reportedByName = e.getReportedBy() != null
                ? userRepo.findById(e.getReportedBy()).map(UserEntity::getFullName).orElse(null)
                : null;

        String receivingCode = e.getReceivingId() != null
                ? receivingOrderRepo.findById(e.getReceivingId())
                        .map(ReceivingOrderEntity::getReceivingCode).orElse(null)
                : null;

        return IncidentResponse.builder()
                .incidentId(e.getIncidentId())
                .warehouseId(e.getWarehouseId())
                .incidentCode(e.getIncidentCode())
                .incidentType(e.getIncidentType())
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
                .build();
    }
}
