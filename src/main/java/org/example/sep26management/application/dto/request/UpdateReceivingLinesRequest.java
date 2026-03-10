package org.example.sep26management.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateReceivingLinesRequest {

    @Schema(description = "Danh sách cập nhật chi tiết mặt hàng")
    @NotEmpty(message = "Lines cannot be empty")
    private List<LineItem> lines;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LineItem {
        @Schema(description = "ID Dòng sản phẩm trong phiếu nhập", example = "10")
        private Long receivingItemId;

        @Schema(description = "Tổng số thực đếm (Keeper scan)", example = "100.0")
        private BigDecimal receivedQty;

        @Schema(description = "Số hàng PASS (QC scan)", example = "90.0")
        private BigDecimal acceptedQty;

        @Schema(description = "Số hàng FAIL (QC scan)", example = "10.0")
        private BigDecimal damagedQty;

        @Schema(description = "Số hàng không nhận (nếu có)", example = "0.0")
        private BigDecimal rejectedQty;

        @Schema(description = "Lý do sai lệch nếu có (QC điền)", example = "Móp hộp, thiếu hàng")
        private String discrepancyReason;

        @Schema(description = "Ghi chú thêm", example = "")
        private String note;
    }
}
