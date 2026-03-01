// ===== SubmitOutboundRequest.java =====
package org.example.sep26management.application.dto.request;

import lombok.*;

/**
 * UC-OUT-03: Submit Outbound Order
 * BR-OUT-09: hard block if insufficient stock
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SubmitOutboundRequest {
    /** Optional submission notes */
    private String note;
}