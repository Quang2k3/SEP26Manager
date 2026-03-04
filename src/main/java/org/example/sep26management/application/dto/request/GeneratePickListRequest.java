package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.example.sep26management.application.enums.OutboundType;

/**
 * UC-WXE-06: Generate Pick List
 * BR-WXE-22: can only be generated from allocated stock
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GeneratePickListRequest {

    @NotNull(message = "Document ID is required")
    private Long documentId;

    @NotNull(message = "Order type is required")
    private OutboundType orderType;

    /** Optional: assign pick list to a specific keeper */
    private Long assignedTo;
}