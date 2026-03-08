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

    @NotNull(message = "warehouseId is required")
    private Long warehouseId;

    /** SEAL_BROKEN, SEAL_MISMATCH, PACKAGING_DAMAGE, OTHER, etc */
    @NotNull(message = "incidentType is required")
    @Schema(description = "Loại sự cố", example = "DAMAGE", allowableValues = { "DAMAGE", "SHORTAGE", "OVERAGE",
            "SEAL_BROKEN", "SEAL_MISMATCH", "PACKAGING_DAMAGE", "OTHER" })
    private IncidentType incidentType;

    /** Mô tả sự cố (VD: "Kẹp chì bị đứt, số seal không khớp với phiếu giao") */
    @NotBlank(message = "description is required")
    private String description;

    /** ID Lệnh nhập hàng liên quan (nếu có) */
    private Long receivingId;

    /** ID ảnh đính kèm (sau khi upload lên /attachments) */
    private Long attachmentId;
}
