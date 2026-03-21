package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Manager xử lý Incident SHORTAGE của luồng Outbound (Case 1).
 *
 * Actions:
 *  - WAIT_BACKORDER : giữ SO ở WAITING_STOCK, chờ nhập hàng bù rồi re-Allocate
 *  - CLOSE_SHORT    : chấp nhận số lượng hiện có, re-Allocate với qty thực tế
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ResolveOutboundShortageRequest {

    @Schema(description = "Action của Manager",
            allowableValues = {"WAIT_BACKORDER", "CLOSE_SHORT"},
            example = "CLOSE_SHORT")
    @NotBlank(message = "action is required")
    private String action;

    @Schema(description = "Ghi chú của Manager (tùy chọn)")
    private String note;
}