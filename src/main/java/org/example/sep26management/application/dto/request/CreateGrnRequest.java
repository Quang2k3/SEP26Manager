package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateGrnRequest {

    // warehouseId is resolved from the scan session data (which was set from JWT
    // when session was created)

    @Schema(description = "Loại giao dịch nhập kho", example = "SUPPLIER", allowableValues = { "SUPPLIER", "TRANSFER",
            "RETURN" })
    @NotBlank(message = "sourceType is required")
    private String sourceType;

    @Schema(description = "Mã nhà cung cấp (Lấy từ API danh sách supplier)", example = "SUP-001")
    private String supplierCode;

    @Schema(description = "Mã chứng từ gốc (ví dụ mã PO, số Invoice)", example = "PO-20231015-01")
    private String sourceReferenceCode;

    @Schema(description = "Mã lô áp dụng chung cho toàn bộ đơn (Nếu chưa có, API sẽ ghi chú lại hoặc tự sinh)", example = "L123456")
    private String lotNumber;

    @Schema(description = "Ngày hết hạn chung của lô", example = "2025-12-31")
    private LocalDate expiryDate;

    @Schema(description = "Ngày sản xuất chung của lô", example = "2023-10-01")
    private LocalDate manufactureDate;

    @Schema(description = "Ghi chú thêm", example = "Nhập hàng đợt 1")
    private String note;
}