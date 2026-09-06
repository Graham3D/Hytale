package com.inigmasgames.hytalerpg.domain;

import java.util.Locale;

public enum PassiveSlot {
    PASSIVE01(0), PASSIVE02(1), PASSIVE03(2), PASSIVE04(3), PASSIVE05(4), PASSIVE06(5);
    private final int index;
    PassiveSlot(int index) { this.index = index; }
    public int index() { return index; }
    public String externalId() { return name().toLowerCase(Locale.ROOT); }
    public static PassiveSlot parse(String value) {
        String normalized = value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        for (PassiveSlot slot : values()) {
            if (normalized.equals(slot.name()) || normalized.equals("PASSIVE" + (slot.index + 1))) return slot;
        }
        throw new IllegalArgumentException("Unknown Passive slot: " + value + " (expected passive01..passive06)");
    }
}
