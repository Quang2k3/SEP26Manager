package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QcScanRequest {

    @Schema(description = "ID của picking task", example = "5")
    @NotNull(message = "pickingTaskId is required")
    private Long pickingTaskId;

    @Schema(description = "ID của item cần scan QC", example = "12")
    @NotNull(message = "pickingTaskItemId is required")
    private Long pickingTaskItemId;

    @Schema(description = "Kết quả QC", example = "PASS",
            allowableValues = {"PASS", "FAIL", "HOLD"})
    @NotBlank(message = "result is required")
    @Pattern(regexp = "PASS|FAIL|HOLD", message = "result must be PASS, FAIL or HOLD")
    private String result;

    @Schema(description = "Lý do (bắt buộc nếu FAIL)", example = "Sản phẩm bị trầy xước")
    private String reason;
}