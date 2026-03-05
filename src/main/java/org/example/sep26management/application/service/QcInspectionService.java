package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.request.QcDecisionRequest;
import org.example.sep26management.application.dto.request.UpdateQcInspectionRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.QcInspectionResponse;
import org.example.sep26management.infrastructure.persistence.entity.InventoryLotEntity;
import org.example.sep26management.infrastructure.persistence.entity.QcInspectionEntity;
import org.example.sep26management.infrastructure.persistence.entity.QuarantineHoldEntity;
import org.example.sep26management.infrastructure.persistence.entity.SkuEntity;
import org.example.sep26management.infrastructure.persistence.entity.UserEntity;
import org.example.sep26management.infrastructure.persistence.repository.InventorySnapshotJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.QcInspectionJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.QuarantineHoldJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.SkuJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.UserJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class QcInspectionService {

    private final QcInspectionJpaRepository qcRepo;
    private final QuarantineHoldJpaRepository quarantineRepo;
    private final SkuJpaRepository skuRepo;
    private final UserJpaRepository userRepo;

    // ─── List QC Inspections ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<List<QcInspectionResponse>> listInspections(String status) {
        List<QcInspectionEntity> inspections = status != null && !status.isBlank()
                ? qcRepo.findByStatusOrderByCreatedAtDesc(status)
                : qcRepo.findAllByOrderByCreatedAtDesc();

        List<QcInspectionResponse> result = inspections.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ApiResponse.success("OK", result);
    }

    // ─── Get QC Inspection Detail ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<QcInspectionResponse> getInspection(Long id) {
        QcInspectionEntity inspection = qcRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("QC Inspection not found: " + id));
        return ApiResponse.success("OK", toResponse(inspection));
    }

    // ─── QC Submits Report (Bước 3 - QC Workspace) ─────────────────────────

    @Transactional
    public ApiResponse<QcInspectionResponse> submitReport(Long id, UpdateQcInspectionRequest request, Long qcUserId) {
        QcInspectionEntity inspection = qcRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("QC Inspection not found: " + id));

        if (!"PENDING".equals(inspection.getStatus())) {
            throw new RuntimeException("QC Inspection is not in PENDING status. Current: " + inspection.getStatus());
        }

        inspection.setRemarks(request.getRemarks());
        inspection.setAttachmentId(request.getAttachmentId());
        inspection.setInspectedBy(qcUserId);
        inspection.setInspectedAt(LocalDateTime.now());
        inspection.setStatus("INSPECTED");

        qcRepo.save(inspection);

        log.info("QC Report submitted for inspection {} by userId={}", inspection.getInspectionCode(), qcUserId);

        return ApiResponse.success("QC Report submitted successfully", toResponse(inspection));
    }

    // ─── Manager Decision (Bước 4 - Manager Dashboard) ─────────────────────

    @Transactional
    public ApiResponse<QcInspectionResponse> makeDecision(Long id, QcDecisionRequest request, Long managerId) {
        QcInspectionEntity inspection = qcRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("QC Inspection not found: " + id));

        if (!"INSPECTED".equals(inspection.getStatus())) {
            throw new RuntimeException(
                    "QC Inspection must be in INSPECTED status before decision. Current: " + inspection.getStatus());
        }

        String decision = request.getDecision().toUpperCase();
        if (!"SCRAP".equals(decision) && !"RETURN".equals(decision) && !"DOWNGRADE".equals(decision)) {
            throw new RuntimeException("Invalid decision. Must be SCRAP, RETURN, or DOWNGRADE.");
        }

        // Update inspection status
        inspection.setStatus("DECIDED");
        if (request.getNote() != null) {
            String existingRemarks = inspection.getRemarks() != null ? inspection.getRemarks() : "";
            inspection.setRemarks(existingRemarks + "\n[Manager Decision: " + decision + "] " + request.getNote());
        }
        qcRepo.save(inspection);

        // Release the quarantine hold
        List<QuarantineHoldEntity> holds = quarantineRepo.findByLotId(inspection.getLotId());
        for (QuarantineHoldEntity hold : holds) {
            if (hold.getReleaseAt() == null) {
                hold.setReleaseBy(managerId);
                hold.setReleaseAt(LocalDateTime.now());
                hold.setReleaseNote(
                        "Decision: " + decision + ". " + (request.getNote() != null ? request.getNote() : ""));
                quarantineRepo.save(hold);
            }
        }

        log.info("Manager decision for inspection {}: {} by managerId={}", inspection.getInspectionCode(), decision,
                managerId);

        // If RETURN → generate return order (to be implemented in
        // ReceivingOrderService)
        if ("RETURN".equals(decision)) {
            log.info("RETURN decision → Return order should be generated for lotId={}", inspection.getLotId());
            // TODO: Call ReceivingOrderService.generateDamagedReturn() or similar
        }

        QcInspectionResponse response = toResponse(inspection);
        response.setDecision(decision);

        return ApiResponse.success("Decision recorded: " + decision, response);
    }

    // ─── Helper: Convert to Response ────────────────────────────────────────

    private QcInspectionResponse toResponse(QcInspectionEntity e) {
        String inspectedByName = e.getInspectedBy() != null
                ? userRepo.findById(e.getInspectedBy()).map(UserEntity::getFullName).orElse(null)
                : null;

        // Lookup lot → SKU info
        String lotNumber = null;
        Long skuId = null;
        String skuCode = null;
        String skuName = null;

        // We don't have InventoryLotJpaRepository yet, so we leave lot info for now
        // TODO: Add lot info enrichment when InventoryLotJpaRepository is created

        return QcInspectionResponse.builder()
                .inspectionId(e.getInspectionId())
                .warehouseId(e.getWarehouseId())
                .lotId(e.getLotId())
                .inspectionCode(e.getInspectionCode())
                .status(e.getStatus())
                .inspectedBy(e.getInspectedBy())
                .inspectedByName(inspectedByName)
                .inspectedAt(e.getInspectedAt())
                .remarks(e.getRemarks())
                .attachmentId(e.getAttachmentId())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
