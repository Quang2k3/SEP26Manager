package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Request body for POST /v1/outbound/qc-scan
 *
 * result must be one of: PASS | FAIL | HOLD
 * reason is required only when result = FAIL (BR-QC-01)
 */
@Data
public class QcScanRequest {

    @NotNull(message = "pickingTaskId is required")
    private Long pickingTaskId;

    @NotNull(message = "pickingTaskItemId is required")
    private Long pickingTaskItemId;

    @NotBlank(message = "barcode is required")
    private String barcode;

    @NotBlank(message = "result is required")
    @Pattern(regexp = "PASS|FAIL|HOLD", message = "result must be PASS, FAIL or HOLD")
    private String result;

    /** Required when result = FAIL */
    private String reason;
}