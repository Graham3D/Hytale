package com.inigmasgames.taverns;

/** Stable persisted Core type identifiers. Unimplemented types remain unregistered in CoreDefinitions. */
public enum CoreType {
    TAVERN(true),
    KITCHEN(false),
    BEDROOM(false),
    BAR(false),
    RESERVED(false);

    private final boolean primary;

    CoreType(boolean primary) {
        this.primary = primary;
    }

    public boolean isPrimary() {
        return primary;
    }
}
