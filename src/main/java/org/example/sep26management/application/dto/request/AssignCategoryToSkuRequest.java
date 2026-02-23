package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignCategoryToSkuRequest {

    @NotNull(message = "Category ID is required")
    private Long categoryId;
}