package com.inigmasgames.hytalerpg.combat.damage;

import java.util.Objects;
import java.util.Random;
import java.util.function.DoubleSupplier;

public final class CriticalRoller {
    private final DoubleSupplier random;
    public CriticalRoller(DoubleSupplier random) { this.random = Objects.requireNonNull(random); }
    public static CriticalRoller seeded(long seed) { Random random = new Random(seed); return new CriticalRoller(random::nextDouble); }
    public boolean roll(double chance, boolean canCrit) {
        if (!Double.isFinite(chance) || chance < 0.0 || chance > 1.0) throw new IllegalArgumentException("Crit chance must be 0..1");
        return canCrit && random.getAsDouble() < chance;
    }
}
