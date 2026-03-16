package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.request.QcDecisionRequest;
import org.example.sep26management.application.dto.request.UpdateQcInspectionRequest;
import org.example.sep26management.application.dto.response.ApiResponse;
import org.example.sep26management.application.dto.response.PageResponse;
import org.example.sep26management.application.dto.response.QcInspectionResponse;
import org.example.sep26management.infrastructure.exception.BusinessException;
import org.example.sep26management.infrastructure.exception.ResourceNotFoundException;
import org.example.sep26management.infrastructure.persistence.entity.InventoryLotEntity;
import org.example.sep26management.infrastructure.persistence.entity.QcInspectionEntity;
import org.example.sep26management.infrastructure.persistence.entity.QuarantineHoldEntity;
import org.example.sep26management.infrastructure.persistence.entity.SkuEntity;
import org.example.sep26management.infrastructure.persistence.entity.UserEntity;
import org.example.sep26management.infrastructure.persistence.repository.InventoryLotJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.QcInspectionJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.QuarantineHoldJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.SkuJpaRepository;
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
public class QcInspectionService {

    private final QcInspectionJpaRepository qcRepo;
    private final QuarantineHoldJpaRepository quarantineRepo;
    private final SkuJpaRepository skuRepo;
    private final UserJpaRepository userRepo;
    // BUG-07 FIX: inject InventoryLotJpaRepository để enrich lot → SKU info
    private final InventoryLotJpaRepository lotRepo;

    // ─── List QC Inspections ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<PageResponse<QcInspectionResponse>> listInspections(String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<QcInspectionEntity> inspectionsPage = status != null && !status.isBlank()
                ? qcRepo.findByStatusOrderByCreatedAtDesc(status, pageable)
                : qcRepo.findAllByOrderByCreatedAtDesc(pageable);

        List<QcInspectionResponse> content = inspectionsPage.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        PageResponse<QcInspectionResponse> pageResponse = PageResponse.<QcInspectionResponse>builder()
                .content(content)
                .page(inspectionsPage.getNumber())
                .size(inspectionsPage.getSize())
                .totalElements(inspectionsPage.getTotalElements())
                .totalPages(inspectionsPage.getTotalPages())
                .last(inspectionsPage.isLast())
                .build();

        return ApiResponse.success("OK", pageResponse);
    }

    // ─── Get QC Inspection Detail ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<QcInspectionResponse> getInspection(Long id) {
        // BUG-08 FIX: đổi RuntimeException → ResourceNotFoundException (HTTP 404)
        QcInspectionEntity inspection = qcRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("QC Inspection not found: " + id));
        return ApiResponse.success("OK", toResponse(inspection));
    }

    // ─── QC Submits Report (Bước 3 - QC Workspace) ─────────────────────────

    @Transactional
    public ApiResponse<QcInspectionResponse> submitReport(Long id, UpdateQcInspectionRequest request, Long qcUserId) {
        // BUG-08 FIX: đổi RuntimeException → ResourceNotFoundException (HTTP 404)
        QcInspectionEntity inspection = qcRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("QC Inspection not found: " + id));

        // BUG-08 FIX: đổi RuntimeException → BusinessException (HTTP 400)
        if (!"PENDING".equals(inspection.getStatus())) {
            throw new BusinessException(
                    "QC Inspection không ở trạng thái PENDING. Trạng thái hiện tại: " + inspection.getStatus());
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
        // BUG-08 FIX: đổi RuntimeException → ResourceNotFoundException (HTTP 404)
        QcInspectionEntity inspection = qcRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("QC Inspection not found: " + id));

        // BUG-08 FIX: đổi RuntimeException → BusinessException (HTTP 400)
        if (!"INSPECTED".equals(inspection.getStatus())) {
            throw new BusinessException(
                    "QC Inspection phải ở trạng thái INSPECTED trước khi ra quyết định. Trạng thái hiện tại: "
                            + inspection.getStatus());
        }

        String decision = request.getDecision().toUpperCase();
        // BUG-08 FIX: đổi RuntimeException → BusinessException (HTTP 400)
        if (!"SCRAP".equals(decision) && !"RETURN".equals(decision) && !"DOWNGRADE".equals(decision)) {
            throw new BusinessException("Quyết định không hợp lệ: " + decision
                    + ". Chỉ chấp nhận SCRAP, RETURN, hoặc DOWNGRADE.");
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

        // BUG-07 FIX: enrich lot → SKU info từ InventoryLotJpaRepository
        String lotNumber = null;
        Long skuId = null;
        String skuCode = null;
        String skuName = null;

        if (e.getLotId() != null) {
            InventoryLotEntity lot = lotRepo.findById(e.getLotId()).orElse(null);
            if (lot != null) {
                lotNumber = lot.getLotNumber();
                skuId = lot.getSkuId();
                if (skuId != null) {
                    SkuEntity sku = skuRepo.findById(skuId).orElse(null);
                    if (sku != null) {
                        skuCode = sku.getSkuCode();
                        skuName = sku.getSkuName();
                    }
                }
            }
        }

        return QcInspectionResponse.builder()
                .inspectionId(e.getInspectionId())
                .warehouseId(e.getWarehouseId())
                .lotId(e.getLotId())
                .inspectionCode(e.getInspectionCode())
                .status(e.getStatus())
                .lotNumber(lotNumber)
                .skuId(skuId)
                .skuCode(skuCode)
                .skuName(skuName)
                .inspectedBy(e.getInspectedBy())
                .inspectedByName(inspectedByName)
                .inspectedAt(e.getInspectedAt())
                .remarks(e.getRemarks())
                .attachmentId(e.getAttachmentId())
                .createdAt(e.getCreatedAt())
                .build();
    }
}