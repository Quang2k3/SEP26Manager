package org.example.sep26management.domain.putaway.suggestion;

import java.util.List;

public interface EligibilityRule {

    boolean isEligible(PutawaySuggestionContext ctx,
                       CandidateBin bin,
                       List<String> reasons);
}

