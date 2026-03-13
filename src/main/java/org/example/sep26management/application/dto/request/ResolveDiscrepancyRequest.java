package org.example.sep26management.application.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

/**
 * Request cho Manager xử lý sai lệch số lượng (thừa/thiếu) theo từng item.
 *
 * Actions:
 * - SHORTAGE items: CLOSE_SHORT (chốt thiếu) | WAIT_BACKORDER (chờ giao bù)
 * - OVERAGE items: ACCEPT (nhận thêm) | RETURN (trả hàng thừa)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveDiscrepancyRequest {

    @NotEmpty(message = "At least one item resolution is required")
    @Valid
    private List<ItemResolution> items;

    /** Optional manager note */
    private String note;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemResolution {

        @NotNull(message = "incidentItemId is required")
        private Long incidentItemId;

        /**
         * Action:
         * - CLOSE_SHORT: Chốt thiếu (SHORTAGE)
         * - WAIT_BACKORDER: Chờ giao bù (SHORTAGE)
         * - ACCEPT: Nhận hàng thừa (OVERAGE)
         * - RETURN: Trả hàng thừa (OVERAGE)
         */
        @NotBlank(message = "action is required")
        private String action;
    }
}
