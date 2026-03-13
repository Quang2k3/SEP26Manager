package org.example.sep26management.domain.putaway.suggestion;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Input chuẩn hóa cho engine gợi ý putaway, tương ứng với 1 dòng putaway task.
 */
public class PutawaySuggestionRequest {

    private Long warehouseId;
    private Long putawayTaskId;
    private Long putawayTaskItemId;

    private Long skuId;
    private String skuCode;

    private Long lotId;
    private String lotNumber;
    private LocalDate expiryDate;

    private BigDecimal quantity;
    private String uom;

    private BigDecimal weight;
    private BigDecimal volume;

    private String qcStatus;
    private String storageCondition;

    private Boolean splitAllowed;
    private String strategyCode;

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Long getPutawayTaskId() {
        return putawayTaskId;
    }

    public void setPutawayTaskId(Long putawayTaskId) {
        this.putawayTaskId = putawayTaskId;
    }

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

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getUom() {
        return uom;
    }

    public void setUom(String uom) {
        this.uom = uom;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }

    public String getQcStatus() {
        return qcStatus;
    }

    public void setQcStatus(String qcStatus) {
        this.qcStatus = qcStatus;
    }

    public String getStorageCondition() {
        return storageCondition;
    }

    public void setStorageCondition(String storageCondition) {
        this.storageCondition = storageCondition;
    }

    public Boolean getSplitAllowed() {
        return splitAllowed;
    }

    public void setSplitAllowed(Boolean splitAllowed) {
        this.splitAllowed = splitAllowed;
    }

    public String getStrategyCode() {
        return strategyCode;
    }

    public void setStrategyCode(String strategyCode) {
        this.strategyCode = strategyCode;
    }
}

