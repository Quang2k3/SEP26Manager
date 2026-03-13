package org.example.sep26management.domain.putaway.suggestion.rules;

import org.example.sep26management.domain.putaway.suggestion.CandidateBin;
import org.example.sep26management.domain.putaway.suggestion.EligibilityRule;
import org.example.sep26management.domain.putaway.suggestion.PutawaySuggestionContext;

import java.math.BigDecimal;
import java.util.List;

public class CapacityEligibilityRule implements EligibilityRule {

    @Override
    public boolean isEligible(PutawaySuggestionContext ctx,
                              CandidateBin bin,
                              List<String> reasons) {

        BigDecimal available = bin.getAvailableCapacity();
        if (available == null || available.compareTo(BigDecimal.ZERO) <= 0) {
            reasons.add("NO_CAPACITY");
            return false;
        }

        BigDecimal qty = ctx.getRequest().getQuantity() != null
                ? ctx.getRequest().getQuantity()
                : BigDecimal.ZERO;

        if (Boolean.FALSE.equals(ctx.getRequest().getSplitAllowed())
                && available.compareTo(qty) < 0) {
            reasons.add("NOT_ENOUGH_CAPACITY_FOR_FULL_QTY");
            return false;
        }

        return true;
    }
}

