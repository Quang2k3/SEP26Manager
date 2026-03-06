package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

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
    @NotBlank(message = "Category code is required")
    private String categoryCode;
}