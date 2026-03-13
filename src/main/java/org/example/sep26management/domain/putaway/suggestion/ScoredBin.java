package org.example.sep26management.domain.putaway.suggestion;

import java.math.BigDecimal;
import java.util.List;

public class ScoredBin {

    private final CandidateBin bin;
    private final BigDecimal score;
    private final List<String> matchedRules;

    public ScoredBin(CandidateBin bin, BigDecimal score, List<String> matchedRules) {
        this.bin = bin;
        this.score = score;
        this.matchedRules = matchedRules;
    }

    public CandidateBin getBin() {
        return bin;
    }

    public BigDecimal getScore() {
        return score;
    }

    public List<String> getMatchedRules() {
        return matchedRules;
    }
}

