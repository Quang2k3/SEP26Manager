package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

/**
 * UC-B08: Import SKU Result
 * Summary: totalRows, successCount, errorCount + error detail list
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImportSkuResultResponse {

    private int totalRows;
    private int successCount;
    private int errorCount;
    private int duplicateCount;

    private List<RowError> errors;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RowError {
        private int rowNumber;
        private String skuCode;
        private String reason;
    }
}