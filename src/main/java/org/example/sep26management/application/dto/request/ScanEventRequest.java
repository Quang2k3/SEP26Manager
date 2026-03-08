package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScanEventRequest {

    @Schema(description = "Mã vạch của sản phẩm (SKU Code)", example = "SKU-IPHONE15-PRO-256")
    @NotBlank(message = "barcode is required")
    private String barcode;

    @Schema(description = "Số lượng quét", example = "10")
    @NotNull(message = "qty is required")
    @Positive(message = "qty must be positive")
    private BigDecimal qty;

    @Schema(description = "Tình trạng hàng hóa khi quét", example = "PASS", allowableValues = { "PASS", "FAIL" })
    @Builder.Default
    private String condition = "PASS";

    @Schema(description = "Mã lý do lỗi (Bắt buộc nếu condition = FAIL). VD: HOLE, DENTED, TEAR", example = "DENTED")
    private String reasonCode;

    @Schema(description = "Mã phiên quét (Session ID). Lấy từ API tạo phiên: `POST /v1/receiving-sessions`.", example = "sess_123456789")
    private String sessionId;
}
