package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.example.sep26management.application.enums.IncidentType;
import org.example.sep26management.application.enums.IncidentCategory;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IncidentResponse {

    @Schema(description = "ID Sự Cố", example = "10")
    private Long incidentId;
    @Schema(description = "ID Kho Hệ Thống", example = "1")
    private Long warehouseId;
    @Schema(description = "Mã Sự Cố Tham Chiếu", example = "INC-200501")
    private String incidentCode;
    @Schema(description = "Loại Sự Cố", example = "DAMAGE")
    private IncidentType incidentType;
    @Schema(description = "Phân loại báo cáo (GATE/QUALITY)", example = "GATE")
    private IncidentCategory category;
    @Schema(description = "Độ Nghiêm Trọng (HIGH/MEDIUM/LOW)", example = "HIGH")
    private String severity;
    @Schema(description = "Thời gian xảy ra/báo cáo")
    private LocalDateTime occurredAt;
    @Schema(description = "Mô tả sự cố chi tiết")
    private String description;
    @Schema(description = "User ID người báo cáo", example = "2")
    private Long reportedBy;
    @Schema(description = "Tên người báo cáo")
    private String reportedByName;
    @Schema(description = "ID hình ảnh đính kèm")
    private Long attachmentId;
    @Schema(description = "Trạng thái (OPEN, APPROVED, REJECTED, RESOLVED)")
    private String status;
    @Schema(description = "ID phiếu nhập kho liên đới (inbound incidents)")
    private Long receivingId;
    @Schema(description = "Mã phiếu nhập kho (GRN) liên đới")
    private String receivingCode;

    /**
     * [V20] ID Sales Order liên đới (outbound incidents — DAMAGE / SHORTAGE).
     * Dùng để FE biết đây là incident outbound và hiển thị đúng actions.
     */
    @Schema(description = "ID Sales Order liên đới (outbound)", example = "42")
    private Long soId;

    @Schema(description = "Ngày tạo phiếu")
    private LocalDateTime createdAt;

    @Schema(description = "Danh sách sản phẩm bị lỗi trong sự cố này")
    private java.util.List<IncidentItemResponse> items;
}