package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.example.sep26management.application.enums.OccupancyStatus;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * UC-LOC-07: Search Empty Bin
 * Shows: bin code, zone/location hierarchy, total capacity, available capacity,
 * status
 * BR-LOC-27: sorted by putaway priority (least residual space first)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmptyBinResponse {

    @Schema(description = "ID Vị trí Bin", example = "55")
    private Long locationId;
    @Schema(description = "Mã Vị trí/Kệ", example = "BIN-B1")
    private String locationCode;

    // Hierarchy
    @Schema(description = "ID Zone Cha", example = "1")
    private Long zoneId;
    @Schema(description = "Tên Zone", example = "ZONE-ELEC")
    private String zoneCode;
    @Schema(description = "Thuộc tủ chứa mã bao nhiêu", example = "RACK-102")
    private String rackCode; // parent rack

    // Capacity
    @Schema(description = "Sức chứa cân nặng Max", example = "100.0")
    private BigDecimal maxWeightKg;
    @Schema(description = "Sức chứa T.tích Max", example = "2.5")
    private BigDecimal maxVolumeM3;
    @Schema(description = "Đã chiếm nãy giờ", example = "0.0")
    private BigDecimal occupiedQty;
    @Schema(description = "Còn Mấy Kilograms nữa", example = "100.0")
    private BigDecimal availableWeightKg; // maxWeightKg - occupiedQty (weight-based)
    @Schema(description = "Còn Mấy M3", example = "2.5")
    private BigDecimal availableVolumeM3; // maxVolumeM3 - occupied volume

    @Schema(description = "Trạng Thái Kho Này (EMPTY, PARTIAL, FULL)", example = "EMPTY")
    private OccupancyStatus occupancyStatus;

    @Schema(description = "Dùng cho nhân viên đi lấy hàng thường xuyên không (Pick Face)", example = "true")
    private Boolean isPickingFace;
}