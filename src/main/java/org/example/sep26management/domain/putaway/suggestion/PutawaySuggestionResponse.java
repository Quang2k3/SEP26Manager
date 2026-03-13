package org.example.sep26management.domain.putaway.suggestion;

import java.util.List;

/**
 * Kết quả suggestion cho toàn bộ putaway task.
 */
public class PutawaySuggestionResponse {

    private Long putawayTaskId;
    private Long warehouseId;
    private List<PutawaySuggestionLineResponse> lines;

    public Long getPutawayTaskId() {
        return putawayTaskId;
    }

    public void setPutawayTaskId(Long putawayTaskId) {
        this.putawayTaskId = putawayTaskId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public List<PutawaySuggestionLineResponse> getLines() {
        return lines;
    }

    public void setLines(List<PutawaySuggestionLineResponse> lines) {
        this.lines = lines;
    }
}

