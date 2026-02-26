package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateGrnRequest {

    // warehouseId is resolved from the scan session data (which was set from JWT
    // when session was created)

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
