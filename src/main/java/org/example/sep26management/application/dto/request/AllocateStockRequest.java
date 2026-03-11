package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.example.sep26management.application.enums.OutboundType;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AllocateStockRequest {

    @Schema(description = "Outbound ID đã APPROVED", example = "10")
    @NotNull(message = "Document ID is required")
    private Long documentId;

    @Schema(description = "Loại đơn", example = "SALES_ORDER",
            allowableValues = {"SALES_ORDER", "INTERNAL_TRANSFER"})
    @NotNull(message = "Order type is required")
    private OutboundType orderType;
}