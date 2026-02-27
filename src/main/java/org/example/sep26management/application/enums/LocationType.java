package org.example.sep26management.application.enums;

/**
 * BR-LOC-04: Location hierarchy must follow Zone → Aisle → Rack → Bin
 * BR-LOC-06: Only BIN can store inventory
 */
public enum LocationType {
    AISLE,
    RACK,
    BIN;

    /**
     * BR-LOC-04: validate allowed parent type
     * AISLE's parent = null (under zone directly)
     * RACK's parent = AISLE
     * BIN's parent = RACK
     */
    public LocationType expectedParentType() {
        return switch (this) {
            case AISLE -> null;   // directly under zone
            case RACK  -> AISLE;
            case BIN   -> RACK;
        };
    }
}