package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QcDecisionRequest {

    /**
     * SCRAP = Tiêu hủy,
     * RETURN = Trả hàng về nhà máy,
     * DOWNGRADE = Thanh lý / Bán hạ cấp
     */
    @NotBlank(message = "decision is required (SCRAP, RETURN, DOWNGRADE)")
    @Schema(description = "Quyết định xử lý", example = "RETURN", allowableValues = { "SCRAP", "RETURN", "DOWNGRADE" })
    private String decision;

    /** Ghi chú thêm của Manager */
    @Schema(description = "Ghi chú thêm của Manager", example = "Hàng bị lỗi vỏ hộp nhẹ, có thể bán hạ cấp xuống hàng Loại B.")
    private String note;
}
