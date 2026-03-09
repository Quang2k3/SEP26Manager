package org.example.sep26management.application.dto.response;

import lombok.*;
import org.example.sep26management.application.dto.scan.ScanLineItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScanSessionResponse {

    @Schema(description = "ID Phiên quét đang mở", example = "8ce4260b-8d8a-4db5-bdf9-009d1d6a8b75")
    private String sessionId;

    @Schema(description = "ID Kho áp dụng", example = "1")
    private Long warehouseId;

    @Schema(description = "Token bảo mật chuyên dùng để thiết bị di động gọi API scan mã vạch", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String scanToken;

    @Schema(description = "Đường dẫn URL có mã scanToken tích hợp sẵn để thiết bị di động vào thẳng trang Scan", example = "http://localhost:3000/scan?token=eyJ...")
    private String scanUrl;

    @Schema(description = "Danh sách các món hàng VỪA MỚI QUÉT trong phiên này")
    private List<ScanLineItem> lines;
}
