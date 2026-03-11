package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveShortageRequest {

    /**
     * Phương án xử lý thiếu hàng:
     * - CLOSE_SHORT: Chốt thiếu, hoàn tất đơn.
     * - WAIT_BACKORDER: Chờ giao bù, đơn trở thành nhập nhiều lần.
     */
    @NotBlank(message = "Resolution is required")
    private String resolution;
}
