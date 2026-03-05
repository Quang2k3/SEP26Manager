package org.example.sep26management.application.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateQcInspectionRequest {

    /** Chi tiết kết quả kiểm tra QC */
    private String remarks;

    /** ID ảnh đính kèm (ảnh chụp hư hỏng) */
    private Long attachmentId;
}
