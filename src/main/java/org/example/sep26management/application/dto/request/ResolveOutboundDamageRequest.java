package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Manager xử lý Incident DAMAGE của luồng QC Outbound.
 *
 * Actions:
 *  - RETURN_SCRAP : trả/huỷ hàng lỗi → trừ tồn → SO trở về PICKING để re-pick
 *  - ACCEPT       : chấp nhận xuất luôn hàng lỗi → SO tiếp tục → DISPATCHED
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ResolveOutboundDamageRequest {

    @Schema(description = "Action của Manager",
            allowableValues = {"RETURN_SCRAP", "ACCEPT"},
            example = "RETURN_SCRAP")
    @NotBlank(message = "action is required")
    private String action;

    @Schema(description = "Ghi chú của Manager (tùy chọn)", example = "Hàng bị ẩm do bảo quản sai")
    private String note;
}