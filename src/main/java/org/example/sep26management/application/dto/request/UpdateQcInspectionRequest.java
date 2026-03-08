package org.example.sep26management.application.dto.request;

import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateQcInspectionRequest {

    /** Chi tiết kết quả kiểm tra QC */
    @Schema(description = "Ghi chú chi tiết kết quả kiểm tra QC từ nhân viên QC", example = "Sản phẩm bị rách bao bì, móp méo nặng.")
    private String remarks;

    /** ID ảnh đính kèm (ảnh chụp hư hỏng) */
    @Schema(description = "ID của ảnh đính kèm (sau khi upload lên hệ thống)", example = "105")
    private Long attachmentId;
}
