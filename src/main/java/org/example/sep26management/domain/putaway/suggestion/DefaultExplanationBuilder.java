package org.example.sep26management.domain.putaway.suggestion;

import java.math.BigDecimal;
import java.util.List;

public class DefaultExplanationBuilder implements ExplanationBuilder {

    @Override
    public String buildOverall(PutawaySuggestionContext ctx,
                               List<PutawayBinSuggestionDto> binSuggestions,
                               boolean fullyAllocated) {

        if (binSuggestions == null || binSuggestions.isEmpty()) {
            return "No suitable bin found for this line.";
        }

        BigDecimal totalSuggested = binSuggestions.stream()
                .map(PutawayBinSuggestionDto::getSuggestedQuantity)
                .filter(q -> q != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        StringBuilder sb = new StringBuilder();
        sb.append("Suggested ").append(totalSuggested).append(" units into ")
                .append(binSuggestions.size()).append(" bin(s). ");

        if (!fullyAllocated) {
            BigDecimal remaining = ctx.getRequest().getQuantity() != null
                    ? ctx.getRequest().getQuantity().subtract(totalSuggested)
                    : BigDecimal.ZERO;
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                sb.append("Remaining ").append(remaining).append(" units not allocated due to capacity limits. ");
            }
        }

        return sb.toString();
    }
}

