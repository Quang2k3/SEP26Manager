package org.example.sep26management.application.dto.response;

import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboundSummaryResponse {
    private long total;
    private long draft;
    private long pendingApproval;
    private long approved;
    private long allocated;
    private long picking;
    private long qcScan;
    private long dispatched;
    private long rejected;
}