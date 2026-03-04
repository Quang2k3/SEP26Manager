package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.example.sep26management.application.enums.OutboundType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * UC-OUT-06: One row in the outbound list table
 * Fields per SRS: documentCode, type, destination, status, totalItems,
 *                 totalQty, createdBy, createdDate, shipmentDate, actions
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OutboundListResponse {

    private Long documentId;
    private String documentCode;
    private OutboundType orderType;

    // Destination — customer name (SALES) or warehouse name (TRANSFER)
    private String destination;

    private String status;
    private int totalItems;
    private BigDecimal totalQty;

    private Long createdBy;
    private String createdByName;
    private LocalDateTime createdAt;

    // Delivery date (SALES) or transfer date (TRANSFER)
    private LocalDate shipmentDate;

    // BR-OUT-26: available actions based on role + status
    private boolean canEdit;       // DRAFT + creator
    private boolean canDelete;     // DRAFT + creator
    private boolean canSubmit;     // DRAFT + creator
    private boolean canApprove;    // PENDING + MANAGER
    private boolean canConfirm;    // APPROVED + KEEPER
}