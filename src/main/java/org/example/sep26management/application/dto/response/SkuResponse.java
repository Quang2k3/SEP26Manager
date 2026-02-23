package org.example.sep26management.application.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkuResponse {

    private Long skuId;
    private String skuCode;
    private String skuName;
    private String description;
    private String brand;
    private String unit;
    private Long categoryId;
    private String categoryCode;
    private String categoryName;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}