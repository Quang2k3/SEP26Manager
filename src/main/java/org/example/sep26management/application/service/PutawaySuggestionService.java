package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.response.PutawaySuggestion;
import org.example.sep26management.domain.putaway.suggestion.DefaultPutawaySuggestionEngine;
import org.example.sep26management.domain.putaway.suggestion.PutawayBinSuggestionDto;
import org.example.sep26management.domain.putaway.suggestion.PutawaySuggestionEngine;
import org.example.sep26management.domain.putaway.suggestion.PutawaySuggestionLineResponse;
import org.example.sep26management.domain.putaway.suggestion.PutawaySuggestionRequest;
import org.example.sep26management.infrastructure.persistence.entity.SkuEntity;
import org.example.sep26management.infrastructure.persistence.entity.ZoneEntity;
import org.example.sep26management.infrastructure.persistence.repository.InventorySnapshotJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.LocationJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.SkuJpaRepository;
import org.example.sep26management.infrastructure.persistence.repository.ZoneJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Service gợi ý vị trí putaway dựa trên zone-category matching.
 *
 * Convention: zone_code = "Z-" + category_code
 * Ví dụ: SKU thuộc category "HC" → gợi ý BIN trong zone "Z-HC"
 *
 * Logic:
 * 1. SKU → Category → categoryCode
 * 2. zoneCode = "Z-" + categoryCode → find Zone in warehouse
 * 3. Find all active BINs in that zone
 * 4. Calculate remaining capacity per BIN (maxWeightKg - currentQty)
 * 5. Suggest the BIN with the most available capacity
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PutawaySuggestionService {

    private final SkuJpaRepository skuRepo;
    private final ZoneJpaRepository zoneRepo;
    private final LocationJpaRepository locationRepo;
    private final InventorySnapshotJpaRepository snapshotRepo;

    private PutawaySuggestionEngine engine;

    private PutawaySuggestionEngine engine() {
        if (engine == null) {
            engine = new DefaultPutawaySuggestionEngine(skuRepo, zoneRepo, locationRepo, snapshotRepo);
        }
        return engine;
    }

    /**
     * Gợi ý BIN location cho 1 SKU trong warehouse (API cũ, trả về 1 bin tốt nhất).
     */
    @Transactional(readOnly = true)
    public Optional<PutawaySuggestion> suggestLocation(Long warehouseId, Long skuId, BigDecimal qty) {
        SkuEntity sku = skuRepo.findByIdWithCategory(skuId).orElse(null);
        if (sku == null) {
            log.warn("Putaway suggestion FAILED: SKU {} not found in database", skuId);
            return Optional.empty();
        }

        if (sku.getCategory() == null) {
            log.warn("Putaway suggestion FAILED: SKU {} ({}) has no category assigned", skuId, sku.getSkuCode());
            return Optional.empty();
        }

        String categoryCode = sku.getCategory().getCategoryCode();
        String expectedZoneCode = "Z-" + categoryCode;
        log.info("Putaway suggestion: SKU {} ({}) → category={} → looking for zone '{}'",
                skuId, sku.getSkuCode(), categoryCode, expectedZoneCode);

        PutawaySuggestionRequest req = new PutawaySuggestionRequest();
        req.setWarehouseId(warehouseId);
        req.setSkuId(skuId);
        req.setSkuCode(sku.getSkuCode());
        req.setQuantity(qty);
        // cho phép split mặc định
        req.setSplitAllowed(true);
        req.setStrategyCode(null);

        PutawaySuggestionLineResponse lineRes = engine().suggestForLine(req);
        List<PutawayBinSuggestionDto> bins = lineRes.getBinSuggestions();
        if (bins == null || bins.isEmpty()) {
            return Optional.empty();
        }

        PutawayBinSuggestionDto top = bins.get(0);

        // Lấy lại thông tin zone để mapping về DTO cũ
        ZoneEntity zone = null;
        if (top.getZoneId() != null) {
            zone = zoneRepo.findById(top.getZoneId()).orElse(null);
        }

        PutawaySuggestion suggestion = PutawaySuggestion.builder()
                .skuId(skuId)
                .skuCode(sku.getSkuCode())
                .categoryCode(sku.getCategory() != null ? sku.getCategory().getCategoryCode() : null)
                .matchedZoneCode(zone != null ? zone.getZoneCode() : top.getZoneCode())
                .matchedZoneId(zone != null ? zone.getZoneId() : null)
                .matchedZoneName(zone != null ? zone.getZoneName() : null)
                .suggestedLocationId(top.getBinId())
                .suggestedLocationCode(top.getBinCode())
                .aisleName(null)
                .rackName(null)
                .currentQty(top.getOccupiedQty())
                .maxCapacity(top.getMaxCapacity())
                .availableCapacity(top.getAvailableCapacity())
                .reason(lineRes.getOverallExplanation())
                .build();

        log.info("Putaway suggestion (engine): SKU {} → bin {} (qty {}, score={})",
                sku.getSkuCode(), top.getBinCode(), top.getSuggestedQuantity(), top.getScore());

        return Optional.of(suggestion);
    }

    /**
     * Gợi ý cho nhiều SKUs cùng lúc (dùng cho xem suggestions của 1 task).
     */
    @Transactional(readOnly = true)
    public List<PutawaySuggestion> suggestLocations(Long warehouseId, List<Long> skuIds, List<BigDecimal> quantities) {
        return java.util.stream.IntStream.range(0, skuIds.size())
                .mapToObj(i -> suggestLocation(warehouseId, skuIds.get(i), quantities.get(i)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(java.util.stream.Collectors.toList());
    }
}
