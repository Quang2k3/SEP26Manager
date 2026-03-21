package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SkuResponse {
    @Schema(description = "ID Hệ Thống", example = "100")
    private Long skuId;
    @Schema(description = "Mã SKU", example = "SKU-IP15")
    private String skuCode;
    @Schema(description = "Tên SKU", example = "Iphone 15 Pro")
    private String skuName;
    @Schema(description = "Mô tả SP", example = "Màu đen, 256GB")
    private String description;
    @Schema(description = "Thương Hiệu", example = "Apple")
    private String brand;
    @Schema(description = "Mã Barcode SP Đóng Gói (Nhà Sản Xuất)", example = "8931234567890")
    private String barcode;
    @Schema(description = "Đơn vị tính", example = "Cái")
    private String unit;
    @Schema(description = "Loại đóng gói", example = "Hộp")
    private String packageType;
    @Schema(description = "Thể Tích", example = "500.0")
    private BigDecimal volumeMl;
    @Schema(description = "Cân nặng", example = "250.0")
    private BigDecimal weightG;

    /** Trọng lượng 1 thùng (kg) — dùng để tính putaway + hiển thị trên form nhập hàng */
    private BigDecimal weightPerCartonKg;
    /** Số đơn vị lẻ trong 1 thùng (VD: 4 can, 12 chai) */
    private Integer unitsPerCarton;
    @Schema(description = "Xuất xứ", example = "USA")
    private String originCountry;
    @Schema(description = "Mùi (Nếu có)", example = "Không mùi")
    private String scent;
    @Schema(description = "URL Ảnh SP", example = "https://image.com/ip15.png")
    private String imageUrl;
    @Schema(description = "HSD Tính Theo Ngày", example = "365")
    private Integer shelfLifeDays;
    @Schema(description = "Nhiệt độ tối thiểu", example = "15.0")
    private BigDecimal storageTempMin;
    @Schema(description = "Nhiệt tối đa", example = "25.0")
    private BigDecimal storageTempMax;
    @Schema(description = "Trạng Thái", example = "true")
    private Boolean active;

    // Category info
    @Schema(description = "ID Danh Mục", example = "10")
    private Long categoryId;
    @Schema(description = "Mã Danh Mục", example = "CAT-ELEC")
    private String categoryCode;
    @Schema(description = "Tên Danh Mục", example = "Điện Tử")
    private String categoryName;
    @Schema(description = "Ngày Tạo", example = "2026-03-08T10:00:00")
    private LocalDateTime createdAt;
    @Schema(description = "Ngày Sửa", example = "2026-03-08T10:00:00")
    private LocalDateTime updatedAt;
}