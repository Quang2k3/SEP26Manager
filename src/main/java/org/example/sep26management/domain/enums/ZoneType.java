package org.example.sep26management.domain.enums;

public enum ZoneType {
    INBOUND("Inbound Staging Area"),
    STORAGE("Storage Area"),
    OUTBOUND("Outbound Staging Area"),
    HOLD("Quality Hold Area"),
    DEFECT("Defective Items Area");

    private final String displayName;

    ZoneType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
