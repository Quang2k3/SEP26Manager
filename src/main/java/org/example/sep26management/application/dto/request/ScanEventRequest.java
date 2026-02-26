package org.example.sep26management.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScanEventRequest {

    @NotBlank(message = "barcode is required")
    private String barcode;

    @NotNull(message = "qty is required")
    @Positive(message = "qty must be positive")
    private BigDecimal qty;

    /**
     * Required only when calling with a regular user JWT (e.g. Swagger testing).
     * iPhone scanner uses a scan token that already embeds the sessionId — leave
     * null in that case.
     */
    private String sessionId;
}
