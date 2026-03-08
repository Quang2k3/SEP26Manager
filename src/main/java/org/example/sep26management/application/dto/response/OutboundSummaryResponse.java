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

    @Schema(description = "Số phiếu chờ duyệt (SUBMITTED)", example = "10")
    private long pendingApproval;

    @Schema(description = "Số phiếu đã duyệt đang lấy hàng (APPROVED)", example = "20")
    private long approved; // awaiting shipment

    @Schema(description = "Số phiếu Giao Hàng Xong trong hôm nay", example = "5")
    private long confirmedToday; // shipped today

    @Schema(description = "Số phiếu Nháp (DRAFT)", example = "30")
    private long draft;

    @Schema(description = "Số phiếu đã huỷ/từ chối", example = "2")
    private long rejected;
}