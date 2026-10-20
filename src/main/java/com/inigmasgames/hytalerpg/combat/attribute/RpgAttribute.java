package com.inigmasgames.hytalerpg.combat.attribute;

import java.util.Locale;

public enum RpgAttribute {
    STR, DEX, INT, WIS, LUCK;

    public static RpgAttribute parse(String value) {
        try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (Exception error) { throw new IllegalArgumentException("Attribute must be STR, DEX, INT, WIS, or LUCK."); }
    }
}
