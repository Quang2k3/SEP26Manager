package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * UC-WXE-05: Allocate Stock response
 * BR-WXE-21: retains lot + expiry info per allocation line
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AllocateStockResponse {

    @Schema(description = "ID Yêu Cầu Xuất Kho", example = "10")
    private Long documentId;

    @Schema(description = "Mã Phiếu Xuất Kho", example = "EXP-SAL-1234")
    private String documentCode;

    @Schema(description = "Trạng thái (Ví dụ: ALLOCATED, PARTIALLY_ALLOCATED)", example = "ALLOCATED")
    private String status; // ALLOCATED or PARTIALLY_ALLOCATED

    @Schema(description = "Tổng số DÒNG SKU", example = "3")
    private int totalSkus;

    @Schema(description = "Số DÒNG SKU có hàng phân bổ thành công (Thực tế sắp xếp lấy hàng đc ở Bin nào)", example = "2")
    private int allocatedSkus;

    @Schema(description = "Danh sách chi tiết kho chứa hàng sẵn có để đi lấy")
    private List<AllocationLine> allocations;

    @Schema(description = "Danh sách chi tiết SKU không đủ hàng kho")
    private List<ShortageItem> shortages; // items with insufficient stock

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AllocationLine {
        @Schema(description = "ID của SKU", example = "100")
        private Long skuId;

        @Schema(description = "Mã SKU", example = "SKU-IP15")
        private String skuCode;

        @Schema(description = "Tên SP", example = "Iphone 15 Pro")
        private String skuName;

        @Schema(description = "ID lô (Lot ID)", example = "10")
        private Long lotId;

        @Schema(description = "Mã lô thật xuất đi (VD: Nhập ngày 10/10/2023)", example = "LOT-2023")
        private String lotNumber;

        @Schema(description = "Ngày hết hạn (Để báo FE biết FIFO có chạy đúng không)", example = "2025-12-31")
        private LocalDate expiryDate; // BR-WXE-21: traceability

        @Schema(description = "Nên đi vào thẳng Bin ID nào để lấy hàng xuất?", example = "2")
        private Long locationId;

        @Schema(description = "Mã BIN kệ tủ khuyên dùng", example = "BIN-A2")
        private String locationCode;

        @Schema(description = "Mã Khu vực, tiện sắp xếp đường đi", example = "ZONE-ELEC")
        private String zoneCode;

        @Schema(description = "ĐÃ GIỮ chổ (Đủ số lượng lấy ở Kệ BIN đó) là bao nhiêu", example = "20.0")
        private BigDecimal allocatedQty;

        @Schema(description = "Số lượng khách đang đòi", example = "20.0")
        private BigDecimal requestedQty;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShortageItem {
        @Schema(description = "ID của SKU BỊ THIẾU HÀNG", example = "100")
        private Long skuId;

        @Schema(description = "Mã SKU BỊ THIẾU HÀNG", example = "SKU-IP15")
        private String skuCode;

        @Schema(description = "Số lượng yêu cầu KHÁCH MUA ĐÒI", example = "50")
        private BigDecimal requestedQty;

        @Schema(description = "Số lượng kho có sẵn NGAY LÚC ĐÓ", example = "30")
        private BigDecimal availableQty;

        @Schema(description = "Số lượng thiếu mòn (Cần order bù kho, hoặc Hủy bỏ Outbound nếu Sếp QLý không ký duyệt)", example = "20")
        private BigDecimal shortageQty;
    }
}