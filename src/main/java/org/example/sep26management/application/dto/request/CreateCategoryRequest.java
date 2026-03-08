package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCategoryRequest {

    @Schema(description = "Mã Danh Mục - Chỉ chập nhận viết Hoa, số và gạch dưới", example = "ELEC_01")
    @NotBlank(message = "Category code is required")
    @Size(max = 50, message = "Category code must not exceed 50 characters")
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "Category code must contain only uppercase letters, digits and underscores")
    private String categoryCode;

    @Schema(description = "Tên", example = "Điện tử gia dụng")
    @NotBlank(message = "Category name is required")
    @Size(max = 200, message = "Category name must not exceed 200 characters")
    private String categoryName;

    @Schema(description = "Danh mục cha (nhập ID nếu có, null nếu là category gốc)", example = "1")
    private Long parentCategoryId;

    @Schema(description = "Mô tả", example = "Thiết bị điện tử trong nhà")
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;
}