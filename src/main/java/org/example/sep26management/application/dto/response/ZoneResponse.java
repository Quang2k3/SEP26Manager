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
public class ZoneResponse {

    private Long zoneId;
    private Long warehouseId;
    private String zoneCode;
    private String zoneName;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}