package org.example.sep26management.application.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * Response for GET /v1/outbound/pick-list/{taskId}/qc-summary
 *
 * pendingCount = items where qc_scanned_at IS NULL
 */
@Data
@Builder
public class QcSummaryResponse {
    private Long pickingTaskId;
    private int totalItems;
    private int passCount;
    private int failCount;
    private int holdCount;
    private int pendingCount;
}