package org.example.sep26management.application.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse {

    @Schema(description = "ID Hệ thống", example = "10")
    private Long categoryId;
    @Schema(description = "Mã Danh Mục", example = "CAT-ELEC")
    private String categoryCode;
    @Schema(description = "Tên", example = "Điện Tử")
    private String categoryName;
    @Schema(description = "ID Cha", example = "1")
    private Long parentCategoryId;
    @Schema(description = "Tên Danh Mục Cha", example = "Thiết Bị")
    private String parentCategoryName;
    @Schema(description = "Mô Tả", example = "Các thiết bị điện tử gia dụng")
    private String description;
    @Schema(description = "Kích hoạt", example = "true")
    private Boolean active;
    @Schema(description = "Ngày Tạo", example = "2026-03-08T10:00:00")
    private LocalDateTime createdAt;
    @Schema(description = "Cập Nhật Cuối", example = "2026-03-08T10:00:00")
    private LocalDateTime updatedAt;
}