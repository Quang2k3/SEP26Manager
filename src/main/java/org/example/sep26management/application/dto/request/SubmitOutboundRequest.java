// ===== SubmitOutboundRequest.java =====
package org.example.sep26management.application.dto.request;

import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * UC-OUT-03: Submit Outbound Order
 * BR-OUT-09: hard block if insufficient stock
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmitOutboundRequest {
    @Schema(description = "Ghi chú thêm khi Submit (Tùy chọn)", example = "Đã kiểm tra kỹ số lượng, xin sếp duyệt sớm.")
    private String note;
}