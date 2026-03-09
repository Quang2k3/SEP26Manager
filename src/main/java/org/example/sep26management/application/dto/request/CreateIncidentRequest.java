package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.example.sep26management.application.enums.IncidentType;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateIncidentRequest {

    @Schema(description = "ID Kho (Bắt buộc)", example = "1")
    @NotNull(message = "warehouseId is required")
    private Long warehouseId;

    @Schema(description = "Loại sự cố (Bắt buộc)", example = "DAMAGE", allowableValues = { "DAMAGE", "SHORTAGE",
            "OVERAGE",
            "SEAL_BROKEN", "SEAL_MISMATCH", "PACKAGING_DAMAGE", "OTHER" })
    @NotNull(message = "incidentType is required")
    private IncidentType incidentType;

    @Schema(description = "Mô tả sự cố (VD: \"Kẹp chì bị đứt, số seal không khớp với phiếu giao\") (Bắt buộc)", example = "Kẹp chì bị đứt")
    @NotBlank(message = "description is required")
    private String description;

    @Schema(description = "ID Lệnh nhập hàng liên quan (Có thể null)", example = "15")
    private Long receivingId;

    @Schema(description = "ID ảnh đính kèm (sau khi upload lên /attachments) (Có thể null)", example = "1001")
    private Long attachmentId;
}
