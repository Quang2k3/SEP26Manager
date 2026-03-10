package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResolveIncidentRequest {

    @Schema(description = "Ghi chú của Manager khi xử lý sự cố", example = "Đã kiểm tra, cho qua 2 chiếc, còn lại trả NCC")
    private String note;

    @Schema(description = "Danh sách quyết định xử lý cho từng item bị lỗi")
    @NotNull(message = "resolutions is required")
    private List<ResolutionItemDto> resolutions;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResolutionItemDto {

        @Schema(description = "ID của Incident Item", example = "10")
        @NotNull(message = "incidentItemId is required")
        private Long incidentItemId;

        @Schema(description = "Quyết định xử lý (PASS, RETURN, SCRAP, DOWNGRADE)", example = "RETURN")
        @NotNull(message = "action is required")
        private String action;

        @Schema(description = "Số lượng áp dụng quyết định này", example = "3.0")
        @NotNull(message = "quantity is required")
        private BigDecimal quantity;
    }
}
