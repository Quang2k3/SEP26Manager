package org.example.sep26management.domain.putaway.suggestion;

import org.example.sep26management.infrastructure.persistence.entity.LocationEntity;

import java.math.BigDecimal;

/**
 * Thông tin BIN sau khi đã enrich occupancy/capacity, dùng cho filter/score.
 */
public class CandidateBin {

    private final LocationEntity location;
    private final String zoneCode;

    private final BigDecimal maxCapacity;
    private final BigDecimal occupiedQty;
    private final BigDecimal reservedQty;
    private final BigDecimal availableCapacity;

    public CandidateBin(LocationEntity location,
                        String zoneCode,
                        BigDecimal maxCapacity,
                        BigDecimal occupiedQty,
                        BigDecimal reservedQty,
                        BigDecimal availableCapacity) {
        this.location = location;
        this.zoneCode = zoneCode;
        this.maxCapacity = maxCapacity;
        this.occupiedQty = occupiedQty;
        this.reservedQty = reservedQty;
        this.availableCapacity = availableCapacity;
    }

    public LocationEntity getLocation() {
        return location;
    }

    public String getZoneCode() {
        return zoneCode;
    }

    public BigDecimal getMaxCapacity() {
        return maxCapacity;
    }

    public BigDecimal getOccupiedQty() {
        return occupiedQty;
    }

    public BigDecimal getReservedQty() {
        return reservedQty;
    }

    public BigDecimal getAvailableCapacity() {
        return availableCapacity;
    }
}

