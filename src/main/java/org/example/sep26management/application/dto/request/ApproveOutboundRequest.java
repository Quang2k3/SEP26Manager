package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * UC-OUT-04: Approve Outbound Order
 * BR-OUT-16: final stock check before committing
 * BR-OUT-17: reserve inventory on approval
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApproveOutboundRequest {

    /** true = Approve, false = Reject */
    private boolean approved;

    /** Required if rejected — min 20 chars per SRS */
    @Size(min = 20, message = "Rejection explanation must be at least 20 characters")
    private String rejectionReason;

    /** INSUFFICIENT_STOCK | INVALID_CUSTOMER | BUDGET_CONSTRAINT | OTHER */
    private String rejectionCode;

    /** Optional approval notes */
    private String note;
}