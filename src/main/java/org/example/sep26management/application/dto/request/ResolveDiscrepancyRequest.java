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
        @Schema(description = "Quyết định xử lý. "
                + "Nếu `reasonCode=SHORTAGE`: dùng `CLOSE_SHORT` (chốt thiếu) hoặc `WAIT_BACKORDER` (chờ giao bù). "
                + "Nếu `reasonCode=OVERAGE`: dùng `ACCEPT` (nhận thêm) hoặc `RETURN` (trả NCC).",
                example = "CLOSE_SHORT",
                allowableValues = { "CLOSE_SHORT", "WAIT_BACKORDER", "ACCEPT", "RETURN" })
        private String action;
    }
}
