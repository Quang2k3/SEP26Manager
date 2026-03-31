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
    private final NotificationService notificationService;

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

        // ── Realtime: notify MANAGER + KEEPER có sự cố mới chưa xử lý ────────
        String desc = request.getDescription() != null
                ? request.getDescription() : request.getIncidentType().name();
        notificationService.notifyRoles(new String[]{"MANAGER", "QC", "KEEPER"}, "incident_open",
                saved.getIncidentId(), saved.getIncidentCode(), desc);

        return ApiResponse.success("Incident reported successfully", toResponse(saved));
    }

    // ─── List Incidents ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<PageResponse<IncidentResponse>> listIncidents(String status,
                                                                     org.example.sep26management.application.enums.IncidentCategory category,
                                                                     Long soId,
                                                                     Long receivingId,
                                                                     int page, int size) {
        // [FIX] receivingId filter — inbound QC incidents
        if (receivingId != null) {
            List<IncidentResponse> list = incidentRepo
                    .findByReceivingIdOrderByCreatedAtDesc(receivingId)
                    .stream().map(this::toResponse).collect(Collectors.toList());
            PageResponse<IncidentResponse> p = PageResponse.<IncidentResponse>builder()
                    .content(list).page(0).size(list.size())
                    .totalElements(list.size()).totalPages(1).last(true).build();
            return ApiResponse.success("OK", p);
        }

        // [BUG-FIX] Khi soId != null: chỉ trả incidents của SO đó.
        // Trước đây soId không được xử lý → GET /incidents?soId=X trả về tất cả
        // → banner ON_HOLD hiển thị incidents của đơn khác, không đúng đơn.
        if (soId != null) {
            List<IncidentResponse> soIncidents = incidentRepo
                    .findAllBySoIdOrderByCreatedAtDesc(soId)
                    .stream().map(this::toResponse).collect(Collectors.toList());
            PageResponse<IncidentResponse> soPage = PageResponse.<IncidentResponse>builder()
                    .content(soIncidents).page(0).size(soIncidents.size())
                    .totalElements(soIncidents.size()).totalPages(1).last(true).build();
            return ApiResponse.success("OK", soPage);
        }

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

        // ── Realtime: notify KEEPER incident được duyệt, có thể tiếp tục dỡ hàng
        notificationService.notifyRoles(new String[]{"MANAGER", "QC", "KEEPER"}, "incident_open",
                incident.getIncidentId(), incident.getIncidentCode(),
                "Incident đã duyệt — tiếp tục nhận hàng");

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

        // ── Realtime: notify KEEPER incident bị từ chối ───────────────────────
        notificationService.notifyRoles(new String[]{"MANAGER", "QC", "KEEPER"}, "incident_open",
                incident.getIncidentId(), incident.getIncidentCode(),
                "Incident bị từ chối — " + (reason != null ? reason : ""));

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

                java.math.BigDecimal effectiveQty = res.getQuantity();
                if (effectiveQty == null || effectiveQty.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                    if ("UNEXPECTED_ITEM".equals(item.getReasonCode())) {
                        effectiveQty = item.getActualQty();
                    } else {
                        effectiveQty = item.getDamagedQty();
                    }
                }

                if ("PASS".equalsIgnoreCase(res.getAction()) || "ACCEPT".equalsIgnoreCase(res.getAction())) {
                    // ACCEPT = Manager chấp nhận nhận hàng hỏng vào kho (ghi nhận như PASS)
                    if (item.getActionPassQty() == null)
                        item.setActionPassQty(java.math.BigDecimal.ZERO);
                    item.setActionPassQty(item.getActionPassQty().add(effectiveQty));
                } else if ("RETURN".equalsIgnoreCase(res.getAction())) {
                    if (item.getActionReturnQty() == null)
                        item.setActionReturnQty(java.math.BigDecimal.ZERO);
                    item.setActionReturnQty(item.getActionReturnQty().add(effectiveQty));
                }

                // Cập nhật lại note/reason cho item dựa trên phán quyết của manager
                item.setNote(item.getNote() != null
                        ? item.getNote() + " | [Manager Decision]: " + res.getAction() + " (Qty: " + effectiveQty
                        + ")"
                        : "[Manager Decision]: " + res.getAction() + " (Qty: " + effectiveQty + ")");
                incidentItemRepo.save(item);
            }
        }

        incident.setStatus("RESOLVED");
        if (request.getNote() != null && !request.getNote().isBlank()) {
            incident.setDescription(incident.getDescription() + "\n[Manager Resolution Note] " + request.getNote());
        }
        incidentRepo.save(incident);

        // [FIX] Update receiving order status theo action của Manager
        // Chỉ áp dụng khi incident có receivingId (inbound DAMAGE từ QC scanner)
        if (incident.getReceivingId() != null) {
            // Phân tích TẤT CẢ resolutions (không chỉ action đầu tiên)
            // Case: hàng ngoài phiếu RETURN + hàng trên phiếu PASS → đơn vẫn tiếp tục
            boolean hasReturn = false;
            boolean hasAcceptOrPass = false;

            if (request.getResolutions() != null) {
                for (org.example.sep26management.application.dto.request.ResolveIncidentRequest.ResolutionItemDto res : request.getResolutions()) {
                    String action = res.getAction().toUpperCase();
                    if ("RETURN".equals(action)) {
                        hasReturn = true;
                    } else { // ACCEPT, PASS
                        hasAcceptOrPass = true;
                    }
                }
            }

            // REJECTED chỉ khi toàn bộ item đều RETURN (không có ACCEPT)
            // Nếu có ít nhất 1 ACCEPT → đơn tiếp tục QC_APPROVED
            String newOrderStatus;
            String wsMessage;
            if (hasReturn && !hasAcceptOrPass) {
                // Toàn bộ incident items đều RETURN —
                // Tính tổng qty còn lại trên TẤT CẢ receiving items sau khi trừ returnQty
                // (bao gồm cả SKU của incident, vì có thể chỉ RETURN 1 phần, phần còn lại vẫn pass)

                // Build map: skuId → tổng actionReturnQty từ incident này
                java.util.Map<Long, java.math.BigDecimal> returnQtyBySkuId = incident.getItems().stream()
                        .filter(ii -> ii.getActionReturnQty() != null
                                && ii.getActionReturnQty().compareTo(java.math.BigDecimal.ZERO) > 0)
                        .collect(java.util.stream.Collectors.toMap(
                                IncidentItemEntity::getSkuId,
                                IncidentItemEntity::getActionReturnQty,
                                java.math.BigDecimal::add));

                // Tính tổng qty còn lại sau khi trừ return — trên toàn bộ receiving items
                java.math.BigDecimal totalRemainingQty = receivingItemRepo
                        .findByReceivingOrderReceivingId(incident.getReceivingId()).stream()
                        .map(ri -> {
                            java.math.BigDecimal rQty = ri.getReceivedQty() != null
                                    ? ri.getReceivedQty() : java.math.BigDecimal.ZERO;
                            java.math.BigDecimal returnedQty = returnQtyBySkuId.getOrDefault(
                                    ri.getSkuId(), java.math.BigDecimal.ZERO);
                            java.math.BigDecimal remaining = rQty.subtract(returnedQty);
                            return remaining.compareTo(java.math.BigDecimal.ZERO) > 0
                                    ? remaining : java.math.BigDecimal.ZERO;
                        })
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

                if (totalRemainingQty.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    // Còn hàng hợp lệ sau khi trừ return → QC_APPROVED, chỉ loại bỏ phần RETURN
                    newOrderStatus = "QC_APPROVED";
                    wsMessage = "Hàng hỏng hoàn NCC, hàng hợp lệ tiếp tục tạo GRN";
                } else {
                    // Không còn hàng hợp lệ nào → REJECTED
                    newOrderStatus = "REJECTED";
                    wsMessage = "Toàn bộ hàng hoàn về NCC — đơn bị từ chối";
                }
            } else {
                // Có ít nhất 1 ACCEPT → QC_APPROVED
                newOrderStatus = "QC_APPROVED";
                wsMessage = hasReturn
                        ? "Một phần hàng hoàn NCC, phần còn lại tiếp tục tạo GRN"
                        : "Hàng được chấp nhận — đơn chuyển tạo GRN";
            }

            final String finalStatus = newOrderStatus;
            final String finalMsg    = wsMessage;
            final boolean finalHasReturn = hasReturn;
            receivingOrderRepo.findById(incident.getReceivingId()).ifPresent(order -> {
                if ("PENDING_INCIDENT".equals(order.getStatus())) {
                    order.setStatus(finalStatus);
                    order.setUpdatedAt(java.time.LocalDateTime.now());
                    if ("REJECTED".equals(finalStatus)) {
                        order.setRejectedBy(managerId);
                        order.setRejectedAt(java.time.LocalDateTime.now());
                        order.setRejectReason("Manager quyết định hoàn toàn bộ hàng. Incident: "
                                + incident.getIncidentCode());
                    }

                    // Trừ receivedQty cho item RETURN → loại khỏi GRN
                    if (finalHasReturn && !"REJECTED".equals(finalStatus)) {
                        for (IncidentItemEntity iItem : incident.getItems()) {
                            if (iItem.getActionReturnQty() != null
                                    && iItem.getActionReturnQty().compareTo(java.math.BigDecimal.ZERO) > 0) {
                                receivingItemRepo
                                    .findByReceivingOrderReceivingIdAndSkuId(order.getReceivingId(), iItem.getSkuId())
                                    .ifPresent(ri -> {
                                        java.math.BigDecimal newQty = ri.getReceivedQty() != null
                                                ? ri.getReceivedQty().subtract(iItem.getActionReturnQty())
                                                : java.math.BigDecimal.ZERO;
                                        if (newQty.compareTo(java.math.BigDecimal.ZERO) < 0)
                                            newQty = java.math.BigDecimal.ZERO;
                                        ri.setReceivedQty(newQty);
                                        // [FIX] Chỉ đánh dấu RETURNED nếu hết hàng hoàn toàn
                                        // Nếu còn qty (partial return) → giữ condition cũ để GRN vẫn nhận phần pass
                                        if (newQty.compareTo(java.math.BigDecimal.ZERO) == 0) {
                                            ri.setCondition("RETURNED");
                                        }
                                        receivingItemRepo.save(ri);
                                        log.info("Returned item: SKU {} returnQty={} remainingQty={} on order {}",
                                                iItem.getSkuId(), iItem.getActionReturnQty(),
                                                newQty, order.getReceivingCode());
                                    });
                            }
                        }
                    }

                    receivingOrderRepo.save(order);
                    log.info("Receiving order {} → {} after incident {} resolved",
                            order.getReceivingCode(), finalStatus, incident.getIncidentCode());
                }
                notificationService.notifyRoles(new String[]{"MANAGER", "QC", "KEEPER"}, "receiving_updated",
                        incident.getIncidentId(), incident.getIncidentCode(), finalMsg);
            });
        } else {
            // Không có receivingId (gate check incident) — chỉ notify
            notificationService.notifyRoles(new String[]{"MANAGER", "QC", "KEEPER"}, "receiving_updated",
                    incident.getIncidentId(), incident.getIncidentCode(), "Incident đã xử lý xong");
        }

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
                .equals(incident.getIncidentType())) {
            throw new RuntimeException(
                    "This API is only for resolving quantity discrepancy incidents (SHORTAGE/OVERAGE).");
        }

        ReceivingOrderEntity order = receivingOrderRepo.findById(incident.getReceivingId())
                .orElseThrow(() -> new RuntimeException("Receiving order not found: " + incident.getReceivingId()));

        boolean hasWaitBackorder = false;

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
                    // Chờ giao bù: giữ nguyên expectedQty, đánh dấu chờ
                    hasWaitBackorder = true;
                    incItem.setNote(appendNote(incItem.getNote(),
                            "[Manager]: WAIT_BACKORDER — Chờ giao bù cho phần thiếu"));
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
                    // Trả hàng thừa: trừ phần thừa khỏi receivedQty → chỉ nhận đúng expectedQty
                    if (rcItem != null) {
                        // damagedQty = overageQty (số lượng thừa cần hoàn)
                        java.math.BigDecimal overageQty = incItem.getDamagedQty() != null
                                ? incItem.getDamagedQty() : java.math.BigDecimal.ZERO;
                        incItem.setActionReturnQty(overageQty);

                        // receivedQty sau hoàn = receivedQty hiện tại - overageQty
                        java.math.BigDecimal newReceivedQty = rcItem.getReceivedQty() != null
                                ? rcItem.getReceivedQty().subtract(overageQty)
                                : java.math.BigDecimal.ZERO;
                        if (newReceivedQty.compareTo(java.math.BigDecimal.ZERO) < 0)
                            newReceivedQty = java.math.BigDecimal.ZERO;
                        rcItem.setReceivedQty(newReceivedQty);

                        // Nếu receivedQty = 0 (UNEXPECTED_ITEM hoàn toàn) → đánh dấu RETURNED
                        if (newReceivedQty.compareTo(java.math.BigDecimal.ZERO) == 0) {
                            rcItem.setCondition("RETURNED");
                        }
                        receivingItemRepo.save(rcItem);
                        log.info("OVERAGE RETURN: SKU {} overageQty={} remainingReceivedQty={} on order {}",
                                incItem.getSkuId(), overageQty, newReceivedQty, order.getReceivingCode());
                    }
                    incItem.setNote(appendNote(incItem.getNote(),
                            "[Manager]: RETURN — Trả hàng thừa cho nhà cung cấp"));
                    break;

                default:
                    throw new IllegalArgumentException(
                            "Invalid action: " + action
                                    + ". Must be CLOSE_SHORT, WAIT_BACKORDER, ACCEPT, or RETURN");
            }

            incidentItemRepo.save(incItem);
        }

        // Resolve incident and move order — BE-C3 FIX: chỉ chuyển SUBMITTED nếu KHÔNG có WAIT_BACKORDER
        incident.setStatus("RESOLVED");
        if (request.getNote() != null && !request.getNote().isBlank()) {
            incident.setDescription(
                    incident.getDescription() + "\n[Manager Resolution Note] " + request.getNote());
        }
        incidentRepo.save(incident);

        if (hasWaitBackorder) {
            // BE-C3 FIX: Còn item đang chờ giao bù → giữ PENDING_INCIDENT, không QC approve vội
            // Order sẽ chuyển SUBMITTED khi supplier giao bù và Keeper scan lại
            order.setStatus("PENDING_INCIDENT");
            log.info("Discrepancy Incident {} resolved with WAIT_BACKORDER — order stays PENDING_INCIDENT (receivingId={})",
                    incident.getIncidentCode(), order.getReceivingId());
        } else {
            // Tất cả item đã xử lý dứt điểm (CLOSE_SHORT / ACCEPT / RETURN) → cho QC tiếp tục
            order.setStatus("SUBMITTED");
            log.info("Discrepancy Incident {} fully resolved — order moved to SUBMITTED (receivingId={})",
                    incident.getIncidentCode(), order.getReceivingId());
        }
        receivingOrderRepo.save(order);

        // ── Realtime: notify theo trạng thái kết quả ─────────────────────────
        if (hasWaitBackorder) {
            // Vẫn còn chờ → notify MANAGER + KEEPER để biết
            notificationService.notifyRoles(new String[]{"MANAGER", "QC", "KEEPER"}, "incident_open",
                    incident.getIncidentId(), incident.getIncidentCode(),
                    order.getReceivingCode() + " — Chờ giao bù hàng thiếu");
        } else {
            // Xử lý xong → QC tiếp tục kiểm đếm
            notificationService.notifyRoles(new String[]{"MANAGER", "QC", "KEEPER"}, "receiving_pending_qc",
                    order.getReceivingId(), order.getReceivingCode(),
                    "Discrepancy đã xử lý — tiếp tục QC");
        }

        log.info("Discrepancy Incident {} resolved by managerId={}, hasWaitBackorder={}",
                incident.getIncidentCode(), managerId, hasWaitBackorder);
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

    public IncidentResponse toResponse(IncidentEntity e) {
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
                    // [FIX QC] Trả attachmentUrl — FE hiển ảnh trong IncidentDetailModal
                    .attachmentUrl(item.getAttachmentUrl())
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
                .soId(e.getSoId())
                .createdAt(e.getCreatedAt())
                .items(itemResponses)
                .build();
    }
}