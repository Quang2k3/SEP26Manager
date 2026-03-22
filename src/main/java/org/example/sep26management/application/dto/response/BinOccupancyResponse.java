package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.example.sep26management.application.enums.OccupancyStatus;

import java.math.BigDecimal;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * UC-LOC-06: View Bin Occupancy
 * Shows: zone/aisle/rack/bin code, capacity, occupied qty, occupancy status
 * BR-LOC-20: real-time from inventory_snapshot
 * BR-LOC-21: FULL when occupied >= max capacity
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BinOccupancyResponse {

    @Schema(description = "ID Bin", example = "50")
    private Long locationId;
    @Schema(description = "Mã vạch của kệ tủ", example = "BIN-A2")
    private String locationCode;

    // Hierarchy info — BR-LOC-16
    @Schema(description = "ID Khu", example = "1")
    private Long zoneId;
    @Schema(description = "Mã Khu", example = "ZONE-ELEC")
    private String zoneCode;
    @Schema(description = "ID Tủ chứa nó", example = "5")
    private Long parentLocationId; // RACK id
    @Schema(description = "Mã Tủ", example = "RACK-101")
    private String parentLocationCode; // RACK code
    @Schema(description = "ID Lối đi giữa 2 dãy tủ", example = "2")
    private Long grandParentLocationId; // AISLE id
    @Schema(description = "Mã Lối Đi", example = "AISLE-1")
    private String grandParentLocationCode; // AISLE code

    // Capacity config
    @Schema(description = "Max Cân Nặng", example = "100.0")
    private BigDecimal maxWeightKg;
    @Schema(description = "Max Thể Tích", example = "2.5")
    private BigDecimal maxVolumeM3;

    // Real-time occupancy — BR-LOC-23
    @Schema(description = "Số lượng đang chiếm chỗ", example = "10.0")
    private BigDecimal occupiedQty;
    // [FIX] Tong trong luong thuc te (kg) = sum(qty * weightPerCartonKg)
    private BigDecimal occupiedWeightKg;
    @Schema(description = "Số lượng đang chờ xuất", example = "5.0")
    private BigDecimal reservedQty;
    @Schema(description = "Còn trống khả dụng", example = "85.0")
    private BigDecimal availableQty; // max - occupied - reserved (if max configured)

    /** BR-LOC-21: EMPTY / PARTIAL / FULL */
    @Schema(description = "Trạng Thái Khu Này", example = "PARTIAL")
    private OccupancyStatus occupancyStatus;

    @Schema(description = "Có phải khu nhặt hàng không", example = "true")
    private Boolean isPickingFace;
    @Schema(description = "Khu dỡ hàng tạm thời?", example = "false")
    private Boolean isStaging;
    @Schema(description = "Có hoạt động không", example = "true")
    private Boolean active;

    /** Tầng BIN trong rack: 1=dưới (512kg) · 2=giữa (448kg) · 3=trên (400kg) */
    @Schema(description = "Tầng BIN (1=dưới, 2=giữa, 3=trên)", example = "1")
    private Integer binFloor;

    /** Cột BIN trong rack: 1=trái · 2=giữa · 3=phải */
    @Schema(description = "Cột BIN (1=trái, 2=giữa, 3=phải)", example = "2")
    private Integer binColumn;

    /** UC-LOC-06 3c: detailed inventory in bin (optional, only when requested) */
    @Schema(description = "Liệt kê danh sách các món ĐANG NẰM CHÌNH ÌNH TRONG CÁI TỦ NÀY ĐỂ KEEPER NHÌN DỄ KIỂU TRA")
    private List<BinInventoryItem> inventoryItems;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BinInventoryItem {
        @Schema(description = "ID Sản phẩm", example = "100")
        private Long skuId;
        @Schema(description = "Mã SKU", example = "SKU-IP15")
        private String skuCode;
        @Schema(description = "Tên", example = "Iphone 15 Pro")
        private String skuName;
        @Schema(description = "ID Lô (Trong tủ này lô nào đang cất)", example = "10")
        private Long lotId;
        @Schema(description = "Số Lô Nhập", example = "LOT-2023")
        private String lotNumber;
        @Schema(description = "Hạn Sử Dụng của Lô", example = "2025-12-31")
        private java.time.LocalDate expiryDate;
        @Schema(description = "Số Lượng Đang Tồn ở Kệ", example = "50.0")
        private BigDecimal quantity;
        @Schema(description = "Số lượng ĐÃ GIỮ CHỖ (Chờ khách lấy)", example = "10.0")
        private BigDecimal reservedQty;
        // [FIX] Trong luong 1 thung de tinh % tai trong bin
        private BigDecimal weightPerCartonKg;
    }
}