package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReceivingOrderResponse {

    private Long receivingId;
    private String receivingCode;
    private String status;

    // Warehouse
    private Long warehouseId;
    private String warehouseName;

    // Supplier
    private Long supplierId;
    private String supplierName;

    private String sourceType;
    private String sourceReferenceCode;
    private String note;

    // Created by
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Approve / Confirm
    private Long approvedBy;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private Long confirmedBy;
    private String confirmedByName;
    private LocalDateTime confirmedAt;

    // Summary
    private Integer totalLines;
    private BigDecimal totalQty;

    private List<ReceivingItemResponse> items;
}
