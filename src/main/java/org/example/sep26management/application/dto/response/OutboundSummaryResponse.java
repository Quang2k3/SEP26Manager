package org.example.sep26management.application.dto.response;

import lombok.*;

/**
 * UC-OUT-06: Summary cards shown on Outbound List screen
 * BR-OUT-24: default last 30 days
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OutboundSummaryResponse {
    private long total;
    private long pendingApproval;
    private long approved;       // awaiting shipment
    private long confirmedToday; // shipped today
    private long draft;
    private long rejected;
}