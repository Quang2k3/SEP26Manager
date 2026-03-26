package org.example.sep26management.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

/**
 * Request cho Manager xử lý sai lệch số lượng (thừa/thiếu) theo từng item.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request xử lý sai lệch số lượng. Mỗi item cần 1 action riêng.")
public class ResolveDiscrepancyRequest {

    @NotEmpty(message = "At least one item resolution is required")
    @Valid
    @Schema(description = "Danh sách quyết định cho từng item. Lấy `incidentItemId` từ `GET /v1/incidents/{id}` → `items[].incidentItemId`")
    private List<ItemResolution> items;

    @Schema(description = "Ghi chú của Manager (tùy chọn)", example = "Đã liên hệ NCC về phần thiếu")
    private String note;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemResolution {

        @NotNull(message = "incidentItemId is required")
        @Schema(description = "ID item sự cố. LẤY TỪ: `GET /v1/incidents/{id}` → `items[].incidentItemId`",
                example = "11")
        private Long incidentItemId;

        @NotBlank(message = "action is required")
        @Schema(description = "Quyết định xử lý chính (số lượng thừa/thiếu). "
                + "Nếu `reasonCode=SHORTAGE`: dùng `CLOSE_SHORT` hoặc `WAIT_BACKORDER`. "
                + "Nếu `reasonCode=OVERAGE`: dùng `ACCEPT` hoặc `RETURN`.",
                example = "ACCEPT",
                allowableValues = { "CLOSE_SHORT", "WAIT_BACKORDER", "ACCEPT", "RETURN", "SCRAP" })
        private String action;

        @Schema(description = "Quyết định xử lý hàng hỏng (tùy chọn). "
                + "Chỉ dùng khi item vừa có thừa/thiếu VÀ có hàng hỏng. "
                + "Nếu chỉ có 1 vấn đề, dùng `action` là đủ.",
                example = "RETURN",
                allowableValues = { "RETURN", "SCRAP", "ACCEPT" })
        private String damageAction;
    }
}
