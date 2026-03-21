package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.example.sep26management.application.enums.OutboundType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OutboundListResponse {
    private Long documentId;
    private String documentCode;
    private OutboundType orderType;
    private String destination;
    private String status;
    private Long warehouseId;
    private int totalItems;
    private BigDecimal totalQty;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDate shipmentDate;
    private boolean canEdit;
    private boolean canDelete;
    private boolean canSubmit;
    private boolean canApprove;
    private boolean canConfirm;
    // [FIX TC-1A] true khi đơn DRAFT có ít nhất 1 SKU thiếu tồn kho
    // FE đọc field này để render ngay — không cần chờ GET /outbound/{id}
    private boolean hasStockShortage;
}