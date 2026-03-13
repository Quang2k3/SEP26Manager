package org.example.sep26management.domain.putaway.suggestion.rules;

import org.example.sep26management.domain.putaway.suggestion.CandidateBin;
import org.example.sep26management.domain.putaway.suggestion.PutawaySuggestionContext;
import org.example.sep26management.domain.putaway.suggestion.ScoringRule;

import java.math.BigDecimal;
import java.util.List;

/**
 * Rule chấm điểm đơn giản: ưu tiên bin có availableCapacity lớn hơn.
 */
public class BasicCapacityScoringRule implements ScoringRule {

    @Override
    public BigDecimal score(PutawaySuggestionContext ctx,
                            CandidateBin bin,
                            List<String> matchedRules) {
        BigDecimal available = bin.getAvailableCapacity();
        if (available == null) {
            return BigDecimal.ZERO;
        }
        matchedRules.add("CAPACITY_AVAILABLE");
        return available;
    }
}

