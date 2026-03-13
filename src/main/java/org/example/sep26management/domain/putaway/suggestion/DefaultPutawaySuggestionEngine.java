package org.example.sep26management.domain.putaway.suggestion;

import org.example.sep26management.domain.putaway.suggestion.rules.BasicCapacityScoringRule;
import org.example.sep26management.domain.putaway.suggestion.rules.CapacityEligibilityRule;
import org.example.sep26management.domain.putaway.suggestion.strategy.NoSplitStrategy;
import org.example.sep26management.domain.putaway.suggestion.strategy.SplitAcrossBinsStrategy;
import org.example.sep26management.infrastructure.persistence.entity.LocationEntity;
import org.example.sep26management.infrastructure.persistence.entity.SkuEntity;
import org.example.sep26management.infrastructure.persistence.entity.ZoneEntity;
import org.example.sep26management.infrastructure.persistence.repository.InventorySnapshotJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.LocationJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.SkuJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.ZoneJpaRepository;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Triển khai mặc định của engine gợi ý putaway.
 * Hiện tại: áp dụng zone theo convention Z-{categoryCode} và rule capacity cơ bản.
 */
public class DefaultPutawaySuggestionEngine implements PutawaySuggestionEngine {

    private final SkuJpaRepository skuRepo;
    private final ZoneJpaRepository zoneRepo;
    private final LocationJpaRepository locationRepo;
    private final InventorySnapshotJpaRepository snapshotRepo;

    private final List<EligibilityRule> eligibilityRules;
    private final List<ScoringRule> scoringRules;
    private final Map<String, PutawayStrategy> strategies;
    private final ExplanationBuilder explanationBuilder;

    public DefaultPutawaySuggestionEngine(SkuJpaRepository skuRepo,
                                          ZoneJpaRepository zoneRepo,
                                          LocationJpaRepository locationRepo,
                                          InventorySnapshotJpaRepository snapshotRepo) {
        this.skuRepo = skuRepo;
        this.zoneRepo = zoneRepo;
        this.locationRepo = locationRepo;
        this.snapshotRepo = snapshotRepo;

        this.eligibilityRules = List.of(new CapacityEligibilityRule());
        this.scoringRules = List.of(new BasicCapacityScoringRule());

        Map<String, PutawayStrategy> strategyMap = new HashMap<>();
        strategyMap.put("NO_SPLIT", new NoSplitStrategy());
        strategyMap.put("SPLIT", new SplitAcrossBinsStrategy());
        this.strategies = Collections.unmodifiableMap(strategyMap);

        this.explanationBuilder = new DefaultExplanationBuilder();
    }

    @Override
    public PutawaySuggestionLineResponse suggestForLine(PutawaySuggestionRequest request) {
        PutawaySuggestionContext ctx = buildContext(request);

        List<LocationEntity> bins = loadCandidateBins(ctx);
        if (bins.isEmpty()) {
            PutawaySuggestionLineResponse res = new PutawaySuggestionLineResponse();
            res.setPutawayTaskItemId(request.getPutawayTaskItemId());
            res.setSkuId(request.getSkuId());
            res.setSkuCode(request.getSkuCode());
            res.setLotId(request.getLotId());
            res.setLotNumber(request.getLotNumber());
            res.setExpiryDate(request.getExpiryDate());
            res.setTotalQty(request.getQuantity());
            res.setBinSuggestions(Collections.emptyList());
            res.setFullyAllocated(false);
            res.setOverallExplanation("No active BINs found in matched zone.");
            return res;
        }

        List<CandidateBin> candidates = enrichCandidates(ctx, bins);

        List<CandidateBin> eligible = new ArrayList<>();
        for (CandidateBin bin : candidates) {
            boolean ok = true;
            List<String> reasons = new ArrayList<>();
            for (EligibilityRule rule : eligibilityRules) {
                if (!rule.isEligible(ctx, bin, reasons)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                eligible.add(bin);
            }
        }

        if (eligible.isEmpty()) {
            PutawaySuggestionLineResponse res = new PutawaySuggestionLineResponse();
            res.setPutawayTaskItemId(request.getPutawayTaskItemId());
            res.setSkuId(request.getSkuId());
            res.setSkuCode(request.getSkuCode());
            res.setLotId(request.getLotId());
            res.setLotNumber(request.getLotNumber());
            res.setExpiryDate(request.getExpiryDate());
            res.setTotalQty(request.getQuantity());
            res.setBinSuggestions(Collections.emptyList());
            res.setFullyAllocated(false);
            res.setOverallExplanation("No BIN has enough capacity for this line.");
            return res;
        }

        List<ScoredBin> scored = new ArrayList<>();
        for (CandidateBin bin : eligible) {
            BigDecimal totalScore = BigDecimal.ZERO;
            List<String> matchedRules = new ArrayList<>();
            for (ScoringRule rule : scoringRules) {
                BigDecimal s = rule.score(ctx, bin, matchedRules);
                if (s != null) {
                    totalScore = totalScore.add(s);
                }
            }
            scored.add(new ScoredBin(bin, totalScore, matchedRules));
        }

        scored.sort(Comparator.comparing(ScoredBin::getScore).reversed());

        PutawayStrategy strategy = resolveStrategy(request);
        List<PutawayBinSuggestionDto> binSuggestions = strategy.allocate(ctx, scored);

        BigDecimal totalSuggested = binSuggestions.stream()
                .map(PutawayBinSuggestionDto::getSuggestedQuantity)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean fullyAllocated = request.getQuantity() != null
                && totalSuggested.compareTo(request.getQuantity()) >= 0;

        String overallExplanation = explanationBuilder.buildOverall(ctx, binSuggestions, fullyAllocated);

        PutawaySuggestionLineResponse res = new PutawaySuggestionLineResponse();
        res.setPutawayTaskItemId(request.getPutawayTaskItemId());
        res.setSkuId(request.getSkuId());
        res.setSkuCode(request.getSkuCode());
        res.setLotId(request.getLotId());
        res.setLotNumber(request.getLotNumber());
        res.setExpiryDate(request.getExpiryDate());
        res.setTotalQty(request.getQuantity());
        res.setBinSuggestions(binSuggestions);
        res.setFullyAllocated(fullyAllocated);
        res.setOverallExplanation(overallExplanation);
        return res;
    }

    @Override
    public PutawaySuggestionResponse suggestForTask(Long warehouseId, Long putawayTaskId) {
        // Chưa implement: application layer sẽ map task items thành nhiều PutawaySuggestionRequest
        // và tự gọi suggestForLine cho từng dòng.
        PutawaySuggestionResponse res = new PutawaySuggestionResponse();
        res.setWarehouseId(warehouseId);
        res.setPutawayTaskId(putawayTaskId);
        res.setLines(Collections.emptyList());
        return res;
    }

    private PutawaySuggestionContext buildContext(PutawaySuggestionRequest request) {
        // Tạm thời: allowedZoneIds chỉ gồm zone khớp theo convention category → Z-{categoryCode}
        Set<Long> allowedZoneIds = resolveAllowedZones(request);
        return new PutawaySuggestionContext(request, allowedZoneIds);
    }

    private Set<Long> resolveAllowedZones(PutawaySuggestionRequest request) {
        if (request.getSkuId() == null || request.getWarehouseId() == null) {
            return Collections.emptySet();
        }

        SkuEntity sku = skuRepo.findByIdWithCategory(request.getSkuId()).orElse(null);
        if (sku == null || sku.getCategory() == null) {
            return Collections.emptySet();
        }

        String categoryCode = sku.getCategory().getCategoryCode();
        String zoneCode = "Z-" + categoryCode;

        Optional<ZoneEntity> zoneOpt = zoneRepo.findByWarehouseIdAndZoneCode(request.getWarehouseId(), zoneCode);
        return zoneOpt.filter(ZoneEntity::getActive)
                .map(z -> Collections.singleton(z.getZoneId()))
                .orElse(Collections.emptySet());
    }

    private List<LocationEntity> loadCandidateBins(PutawaySuggestionContext ctx) {
        if (ctx.getAllowedZoneIds().isEmpty()) {
            return Collections.emptyList();
        }
        Long zoneId = ctx.getAllowedZoneIds().iterator().next();
        return locationRepo.findActiveBinsByZone(zoneId);
    }

    private List<CandidateBin> enrichCandidates(PutawaySuggestionContext ctx, List<LocationEntity> bins) {
        List<Long> locationIds = bins.stream()
                .map(LocationEntity::getLocationId)
                .collect(Collectors.toList());

        Map<Long, BigDecimal> occupiedMap = snapshotRepo.sumQuantityByLocationIds(locationIds);
        Map<Long, BigDecimal> reservedMap = snapshotRepo.sumReservedByLocationIds(locationIds);

        Map<Long, String> zoneCodes = bins.stream()
                .map(LocationEntity::getZoneId)
                .filter(Objects::nonNull)
                .distinct()
                .map(id -> zoneRepo.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(ZoneEntity::getZoneId, ZoneEntity::getZoneCode));

        List<CandidateBin> result = new ArrayList<>();
        for (LocationEntity bin : bins) {
            BigDecimal occupied = occupiedMap.getOrDefault(bin.getLocationId(), BigDecimal.ZERO);
            BigDecimal reserved = reservedMap.getOrDefault(bin.getLocationId(), BigDecimal.ZERO);
            BigDecimal maxCap = bin.getMaxWeightKg() != null ? bin.getMaxWeightKg() : BigDecimal.ZERO;
            BigDecimal available = maxCap.subtract(occupied).subtract(reserved);
            if (available.compareTo(BigDecimal.ZERO) < 0) {
                available = BigDecimal.ZERO;
            }

            String zoneCode = bin.getZoneId() != null ? zoneCodes.get(bin.getZoneId()) : null;

            result.add(new CandidateBin(
                    bin,
                    zoneCode,
                    maxCap,
                    occupied,
                    reserved,
                    available));
        }

        return result;
    }

    private PutawayStrategy resolveStrategy(PutawaySuggestionRequest request) {
        String code = request.getStrategyCode();
        if (code == null || code.isBlank()) {
            // nếu splitAllowed = false thì mặc định dùng NO_SPLIT, ngược lại SPLIT
            if (Boolean.FALSE.equals(request.getSplitAllowed())) {
                return strategies.getOrDefault("NO_SPLIT", new NoSplitStrategy());
            }
            return strategies.getOrDefault("SPLIT", new SplitAcrossBinsStrategy());
        }
        return strategies.getOrDefault(code, new SplitAcrossBinsStrategy());
    }
}

