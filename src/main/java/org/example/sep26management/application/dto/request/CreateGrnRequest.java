package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateGrnRequest {

    @NotNull(message = "warehouseId is required")
    private Long warehouseId;

    /** SUPPLIER | TRANSFER | RETURN */
    @NotBlank(message = "sourceType is required")
    private String sourceType;

    private Long supplierId;
    private String sourceReferenceCode;

    /** Lot number applied to all scan lines */
    private String lotNumber;
    private LocalDate expiryDate;
    private LocalDate manufactureDate;

    private String note;
}
