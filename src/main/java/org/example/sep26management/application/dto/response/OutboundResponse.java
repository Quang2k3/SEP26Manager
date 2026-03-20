package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.example.sep26management.application.enums.OutboundType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response for all Outbound UC-OUT-01/02/03/04
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OutboundResponse {

    @Schema(description = "ID phiếu yêu cầu xuất(Số SO, Transfer order)", example = "10")
    private Long documentId; // so_id or transfer_id
    @Schema(description = "Mã số Phiếu Xuất (Khác với GRN nhận hàng)", example = "EXP-SAL-200501")
    private String documentCode; // EXP-SAL-... or EXP-INT-...
    @Schema(description = "Loại Phiếu: SALES_ORDER, INTERNAL_TRANSFER", example = "SALES_ORDER")
    private OutboundType orderType;
    @Schema(description = "Trạng thái", example = "DRAFT")
    private String status;
    @Schema(description = "ID Kho áp dụng", example = "1")
    private Long warehouseId;

    // Sales Order fields
    @Schema(description = "ID Khách hàng", example = "100")
    private Long customerId;
    @Schema(description = "Mã Khách hàng — dùng cho edit form", example = "CUST-001")
    private String customerCode;
    @Schema(description = "Tên Khách Hàng", example = "Công ty TNHH A")
    private String customerName;
    @Schema(description = "Ngày dự kiến giao hàng", example = "2026-03-15")
    private LocalDate deliveryDate;
    @Schema(description = "Mã phiếu của khách (Tham chiếu)", example = "PO-KHACH-123")
    private String referenceOrderCode;

    // Internal Transfer fields
    @Schema(description = "ID Kho đến", example = "2")
    private Long destinationWarehouseId;
    @Schema(description = "Mã Kho đến — dùng cho edit form", example = "WH-02")
    private String destinationWarehouseCode;
    @Schema(description = "Tên Kho đến", example = "Kho Quận 1")
    private String destinationWarehouseName;
    @Schema(description = "Lý do chuyển", example = "STOCK_BALANCING")
    private String transferReason;
    @Schema(description = "Tên người nhận", example = "Nguyễn Văn B")
    private String receiverName;
    @Schema(description = "SĐT người nhận", example = "0987654321")
    private String receiverPhone;
    @Schema(description = "Ngày dự kiến chuyển", example = "2026-03-15")
    private LocalDate transferDate;

    @Schema(description = "Danh sách chi tiết hàng hóa báo xuất")
    private List<OutboundItemResponse> items;
    @Schema(description = "Ghi chú chung", example = "Xuất lô cũ trước")
    private String note;

    @Schema(description = "User ID người tạo", example = "1")
    private Long createdBy;
    @Schema(description = "User ID quản lý đã duyệt", example = "2")
    private Long approvedBy;
    @Schema(description = "Ngày giờ duyệt", example = "2026-03-08T10:00:00")
    private LocalDateTime approvedAt;
    @Schema(description = "Ngày giờ tạo", example = "2026-03-08T10:00:00")
    private LocalDateTime createdAt;
    @Schema(description = "Ngày sửa cuối", example = "2026-03-08T10:00:00")
    private LocalDateTime updatedAt;

    // Stock warnings — BR-OUT-03/04
    @Schema(description = "Danh sách cánh báo hết hàng (Nếu Số Lượng Tồn < Số Khách yêu cầu, sẽ hiện Cảnh Báo cho Quản lý biết để Reject phiếu)")
    private List<StockWarning> stockWarnings;
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
    )
    private String dispatchPdfUrl;
    private String signedNoteUrl;
    private String signedNoteUploadedAt;
    /** Ảnh phiếu lấy hàng đã ký (nhân viên kho) */
    private String pickSignedNoteUrl;
    private String pickSignedNoteUploadedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OutboundItemResponse {
        @Schema(description = "ID dòng này", example = "1")
        private Long itemId;
        @Schema(description = "ID Sản phẩm", example = "100")
        private Long skuId;
        @Schema(description = "Mã SKU", example = "SKU-IP15")
        private String skuCode;
        @Schema(description = "Tên Sản phẩm", example = "Iphone 15 Pro")
        private String skuName;
        @Schema(description = "Số lượng muốn xuất đi", example = "50")
        private BigDecimal requestedQty;
        @Schema(description = "Số lượng KHẢ DỤNG trong kho thực tế tính đến GIỜ PHÚT NÀY", example = "150.0")
        private BigDecimal availableQty; // BR-OUT-03: real-time
        @Schema(description = "Kho không đủ hàng (true/false) - Nếu true, số dư nhỏ hơn khách đòi, quản lý sẽ bị cảnh báo đỏ chóet", example = "false")
        private boolean insufficientStock; // true if requested > available
        @Schema(description = "Ghi chú dòng", example = "Lấy hàng mới")
        private String note;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StockWarning {
        @Schema(description = "ID Sản phẩm Thấy mòn (Thiếu hàng)", example = "100")
        private Long skuId;
        @Schema(description = "Mã SKU thiếu hàng", example = "SKU-IP15")
        private String skuCode;
        @Schema(description = "Số Đòi hỏi", example = "50.0")
        private BigDecimal requestedQty;
        @Schema(description = "Số Có sẵn", example = "30.0")
        private BigDecimal availableQty;
        @Schema(description = "Thông báo giải thích", example = "Sản phẩm SKU-IP15 chỉ còn khả dụng 30.0 cái (Khách đòi 50.0).")
        private String message;
    }


}