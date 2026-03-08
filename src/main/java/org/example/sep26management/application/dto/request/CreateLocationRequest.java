package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.example.sep26management.application.enums.LocationType;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * UC-LOC-02: Create Location
 * BR-LOC-04: hierarchy Zone → Aisle → Rack → Bin
 * BR-LOC-05: location_code unique within zone
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateLocationRequest {

    @Schema(description = "ID Zone Cha", example = "1")
    @NotNull(message = "Zone ID is required")
    private Long zoneId;

    @Schema(description = "Mã Location/Tủ/Kệ/Aisle", example = "BIN-A2")
    @NotBlank(message = "Location Code is required")
    @Size(max = 100, message = "Location Code must not exceed 100 characters")
    private String locationCode;

    /** BR-LOC-04: AISLE | RACK | BIN */
    @Schema(description = "Phân loại vị trí", example = "BIN")
    @NotNull(message = "Location Type is required")
    private LocationType locationType;

    /**
     * Parent location ID — required for RACK and BIN
     * AISLE: null (directly under zone)
     * RACK: must point to an AISLE
     * BIN: must point to a RACK
     */
    @Schema(description = "ID Cha của vị trí này (Ví dụ RACK ID của BIN này)", example = "4")
    private Long parentLocationId;

    /** BR-LOC-07: optional capacity constraint for BIN */
    @Schema(description = "Tải trọng Tối Đa (Kg, tùy chọn cho BIN)", example = "100.0")
    @DecimalMin(value = "0.0", inclusive = false, message = "Max weight must be positive")
    private BigDecimal maxWeightKg;

    @Schema(description = "Thể tích Tối Đa (M3, tùy chọn cho BIN)", example = "1.5")
    @DecimalMin(value = "0.0", inclusive = false, message = "Max volume must be positive")
    private BigDecimal maxVolumeM3;

    @Schema(description = "Cho phép lấy hàng bằng tay (Pick Face)?", example = "true")
    private Boolean isPickingFace;

    @Schema(description = "Đóng vai trò điểm tập kết Staging?", example = "false")
    private Boolean isStaging;
}