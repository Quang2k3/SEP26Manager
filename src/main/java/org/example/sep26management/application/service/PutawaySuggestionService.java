package org.example.sep26management.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.sep26management.application.dto.response.PutawaySuggestion;
import org.example.sep26management.infrastructure.persistence.entity.CategoryEntity;
import org.example.sep26management.infrastructure.persistence.entity.LocationEntity;
import org.example.sep26management.infrastructure.persistence.entity.SkuEntity;
import org.example.sep26management.infrastructure.persistence.entity.ZoneEntity;
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

    /**
     * Gợi ý BIN location cho 1 SKU trong warehouse.
     *
     * @param warehouseId warehouse đang nhập hàng
     * @param skuId       SKU cần putaway
     * @param qty         số lượng cần putaway (dùng để check capacity)
     * @return Optional<PutawaySuggestion> — empty nếu không tìm được zone/bin phù
     *         hợp
     */
    @Transactional(readOnly = true)
    public Optional<PutawaySuggestion> suggestLocation(Long warehouseId, Long skuId, BigDecimal qty) {
        // 1. Lookup SKU → Category
        SkuEntity sku = skuRepo.findById(skuId).orElse(null);
        if (sku == null || sku.getCategory() == null) {
            log.warn("Putaway suggestion: SKU {} not found or has no category", skuId);
            return Optional.empty();
        }

        CategoryEntity category = sku.getCategory();
        String categoryCode = category.getCategoryCode();

        // 2. Convention: zone_code = "Z-" + category_code
        String zoneCode = "Z-" + categoryCode;
        Optional<ZoneEntity> zoneOpt = zoneRepo.findByWarehouseIdAndZoneCode(warehouseId, zoneCode);
        if (zoneOpt.isEmpty() || !zoneOpt.get().getActive()) {
            log.warn("Putaway suggestion: Zone {} not found or inactive in warehouse {}", zoneCode, warehouseId);
            return Optional.empty();
        }

        ZoneEntity zone = zoneOpt.get();

        // 3. Find all active BINs in the matched zone
        List<LocationEntity> bins = locationRepo.findActiveBinsByZone(zone.getZoneId());
        if (bins.isEmpty()) {
            log.warn("Putaway suggestion: No active BINs in zone {} ({})", zoneCode, zone.getZoneId());
            return Optional.empty();
        }

        // 4. Find the best BIN: most available capacity, or first empty bin
        LocationEntity bestBin = null;
        BigDecimal bestAvailable = BigDecimal.ZERO;
        BigDecimal bestCurrentQty = BigDecimal.ZERO;

        for (LocationEntity bin : bins) {
            BigDecimal currentQty = locationRepo.getCurrentOccupiedQty(bin.getLocationId());
            BigDecimal maxCap = bin.getMaxWeightKg() != null ? bin.getMaxWeightKg() : new BigDecimal("999999");
            BigDecimal available = maxCap.subtract(currentQty);

            // Skip bins that don't have enough capacity for this qty
            if (available.compareTo(qty) < 0) {
                continue;
            }

            // Prefer bins with most available space
            if (bestBin == null || available.compareTo(bestAvailable) > 0) {
                bestBin = bin;
                bestAvailable = available;
                bestCurrentQty = currentQty;
            }
        }

        if (bestBin == null) {
            log.warn("Putaway suggestion: No BIN with sufficient capacity in zone {} for qty {}", zoneCode, qty);
            return Optional.empty();
        }

        // 5. Resolve parent rack and aisle names
        String rackName = null;
        String aisleName = null;
        if (bestBin.getParentLocationId() != null) {
            LocationEntity rack = locationRepo.findById(bestBin.getParentLocationId()).orElse(null);
            if (rack != null) {
                rackName = rack.getLocationCode();
                if (rack.getParentLocationId() != null) {
                    LocationEntity aisle = locationRepo.findById(rack.getParentLocationId()).orElse(null);
                    if (aisle != null) {
                        aisleName = aisle.getLocationCode();
                    }
                }
            }
        }

        BigDecimal maxCap = bestBin.getMaxWeightKg() != null ? bestBin.getMaxWeightKg() : new BigDecimal("999999");

        PutawaySuggestion suggestion = PutawaySuggestion.builder()
                .skuId(skuId)
                .skuCode(sku.getSkuCode())
                .categoryCode(categoryCode)
                .matchedZoneCode(zoneCode)
                .matchedZoneId(zone.getZoneId())
                .matchedZoneName(zone.getZoneName())
                .suggestedLocationId(bestBin.getLocationId())
                .suggestedLocationCode(bestBin.getLocationCode())
                .aisleName(aisleName)
                .rackName(rackName)
                .currentQty(bestCurrentQty)
                .maxCapacity(maxCap)
                .availableCapacity(bestAvailable)
                .reason("Zone " + zoneCode + " matched category " + categoryCode)
                .build();

        log.info("Putaway suggestion: SKU {} (category {}) → zone {} → bin {} (available: {})",
                sku.getSkuCode(), categoryCode, zoneCode, bestBin.getLocationCode(), bestAvailable);

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
