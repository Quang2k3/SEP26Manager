package org.example.sep26management.application.dto.request;

import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApproveOutboundRequest {

    @Schema(description = "Ghi chú khi duyệt (tuỳ chọn)", example = "Ok, xuất ngay")
    private String note;
}