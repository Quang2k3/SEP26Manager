package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignCategoryToSkuRequest {

    /**
     * Mã category (categoryCode) — FE lấy từ dropdown danh sách category.
     * BE tự tra cứu categoryId từ categoryCode này.
     * Ví dụ: "ELEC", "FOOD", "PHARMA"
     */
    @Schema(description = "Mã Category (của Danh Mục)", example = "CAT-ELEC")
    @NotBlank(message = "Category code is required")
    private String categoryCode;
}