package org.example.sep26management.domain.putaway.suggestion.strategy;

import org.example.sep26management.domain.putaway.suggestion.CandidateBin;
import org.example.sep26management.domain.putaway.suggestion.PutawayBinSuggestionDto;
import org.example.sep26management.domain.putaway.suggestion.PutawayStrategy;
import org.example.sep26management.domain.putaway.suggestion.PutawaySuggestionContext;
import org.example.sep26management.domain.putaway.suggestion.ScoredBin;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class SplitAcrossBinsStrategy implements PutawayStrategy {

    @Override
    public List<PutawayBinSuggestionDto> allocate(PutawaySuggestionContext ctx,
                                                  List<ScoredBin> scoredBins) {

        BigDecimal remaining = ctx.getRequest().getQuantity() != null
                ? ctx.getRequest().getQuantity()
                : BigDecimal.ZERO;

        List<PutawayBinSuggestionDto> result = new ArrayList<>();
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return result;
        }

        for (ScoredBin sb : scoredBins) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            CandidateBin bin = sb.getBin();
            BigDecimal available = bin.getAvailableCapacity();
            if (available == null || available.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal canPut = available.min(remaining);
            if (canPut.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            PutawayBinSuggestionDto dto = new PutawayBinSuggestionDto();
            dto.setBinId(bin.getLocation().getLocationId());
            dto.setBinCode(bin.getLocation().getLocationCode());
            dto.setZoneId(bin.getLocation().getZoneId());
            dto.setZoneCode(bin.getZoneCode());
            dto.setMaxCapacity(bin.getMaxCapacity());
            dto.setOccupiedQty(bin.getOccupiedQty());
            dto.setReservedQty(bin.getReservedQty());
            dto.setAvailableCapacity(bin.getAvailableCapacity());
            dto.setSuggestedQuantity(canPut);
            dto.setScore(sb.getScore());
            dto.setSplitUsed(true);
            dto.setMatchedRules(new ArrayList<>(sb.getMatchedRules()));
            dto.setExplanation("Allocated " + canPut + " units to bin " + bin.getLocation().getLocationCode());

            result.add(dto);
            remaining = remaining.subtract(canPut);
        }

        return result;
    }
}

