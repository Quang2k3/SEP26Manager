package org.example.sep26management.domain.putaway.suggestion;

import java.util.List;

public interface ExplanationBuilder {

    String buildOverall(PutawaySuggestionContext ctx,
                        List<PutawayBinSuggestionDto> binSuggestions,
                        boolean fullyAllocated);
}

