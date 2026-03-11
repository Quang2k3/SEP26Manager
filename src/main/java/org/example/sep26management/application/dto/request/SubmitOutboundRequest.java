package org.example.sep26management.application.dto.request;

import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SubmitOutboundRequest {

    @Schema(description = "Ghi chú khi submit (tuỳ chọn)", example = "Xin sếp duyệt sớm")
    private String note;
}