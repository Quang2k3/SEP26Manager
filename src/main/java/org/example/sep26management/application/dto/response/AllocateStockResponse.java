package org.example.sep26management.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * UC-WXE-05: Allocate Stock response
 * BR-WXE-21: retains lot + expiry info per allocation line
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AllocateStockResponse {

    private Long documentId;
    private String documentCode;
    private String status; // ALLOCATED or PARTIALLY_ALLOCATED

    private int totalSkus;
    private int allocatedSkus;

    private List<AllocationLine> allocations;
    private List<ShortageItem> shortages; // items with insufficient stock

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AllocationLine {
        private Long skuId;
        private String skuCode;
        private String skuName;
        private Long lotId;
        private String lotNumber;
        private LocalDate expiryDate;      // BR-WXE-21: traceability
        private Long locationId;
        private String locationCode;
        private String zoneCode;
        private BigDecimal allocatedQty;
        private BigDecimal requestedQty;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ShortageItem {
        private Long skuId;
        private String skuCode;
        private BigDecimal requestedQty;
        private BigDecimal availableQty;
        private BigDecimal shortageQty;
    }
}