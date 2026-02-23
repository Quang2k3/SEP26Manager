package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SkuResponse {

    private Long skuId;
    private String skuCode;
    private String skuName;
    private String description;
    private String brand;
    private String barcode;
    private String unit;
    private String packageType;
    private BigDecimal volumeMl;
    private BigDecimal weightG;
    private String originCountry;
    private String scent;
    private String imageUrl;
    private Integer shelfLifeDays;
    private BigDecimal storageTempMin;
    private BigDecimal storageTempMax;
    private Boolean active;

    // Category info
    private Long categoryId;
    private String categoryCode;
    private String categoryName;
}
