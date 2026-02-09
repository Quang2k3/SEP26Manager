package org.example.sep26management.application.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryZoneMappingResponse {

    private Long mappingId;

    // Category info
    private Long categoryId;
    private String categoryCode;
    private String categoryName;

    // Zone info
    private Long zoneId;
    private String zoneCode;
    private String zoneName;

    private Integer priority;
    private Boolean isActive;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
