package org.example.sep26management.application.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceivingOrderResponse {

    private Long receivingId;
    private Long warehouseId;
    private String receivingCode;
    private String status;
    private String sourceType;
    private Long supplierId;
    private String sourceReferenceCode;
    private String note;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private List<ReceivingItemResponse> items;
}
