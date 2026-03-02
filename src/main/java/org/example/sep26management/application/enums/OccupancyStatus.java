package org.example.sep26management.application.enums;

/**
 * BR-LOC-20: Occupancy calculated from current inventory in bin
 * BR-LOC-21: FULL when occupied quantity >= configured capacity
 */
public enum OccupancyStatus {
    EMPTY,    // quantity == 0
    PARTIAL,  // 0 < quantity < max capacity
    FULL;     // quantity >= max capacity

    /**
     * Calculate status based on occupied quantity vs max capacity.
     * If no capacity configured → cannot determine FULL, treat PARTIAL when has stock.
     */
    public static OccupancyStatus of(java.math.BigDecimal occupied, java.math.BigDecimal maxCapacity) {
        if (occupied == null || occupied.compareTo(java.math.BigDecimal.ZERO) <= 0) return EMPTY;
        if (maxCapacity == null || maxCapacity.compareTo(java.math.BigDecimal.ZERO) <= 0) return PARTIAL;
        return occupied.compareTo(maxCapacity) >= 0 ? FULL : PARTIAL;
    }
}