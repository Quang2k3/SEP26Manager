package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCategoryZoneMappingRequest {

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    @NotNull(message = "Zone ID is required")
    private Long zoneId;

    private Integer priority; // Default = 1

    private Boolean isActive; // Default = true
}
