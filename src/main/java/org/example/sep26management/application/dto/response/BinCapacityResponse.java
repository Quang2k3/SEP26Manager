package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * UC-LOC-08: Configure Bin Capacity response
 * BR-LOC-31: returns recalculated available capacity immediately
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BinCapacityResponse {

    @Schema(description = "ID Vị trí Bin", example = "50")
    private Long locationId;
    @Schema(description = "Mã Vị Trí", example = "BIN-A2")
    private String locationCode;
    @Schema(description = "ID Khu Vực", example = "2")
    private Long zoneId;
    @Schema(description = "Mã Khu Vực", example = "ZONE-ELEC")
    private String zoneCode;

    @Schema(description = "Sức chứa tối đa (Trọng lượng KG)", example = "100.0")
    private BigDecimal maxWeightKg;
    @Schema(description = "Sức chứa tối đa (Thể tích M3)", example = "1.5")
    private BigDecimal maxVolumeM3;

    // Recalculated after save — BR-LOC-31
    @Schema(description = "Số lượng ĐÃ CHIẾM HIỆN TẠI (Tính theo M3 hoặc KG tùy setting cài đặt quy đổi của Cty)", example = "0.5")
    private BigDecimal currentOccupiedQty;
    @Schema(description = "Sức chứa CÒN TRỐNG (Trọng lượng)", example = "80.0")
    private BigDecimal availableWeightKg;
    @Schema(description = "Sức chứa CÒN TRỐNG (Thể tích M3)", example = "1.0")
    private BigDecimal availableVolumeM3;

    @Schema(description = "Lần cập nhật cuối", example = "2026-03-08T10:00:00")
    private LocalDateTime updatedAt;
}