package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.example.sep26management.application.enums.OutboundType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * UC-OUT-06: One row in the outbound list table
 * Fields per SRS: documentCode, type, destination, status, totalItems,
 * totalQty, createdBy, createdDate, shipmentDate, actions
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OutboundListResponse {

    @Schema(description = "ID phiếu yêu cầu xuất(Số SO, Transfer order)", example = "10")
    private Long documentId;

    @Schema(description = "Mã số Phiếu Xuất (Khác với GRN nhận hàng)", example = "EXP-SAL-200501")
    private String documentCode;

    @Schema(description = "Loại Phiếu: SALES_ORDER, INTERNAL_TRANSFER", example = "SALES_ORDER")
    private OutboundType orderType;

    @Schema(description = "Nơi đến: Tên khách hàng (Nếu SALES_ORDER) hoặc Tên Kho Đích (Nếu INTERNAL_TRANSFER)", example = "Khách hàng Nguyễn Văn A")
    private String destination;

    @Schema(description = "Trạng thái", example = "DRAFT")
    private String status;

    @Schema(description = "Tổng DÒNG Sản Phẩm", example = "5")
    private int totalItems;

    @Schema(description = "Tổng SỐ LƯỢNG s.phẩm", example = "150.0")
    private BigDecimal totalQty;

    @Schema(description = "User ID tạo phiếu", example = "1")
    private Long createdBy;
    @Schema(description = "Tên NV tạo phiếu", example = "Bui Quang")
    private String createdByName;
    @Schema(description = "Thời gian tạo", example = "2026-03-08T10:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "Ngày giao hàng / chuyển hàng dự kiến", example = "2026-03-15")
    private LocalDate shipmentDate;

    // BR-OUT-26: available actions based on role + status
    @Schema(description = "Cờ cho biết FE có được hiển thị nút Xóa (Nút thùng rác) không", example = "true")
    private boolean canEdit; // DRAFT + creator
    @Schema(description = "Cờ cho biết FE có được hiển thị Edit (Nút Cái Bút) không", example = "true")
    private boolean canDelete; // DRAFT + creator
    @Schema(description = "Cờ cho biết FE có được hiển thị SUBMIT không", example = "true")
    private boolean canSubmit; // DRAFT + creator
    @Schema(description = "Cờ cho biết FE có được hiển thị nút DUYỆT (APPROVE) không", example = "false")
    private boolean canApprove; // PENDING + MANAGER
    @Schema(description = "Cờ cho lấy picking / đóng gói confirm không", example = "false")
    private boolean canConfirm; // APPROVED + KEEPER
}