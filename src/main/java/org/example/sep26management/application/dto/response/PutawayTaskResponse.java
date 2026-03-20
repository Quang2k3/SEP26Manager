package org.example.sep26management.application.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PutawayTaskResponse {

    @Schema(description = "ID Lệnh cất hàng", example = "10")
    private Long putawayTaskId;
    private Long warehouseId;

    @Schema(description = "Thuộc GRN số", example = "3")
    private Long grnId;

    @Schema(description = "Mã GRN (ví dụ: GRN-1773906771871)", example = "GRN-1773906771871")
    private String grnCode;

    @Schema(description = "ID phiếu nhận hàng gốc", example = "5")
    private Long receivingId;

    @Schema(description = "Mã phiếu nhận hàng (ví dụ: RCV-2026-001)", example = "RCV-2026-001")
    private String receivingCode;

    @Schema(description = "Số lượng SKU trong task (để hiển thị nhanh trên list)", example = "3")
    private Integer itemCount;

    @Schema(description = "Trạng thái (PENDING, OPEN, IN_PROGRESS, DONE)", example = "PENDING")
    private String status;
    private Long fromLocationId;
    private Long assignedTo;

    @Schema(description = "Ngày tạo lệnh", example = "2026-03-08T10:00:00")
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    @Schema(description = "Ghi chú", example = "Cất hàng Zone A")
    private String note;

    @Schema(description = "URL ảnh phiếu cất hàng đã ký")
    private String signedNoteUrl;

    @Schema(description = "Thời điểm upload ảnh ký")
    private LocalDateTime signedNoteUploadedAt;

    @Schema(description = "Danh sách chi tiết hàng cần cất")
    private List<PutawayTaskItemDto> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PutawayTaskItemDto {
        @Schema(description = "ID dòng yêu cầu cất", example = "1")
        private Long putawayTaskItemId;

        @Schema(description = "ID Sản phẩm", example = "100")
        private Long skuId;

        @Schema(description = "Mã SKU", example = "SKU-IP15")
        private String skuCode;

        @Schema(description = "Tên SP", example = "Iphone 15 Pro")
        private String skuName;

        @Schema(description = "ID Lô (Nếu quản lý lô)", example = "3")
        private Long lotId;

        @Schema(description = "SỐ LƯỢNG YÊU CẦU CẤT", example = "10.0")
        private BigDecimal quantity;

        @Schema(description = "Số lượng THỰC TẾ đã cất xong (confirmed)", example = "10.0")
        private BigDecimal putawayQty;

        @Schema(description = "Số lượng đã đặt chỗ (RESERVED, chưa confirm)", example = "40.0")
        private BigDecimal allocatedQty;

        @Schema(description = "Số lượng còn lại chưa phân bổ", example = "50.0")
        private BigDecimal remainingQty;

        @Schema(description = "Gợi ý: ID Vị Trí (Bin)", example = "2")
        private Long suggestedLocationId;

        @Schema(description = "Gợi ý: Mã Bin", example = "BIN-A1-01")
        private String suggestedLocationCode;

        @Schema(description = "Gợi ý: Khu vực (Zone)", example = "ZONE-ELEC")
        private String suggestedZoneCode;

        @Schema(description = "Gợi ý: Hàng (Aisle)", example = "A1")
        private String suggestedAisle;

        @Schema(description = "Gợi ý: Kệ (Rack)", example = "R02")
        private String suggestedRack;

        @Schema(description = "Tồn kho HIỆN TẠI ở Bin gợi ý", example = "5")
        private BigDecimal binCurrentQty;

        @Schema(description = "Sức chứa tối đa (Max Capacity) của Bin gợi ý", example = "100")
        private BigDecimal binMaxCapacity;

        @Schema(description = "Số chỗ còn trống ở Bin gợi ý", example = "95")
        private BigDecimal binAvailableCapacity;

        @Schema(description = "Vị trí thực tế Cất (Nhân viên dùng súng quét Bin thực tế)", example = "2")
        private Long actualLocationId;
    }
}