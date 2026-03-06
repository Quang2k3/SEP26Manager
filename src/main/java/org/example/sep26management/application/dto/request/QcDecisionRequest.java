package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QcDecisionRequest {

    /**
     * SCRAP = Tiêu hủy,
     * RETURN = Trả hàng về nhà máy,
     * DOWNGRADE = Thanh lý / Bán hạ cấp
     */
    @NotBlank(message = "decision is required (SCRAP, RETURN, DOWNGRADE)")
    private String decision;

    /** Ghi chú thêm của Manager */
    private String note;
}
