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
        @Schema(description = "ID Dòng sản phẩm trong phiếu nhập. LẤY TỪ: attribute `items[].receivingItemId` của API GET /v1/receiving-orders/{id} hoặc mảng `items` khi POST tạo mới.", example = "10")
        private Long receivingItemId;

        @Schema(description = "Tổng số thực đếm (Keeper scan)", example = "100.0")
        private BigDecimal receivedQty;

        @Schema(description = "Ghi chú thêm", example = "")
        private String note;
    }
}
