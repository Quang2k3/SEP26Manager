package org.example.sep26management.domain.putaway.suggestion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Kết quả suggestion cho 1 dòng putaway task.
 */
public class PutawaySuggestionLineResponse {

    private Long putawayTaskItemId;
    private Long skuId;
    private String skuCode;
    private Long lotId;
    private String lotNumber;
    private LocalDate expiryDate;
    private BigDecimal totalQty;

    private List<PutawayBinSuggestionDto> binSuggestions;
    private boolean fullyAllocated;
    private String overallExplanation;

    public Long getPutawayTaskItemId() {
        return putawayTaskItemId;
    }

    public void setPutawayTaskItemId(Long putawayTaskItemId) {
        this.putawayTaskItemId = putawayTaskItemId;
    }

    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public void setSkuCode(String skuCode) {
        this.skuCode = skuCode;
    }

    public Long getLotId() {
        return lotId;
    }

    public void setLotId(Long lotId) {
        this.lotId = lotId;
    }

    public String getLotNumber() {
        return lotNumber;
    }

    public void setLotNumber(String lotNumber) {
        this.lotNumber = lotNumber;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public BigDecimal getTotalQty() {
        return totalQty;
    }

    public void setTotalQty(BigDecimal totalQty) {
        this.totalQty = totalQty;
    }

    public List<PutawayBinSuggestionDto> getBinSuggestions() {
        return binSuggestions;
    }

    public void setBinSuggestions(List<PutawayBinSuggestionDto> binSuggestions) {
        this.binSuggestions = binSuggestions;
    }

    public boolean isFullyAllocated() {
        return fullyAllocated;
    }

    public void setFullyAllocated(boolean fullyAllocated) {
        this.fullyAllocated = fullyAllocated;
    }

    public String getOverallExplanation() {
        return overallExplanation;
    }

    public void setOverallExplanation(String overallExplanation) {
        this.overallExplanation = overallExplanation;
    }
}

