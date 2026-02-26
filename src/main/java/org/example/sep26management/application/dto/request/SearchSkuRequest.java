package org.example.sep26management.application.dto.request;

import lombok.*;

/**
 * UC-B06: Search SKU
 * BR-SKU-06: partial matching (contains), case-insensitive, across SKU Code + Product Name
 * BR-GEN-01: default view = latest 20 records when no keyword
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchSkuRequest {

    private String keyword;

    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int size = 20;
}