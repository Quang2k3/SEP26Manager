package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QcInspectionResponse {

    private Long inspectionId;
    private Long warehouseId;
    private Long lotId;
    private String inspectionCode;
    private String status;

    // Lot info
    private String lotNumber;
    private Long skuId;
    private String skuCode;
    private String skuName;

    // Inspection details
    private Long inspectedBy;
    private String inspectedByName;
    private LocalDateTime inspectedAt;
    private String remarks;
    private Long attachmentId;

    // Manager decision
    private String decision; // SCRAP, RETURN, DOWNGRADE

    private LocalDateTime createdAt;
}
