package org.example.sep26management.application.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceivingItemResponse {

    private Long receivingItemId;
    private Long skuId;
    private String skuCode;
    private String skuName;
    private String unit;
    private BigDecimal receivedQty;
    private String lotNumber;
    private LocalDate expiryDate;
    private LocalDate manufactureDate;
    private String note;
}
