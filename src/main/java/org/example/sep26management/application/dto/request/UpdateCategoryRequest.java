package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCategoryRequest {

    @Schema(description = "Tên Danh Mục", example = "Điện tử gia dụng mới")
    @NotBlank(message = "Category name is required")
    @Size(max = 200, message = "Category name must not exceed 200 characters")
    private String categoryName;

    @Schema(description = "ID Danh mục cha (có thể null)", example = "2")
    private Long parentCategoryId;

    @Schema(description = "Mô tả ngắn", example = "Mô tả update")
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @Schema(description = "Hoạt động/Ngưng hoạt động", example = "true")
    private Boolean active;
}