package org.example.sep26management.application.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Manager xử lý Incident DAMAGE của luồng QC Outbound cho từng item.
 *
 * Actions:
 *  - RETURN_SCRAP : trả/huỷ hàng lỗi → trừ tồn → SO trở về PICKING để re-pick
 *  - ACCEPT       : chấp nhận xuất luôn hàng lỗi → SO tiếp tục → DISPATCHED
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ResolveOutboundDamageRequest {

    @Schema(description = "Danh sách xử lý cho từng incident item")
    @NotEmpty(message = "Danh sách xử lý không được để trống")
    @Valid
    private List<ItemAction> itemResolutions;

    @Schema(description = "Ghi chú của Manager (tùy chọn)", example = "Hàng bị ẩm do bảo quản sai")
    private String note;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ItemAction {
        @Schema(description = "ID của incident item")
        @NotNull(message = "incidentItemId is required")
        private Long incidentItemId;

        @Schema(description = "Action của Manager", allowableValues = {"RETURN_SCRAP", "ACCEPT"})
        @NotBlank(message = "action is required")
        private String action;
    }
}