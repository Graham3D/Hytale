package com.inigmasgames.hytalerpg.combat.status;

import java.util.Objects;
import java.util.UUID;

/** One server-owned status opportunity, before the underlying status/package mutates. */
public record StatusApplication(UUID source, UUID target, String status, double combinedSourceChance,
                                double sourcePenetration, boolean hostile, boolean scripted,
                                boolean noFortuneEscape, boolean noProc) {
    public StatusApplication {
        Objects.requireNonNull(source); Objects.requireNonNull(target);
        if (status == null || status.isBlank() || !Double.isFinite(combinedSourceChance)
                || combinedSourceChance < 0 || combinedSourceChance > 1
                || !Double.isFinite(sourcePenetration) || sourcePenetration < 0)
            throw new IllegalArgumentException("Invalid status opportunity");
    }
    public static StatusApplication hostile(UUID source, UUID target, RpgStatusType status) {
        return new StatusApplication(source, target, status.name(), 1, 0, true, false, false, false);
    }
    public boolean fortuneEligible() {
        return hostile && !source.equals(target) && !scripted && !noFortuneEscape && !status.equals("TAUNT");
    }
    /** Source merge occurs before the single shared resistance roll, never after package acceptance. */
    public static double merge(double existing, double cardChance, double coefficient) {
        for (double value : new double[]{existing, cardChance, coefficient})
            if (!Double.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("Chance must be 0..1");
        return 1 - (1 - existing) * (1 - cardChance * coefficient);
    }
}
