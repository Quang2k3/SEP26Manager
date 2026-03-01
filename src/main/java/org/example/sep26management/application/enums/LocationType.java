package org.example.sep26management.application.enums;

/**
 * BR-LOC-04: Location hierarchy must follow Zone → Aisle → Rack → Bin
 * BR-LOC-06: Only BIN can store inventory
 */
public enum LocationType {
    AISLE,
    RACK,
    BIN,
    STAGING;  // Added: exists in DB data (e.g. WH01-INB-STAGE)

    /**
     * BR-LOC-04: validate allowed parent type
     * AISLE's parent = null (directly under zone)
     * RACK's parent = AISLE
     * BIN's parent = RACK
     * STAGING's parent = null (staging areas sit directly under zone like AISLE)
     */
    public LocationType expectedParentType() {
        return switch (this) {
            case AISLE, STAGING -> null;
            case RACK            -> AISLE;
            case BIN             -> RACK;
        };
    }

    /**
     * BR-LOC-06: only BIN can store inventory
     */
    public boolean canStoreInventory() {
        return this == BIN;
    }
}