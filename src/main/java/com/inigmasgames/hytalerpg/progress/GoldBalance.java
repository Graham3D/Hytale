package com.inigmasgames.hytalerpg.progress;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Owner Gold and its subunit carry, committed with the earned reward checkpoint. */
public record GoldBalance(long gold, BigDecimal remainder) {
    public static final GoldBalance INITIAL = new GoldBalance(0, BigDecimal.ZERO);
    public GoldBalance {
        Objects.requireNonNull(remainder);
        remainder = remainder.stripTrailingZeros();
        if (gold < 0 || remainder.signum() < 0 || remainder.compareTo(BigDecimal.ONE) >= 0)
            throw new IllegalArgumentException("INVALID_GOLD_BALANCE");
    }
    public GoldBalance award(EarnedReward.GoldPot pot) {
        if (pot == null) return this;
        pot=new EarnedReward.GoldPot(pot.baseGold(),pot.findPercent(),pot.contributionFraction());
        BigDecimal total = pot.baseShare()
                .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(pot.findPercent()).movePointLeft(2)))
                .add(remainder);
        long whole = total.setScale(0, RoundingMode.DOWN).longValueExact();
        return new GoldBalance(Math.addExact(gold, whole), total.subtract(BigDecimal.valueOf(whole)));
    }
}
