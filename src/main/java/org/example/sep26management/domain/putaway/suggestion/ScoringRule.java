package org.example.sep26management.domain.putaway.suggestion;

import java.math.BigDecimal;
import java.util.List;

public interface ScoringRule {

    BigDecimal score(PutawaySuggestionContext ctx,
                     CandidateBin bin,
                     List<String> matchedRules);
}

