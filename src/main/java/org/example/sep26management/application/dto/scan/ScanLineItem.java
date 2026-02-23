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
}
