package org.example.sep26management.application.dto.scan;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScanLineItem {

    private Long skuId;
    private String skuCode;
    private String skuName;
    private String barcode;
    private BigDecimal qty;

    /** PASS or FAIL */
    @Builder.Default
    private String condition = "PASS";

    /** Reason code when condition = FAIL (e.g. LEAK, TORN_PACKAGING, DENTED) */
    private String reasonCode;

    /** URL ảnh bằng chứng hàng hỏng khi condition = FAIL */
    private String attachmentUrl;

    private String lotNumber;
    private java.time.LocalDate manufactureDate;
    private java.time.LocalDate expiryDate;
}
