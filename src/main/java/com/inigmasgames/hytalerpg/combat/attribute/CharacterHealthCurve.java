package com.inigmasgames.hytalerpg.combat.attribute;

/** Natural player Health capacity; level and allocated attributes remain separately authoritative. */
public final class CharacterHealthCurve {
    private CharacterHealthCurve() {}

    public static int naturalMaximum(int level) {
        if (level < 1 || level > 99) throw new IllegalArgumentException("Character level must be 1..99");
        return 100 + (288 * (level - 1)) / 98;
    }
}
