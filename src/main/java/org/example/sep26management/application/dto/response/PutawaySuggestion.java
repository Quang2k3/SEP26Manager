package org.example.sep26management.application.dto.response;

import lombok.*;

import java.math.BigDecimal;

/**
 * Suggestion detail cho mỗi putaway item.
 * Gồm zone matching info + bin capacity info.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PutawaySuggestion {

    private Long skuId;
    private String skuCode;
    private String categoryCode;

    // Zone match info
    private String matchedZoneCode;
    private Long matchedZoneId;
    private String matchedZoneName;

    // Suggested bin info
    private Long suggestedLocationId;
    private String suggestedLocationCode; // e.g. "BIN-A01-R01-01"
    private String aisleName; // parent aisle code
    private String rackName; // parent rack code

    // Capacity info
    private BigDecimal currentQty; // qty already in this bin
    private BigDecimal maxCapacity; // maxWeightKg of bin
    private BigDecimal availableCapacity; // maxCapacity - currentQty

    // Matching reason
    private String reason; // e.g. "Zone Z-HC matched category HC"
}
