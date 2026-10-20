package com.inigmasgames.hytalerpg.progress;

import java.math.BigDecimal;
import java.util.Objects;

/** R199 schema-12 balance retained across older Hywind reward/loadout writes. */
public record GoldBalance(long gold, BigDecimal remainder) {
    public static final GoldBalance INITIAL = new GoldBalance(0, BigDecimal.ZERO);

    public GoldBalance {
        Objects.requireNonNull(remainder);
        remainder = remainder.stripTrailingZeros();
        if (gold < 0 || remainder.signum() < 0 || remainder.compareTo(BigDecimal.ONE) >= 0)
            throw new IllegalArgumentException("INVALID_GOLD_BALANCE");
    }
}
