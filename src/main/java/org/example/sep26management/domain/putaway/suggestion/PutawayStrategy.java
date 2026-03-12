package org.example.sep26management.domain.putaway.suggestion;

import java.util.List;

public interface PutawayStrategy {

    List<PutawayBinSuggestionDto> allocate(
            PutawaySuggestionContext ctx,
            List<ScoredBin> scoredBins
    );
}

