package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.example.sep26management.application.enums.IncidentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateIncidentRequest {

    @Schema(description = "ID Kho (Bắt buộc)", example = "1")
    @NotNull(message = "warehouseId is required")
    private Long warehouseId;

    @Schema(description = "Loại sự cố (Bắt buộc)", example = "DAMAGE", allowableValues = { "DAMAGE", "SHORTAGE",
            "OVERAGE",
            "SEAL_BROKEN", "SEAL_MISMATCH", "PACKAGING_DAMAGE", "OTHER" })
    @NotNull(message = "incidentType is required")
    private IncidentType incidentType;

    @Schema(description = "Mô tả sự cố (VD: \"Kẹp chì bị đứt, số seal không khớp với phiếu giao\") (Bắt buộc)", example = "Kẹp chì bị đứt")
    @NotBlank(message = "description is required")
    private String description;

    @Schema(description = "ID Lệnh nhập hàng liên quan (Có thể null)", example = "15")
    private Long receivingId;

    @Schema(description = "ID ảnh đính kèm (sau khi upload lên /attachments) (Có thể null)", example = "1001")
    private Long attachmentId;

    @Schema(description = "Danh sách sản phẩm bị lỗi (nếu có)")
    private List<IncidentItemDto> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IncidentItemDto {
        @Schema(description = "ID Sản phẩm bị lỗi", example = "150")
        @NotNull(message = "skuId is required")
        private Long skuId;

        @Schema(description = "Số lượng bị lỗi", example = "5.0")
        @NotNull(message = "damagedQty is required")
        private BigDecimal damagedQty;

        @Schema(description = "Mã lỗi chi tiết cho mặt hàng này", example = "TORN_PACKAGING")
        private String reasonCode;

        @Schema(description = "Ghi chú thêm cho mặt hàng này", example = "Thùng móp hỏng hoàn toàn")
        private String note;
    }
}
