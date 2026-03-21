package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.example.sep26management.application.enums.LocationType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * UC-LOC-05: View Location List - each record shows:
 * locationCode, locationType, parentLocationId, status
 * BR-LOC-16: parent-child relationships clearly indicated
 * BR-LOC-17: inactive locations visible but clearly marked
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LocationResponse {

    @Schema(description = "ID Location (Kệ/Tủ/Bin)", example = "10")
    private Long locationId;
    @Schema(description = "ID Kho Hệ Thống", example = "1")
    private Long warehouseId;
    @Schema(description = "ID Khu Vực (Zone)", example = "2")
    private Long zoneId;
    @Schema(description = "Mã Khu Vực", example = "ZONE-ELEC")
    private String zoneCode; // denormalized for display

    @Schema(description = "Mã số Kệ/Tủ/Bin", example = "BIN-A2")
    private String locationCode;
    @Schema(description = "Loại (RECEIVING_DOCK, STAGING, STORAGE, QC, REWORK...)", example = "STORAGE")
    private LocationType locationType;

    // Parent info — BR-LOC-16: show hierarchy
    @Schema(description = "ID của Khu chứa nó (Rack ID chứa Bin ID)", example = "4")
    private Long parentLocationId;
    @Schema(description = "Mã Khu chứa nó", example = "RACK-101")
    private String parentLocationCode; // denormalized for display

    @Schema(description = "Tải trọng Tối Đa", example = "100.0")
    private BigDecimal maxWeightKg;
    @Schema(description = "Thể tích Tối Đa", example = "2.5")
    private BigDecimal maxVolumeM3;
    @Schema(description = "Có cho phép Nhặt Hàng trực tiếp không", example = "true")
    private Boolean isPickingFace;
    @Schema(description = "Khu tập kết ngắn hạn không", example = "false")
    private Boolean isStaging;

    @Schema(description = "Khu hàng lỗi không", example = "false")
    private Boolean isDefect;

    @Schema(description = "Tầng BIN (1=dưới/512kg · 2=giữa/448kg · 3=trên/400kg). Null nếu AISLE/RACK.", example = "1")
    private Integer binFloor;

    @Schema(description = "Cột BIN (1=trái · 2=giữa · 3=phải). Null nếu AISLE/RACK.", example = "1")
    private Integer binColumn;

    @Schema(description = "Số thùng tối đa ước tính (max_weight_kg ÷ 16kg chuẩn)", example = "32")
    private Integer maxBoxCount;

    /** BR-LOC-17: always present — never hidden */
    @Schema(description = "Trạng Thái Hoạt Động", example = "true")
    private Boolean active;

    @Schema(description = "Thời gian Tạo", example = "2026-03-08T10:00:00")
    private LocalDateTime createdAt;
    @Schema(description = "Thời gian Cập Nhật", example = "2026-03-08T10:00:00")
    private LocalDateTime updatedAt;
}