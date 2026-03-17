package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * UC-WXE-06: Generate Pick List response
 * BR-WXE-23: optimal picking route (sorted by zone → location code)
 * BR-WXE-24: SKU, lot, location traceability
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PickListResponse {

    @Schema(description = "ID phiếu Soạn Hàng (Pick List)", example = "50")
    private Long pickingTaskId;

    @Schema(description = "Mã phiếu Soạn Hàng", example = "PICK-200501-A")
    private String pickingTaskCode;

    @Schema(description = "ID Yêu cầu xuất (Sales Order / Transfer)", example = "10")
    private Long documentId;

    @Schema(description = "Mã số Phiếu Xuất (Khác với GRN nhận hàng)", example = "EXP-SAL-200501")
    private String documentCode;

    @Schema(description = "Trạng thái Pick List (Ví dụ: PENDING, IN_PROGRESS, COMPLETED)", example = "PENDING")
    private String status;

    @Schema(description = "User ID Nhân viên lấy hàng", example = "12")
    private Long assignedTo;

    @Schema(description = "Tên NV lấy hàng", example = "Lê Văn C")
    private String assignedToName;

    @Schema(description = "Danh sách chi tiết các món cần lấy (ĐÃ ĐƯỢC AI TỐI ƯU HÓA ĐƯỜNG ĐI ĐỂ LẤY NHANH NHẤT LẦN LƯỢT THEO ZONE)")
    private List<PickListItem> items; // sorted by optimal route

    @Schema(description = "Ngày/Giờ tạo", example = "2026-03-08T10:00:00")
    private LocalDateTime generatedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PickListItem {
        @Schema(description = "Thứ tự đi lấy món này (1 là món đầu tiên, 2 là kế tiếp, AI xếp sao đi vậy cho nhanh khỏi đi lòng vòng)", example = "1")
        private int sequence; // picking order (BR-WXE-23: optimal route)

        @Schema(description = "ID của Item trong phiếu Pick List", example = "105")
        private Long pickingTaskItemId;

        // Location info
        @Schema(description = "Đến Bin này để lấy nè", example = "2")
        private Long locationId;

        @Schema(description = "Mã Bin (Tên trên kệ tủ thép)", example = "BIN-A2")
        private String locationCode;

        @Schema(description = "Mã Khu vực (Zone)", example = "ZONE-ELEC")
        private String zoneCode;

        @Schema(description = "Mã Kệ (Rack)", example = "RACK-101")
        private String rackCode;

        // SKU info — BR-WXE-24: traceability
        @Schema(description = "ID Sản Phẩm", example = "100")
        private Long skuId;

        @Schema(description = "Mã Sản Phẩm", example = "SKU-IP15")
        private String skuCode;

        @Schema(description = "Tên Sản Phẩm", example = "Iphone 15 Pro")
        private String skuName;

        @Schema(description = "Barcode vật lý trên hộp sản phẩm (dùng để quét trên điện thoại)", example = "0001-1012")
        private String barcode;

        // Lot info
        @Schema(description = "ID của Lô hàng mà lô này được phân bổ", example = "10")
        private Long lotId;

        @Schema(description = "Số Lot", example = "LOT-2023")
        private String lotNumber;

        @Schema(description = "Ngày hết hạn", example = "2025-12-31")
        private LocalDate expiryDate;

        @Schema(description = "Số lượng Yêu Cầu lấy trên hệ thống", example = "20.0")
        private BigDecimal requiredQty;

        @Schema(description = "Số lượng THỰC TẾ NV đã lấy vào mâm (Sẽ khác lúc NV confirm sau khi lấy xong)", example = "20.0")
        private BigDecimal pickedQty;

        @Schema(description = "Trạng thái dòng này (PENDING | PICKED)", example = "PENDING")
        private String status; // PENDING | PICKED
    }
}