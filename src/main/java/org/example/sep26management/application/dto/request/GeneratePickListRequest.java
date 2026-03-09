package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.example.sep26management.application.enums.OutboundType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * UC-WXE-06: Generate Pick List
 * BR-WXE-22: can only be generated from allocated stock
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneratePickListRequest {

    @Schema(description = "ID của Phiếu Yêu cầu xuất (Sales Order hoặc Transfer Order)", example = "10")
    @NotNull(message = "Document ID is required")
    private Long documentId;

    @Schema(description = "Loại yêu cầu: SALES_ORDER hoặc INTERNAL_TRANSFER", example = "SALES_ORDER")
    @NotNull(message = "Order type is required")
    private OutboundType orderType;

    @Schema(description = "Giao việc lấy hàng cho nhân viên nào? (Truyền User ID của Keeper/Picker)", example = "12")
    private Long assignedTo;
}