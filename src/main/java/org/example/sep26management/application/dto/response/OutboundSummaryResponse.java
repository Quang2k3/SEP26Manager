package org.example.sep26management.application.dto.response;

import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * UC-OUT-06: Summary cards shown on Outbound List screen
 * BR-OUT-24: default last 30 days
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboundSummaryResponse {
    @Schema(description = "Tổng số phiếu", example = "150")
    private long total;

    @Schema(description = "Số phiếu chờ duyệt (PENDING_APPROVAL)", example = "10")
    private long pendingApproval;

    @Schema(description = "Số phiếu đã duyệt (APPROVED)", example = "20")
    private long approved;

    @Schema(description = "Số phiếu đã phân bổ tồn kho (ALLOCATED)", example = "5")
    private long allocated;

    @Schema(description = "Số phiếu đang lấy hàng (PICKING)", example = "3")
    private long picking;

    @Schema(description = "Số phiếu đang QC (QC_SCAN)", example = "2")
    private long qcScan;

    @Schema(description = "Số phiếu đã xuất kho (DISPATCHED)", example = "5")
    private long dispatched;

    @Schema(description = "Số phiếu Nháp (DRAFT)", example = "30")
    private long draft;

    @Schema(description = "Số phiếu đã từ chối (REJECTED)", example = "2")
    private long rejected;
}