package com.inigmasgames.hytalerpg.execution.summon;

import com.inigmasgames.hytalerpg.combat.power.BasePowerSource;
import com.inigmasgames.hytalerpg.combat.snapshot.CombatSnapshot;

/** Canonical offensive snapshot and base payload for Summon Skeleton Archers. */
public final class SummonArrowDamage {
    public static final double BASE_COEFFICIENT = 0.35;

    private SummonArrowDamage() { }

    /** Captured once when the summon lease is created; later equipment/stat changes cannot alter it. */
    public static double snapshotMagicPower(CombatSnapshot snapshot) {
        if (snapshot == null || snapshot.basePowerSource() != BasePowerSource.MAGIC_WEAPON)
            throw new IllegalArgumentException("SUMMON_ARROW_MAGIC_POWER_SOURCE_INVALID");
        double resolved = snapshot.basePower() * snapshot.derivedStats().magicDamageMultiplier();
        if (!Double.isFinite(resolved) || resolved <= 0.0)
            throw new IllegalArgumentException("SUMMON_ARROW_MAGIC_POWER_SNAPSHOT_INVALID");
        return resolved;
    }

    public static double basePhysicalDamage(double snapshottedMagicPower) {
        if (!Double.isFinite(snapshottedMagicPower) || snapshottedMagicPower <= 0.0)
            throw new IllegalArgumentException("SUMMON_ARROW_MAGIC_POWER_SNAPSHOT_INVALID");
        return snapshottedMagicPower * BASE_COEFFICIENT;
    }

    public static double passiveMagnitudeFactor(double resolvedCoefficient) {
        if (!Double.isFinite(resolvedCoefficient) || resolvedCoefficient <= 0.0)
            throw new IllegalArgumentException("SUMMON_ARROW_COEFFICIENT_INVALID");
        return resolvedCoefficient / BASE_COEFFICIENT;
    }
}
