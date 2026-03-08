package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.example.sep26management.application.enums.IncidentType;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IncidentResponse {

    private Long incidentId;
    private Long warehouseId;
    private String incidentCode;
    private IncidentType incidentType;
    private String severity;
    private LocalDateTime occurredAt;
    private String description;
    private Long reportedBy;
    private String reportedByName;
    private Long attachmentId;
    private String status;
    private Long receivingId;
    private String receivingCode;
    private LocalDateTime createdAt;
}
