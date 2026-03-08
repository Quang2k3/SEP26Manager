package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.Size;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * UC-OUT-04: Approve Outbound Order
 * BR-OUT-16: final stock check before committing
 * BR-OUT-17: reserve inventory on approval
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApproveOutboundRequest {

    @Schema(description = "Hành động: true = Approve (Duyệt), false = Reject (Từ chối)", example = "true")
    private boolean approved;

    @Schema(description = "Bắt buộc nếu Từ chối (Tối thiểu 20 ký tự giải thích lý do)", example = "Tồn kho không đủ để xuất, cần chờ lô hàng tiếp theo nhập kho vào mai.")
    @Size(min = 20, message = "Rejection explanation must be at least 20 characters")
    private String rejectionReason;

    @Schema(description = "Mã lỗi khi từ chối: INSUFFICIENT_STOCK | INVALID_CUSTOMER | BUDGET_CONSTRAINT | OTHER", example = "INSUFFICIENT_STOCK")
    private String rejectionCode;

    @Schema(description = "Ghi chú thêm lúc duyệt", example = "Ok duyệt, xuất nhanh luôn.")
    private String note;
}