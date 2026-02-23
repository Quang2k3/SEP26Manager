package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MapCategoryToZoneRequest {

    @NotNull(message = "Warehouse ID is required")
    private Long warehouseId;
}