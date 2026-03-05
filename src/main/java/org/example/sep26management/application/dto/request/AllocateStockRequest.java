package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.example.sep26management.application.enums.OutboundType;

/**
 * UC-WXE-05: Allocate / Reserve Stock
 * Triggered manually by Keeper or auto-triggered after approval
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AllocateStockRequest {

    @NotNull(message = "Document ID is required")
    private Long documentId;

    @NotNull(message = "Order type is required")
    private OutboundType orderType; // SALES_ORDER or INTERNAL_TRANSFER
}