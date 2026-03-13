package org.example.sep26management.domain.putaway.suggestion;

public interface PutawaySuggestionEngine {

    PutawaySuggestionLineResponse suggestForLine(PutawaySuggestionRequest request);

    PutawaySuggestionResponse suggestForTask(Long warehouseId, Long putawayTaskId);
}

