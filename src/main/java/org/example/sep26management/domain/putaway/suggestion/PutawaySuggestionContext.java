package org.example.sep26management.domain.putaway.suggestion;

import java.util.Set;

/**
 * Ngữ cảnh đầy đủ cho một lần chạy engine suggestion.
 */
public class PutawaySuggestionContext {

    private final PutawaySuggestionRequest request;
    private final Set<Long> allowedZoneIds;

    public PutawaySuggestionContext(PutawaySuggestionRequest request, Set<Long> allowedZoneIds) {
        this.request = request;
        this.allowedZoneIds = allowedZoneIds;
    }

    public PutawaySuggestionRequest getRequest() {
        return request;
    }

    public Set<Long> getAllowedZoneIds() {
        return allowedZoneIds;
    }
}

