package org.example.sep26management.application.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MapCategoryToZoneResponse {

    private Long categoryId;
    private String categoryCode;
    private String categoryName;

    private Long zoneId;
    private String zoneCode;
    private String zoneName;
    private Long warehouseId;

    private String conventionRule;  // "Z-" + categoryCode
    private Boolean zoneActive;
}