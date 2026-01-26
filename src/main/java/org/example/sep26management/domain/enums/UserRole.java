package org.example.sep26management.domain.enums;

public enum UserRole {
    MANAGER("Warehouse Manager"),
    ACCOUNTANT("Warehouse Accountant"),
    KEEPER("Warehouse Keeper");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}