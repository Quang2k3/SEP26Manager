package org.example.sep26management.domain.putaway.suggestion;

import java.math.BigDecimal;
import java.util.List;

/**
 * Gợi ý cho 1 BIN cụ thể trong kết quả suggestion.
 */
public class PutawayBinSuggestionDto {

    private Long binId;
    private String binCode;

    private Long zoneId;
    private String zoneCode;

    private BigDecimal maxCapacity;
    private BigDecimal occupiedQty;
    private BigDecimal reservedQty;
    private BigDecimal availableCapacity;

    private BigDecimal suggestedQuantity;
    private BigDecimal score;

    private boolean splitUsed;
    private List<String> matchedRules;
    private String explanation;

    public Long getBinId() {
        return binId;
    }

    public void setBinId(Long binId) {
        this.binId = binId;
    }

    public String getBinCode() {
        return binCode;
    }

    public void setBinCode(String binCode) {
        this.binCode = binCode;
    }

    public Long getZoneId() {
        return zoneId;
    }

    public void setZoneId(Long zoneId) {
        this.zoneId = zoneId;
    }

    public String getZoneCode() {
        return zoneCode;
    }

    public void setZoneCode(String zoneCode) {
        this.zoneCode = zoneCode;
    }

    public BigDecimal getMaxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(BigDecimal maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public BigDecimal getOccupiedQty() {
        return occupiedQty;
    }

    public void setOccupiedQty(BigDecimal occupiedQty) {
        this.occupiedQty = occupiedQty;
    }

    public BigDecimal getReservedQty() {
        return reservedQty;
    }

    public void setReservedQty(BigDecimal reservedQty) {
        this.reservedQty = reservedQty;
    }

    public BigDecimal getAvailableCapacity() {
        return availableCapacity;
    }

    public void setAvailableCapacity(BigDecimal availableCapacity) {
        this.availableCapacity = availableCapacity;
    }

    public BigDecimal getSuggestedQuantity() {
        return suggestedQuantity;
    }

    public void setSuggestedQuantity(BigDecimal suggestedQuantity) {
        this.suggestedQuantity = suggestedQuantity;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public boolean isSplitUsed() {
        return splitUsed;
    }

    public void setSplitUsed(boolean splitUsed) {
        this.splitUsed = splitUsed;
    }

    public List<String> getMatchedRules() {
        return matchedRules;
    }

    public void setMatchedRules(List<String> matchedRules) {
        this.matchedRules = matchedRules;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }
}

