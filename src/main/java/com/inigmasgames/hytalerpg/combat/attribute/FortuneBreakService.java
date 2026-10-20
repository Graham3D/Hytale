package com.inigmasgames.hytalerpg.combat.attribute;

import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import java.util.Map;

/** Allocation-only Fortune contract. Equipment, effective attributes and unspent points are not inputs. */
public final class FortuneBreakService {
    private final CombatBalanceProfile.FortuneBreak balance;
    public FortuneBreakService(CombatBalanceProfile profile) {
        balance = java.util.Objects.requireNonNull(profile.fortuneBreak);
        balance.validate();
    }
    public record View(double progressLuck, boolean pureAllocation, boolean active, double fortune,
                       double statusEscapeChance) { }

    public View allocated(long luck, long other) {
        if (luck < 0 || other < 0) throw new IllegalArgumentException("Negative allocation");
        double progress = balance.startingAllocationAttribute + (double) luck;
        boolean pure = other == 0, active = pure && progress >= balance.qualificationProgressLuck;
        double x = Math.clamp((progress - balance.scalarAnchor) / balance.scalarSpan, 0, 1);
        double fortune = active ? Math.pow(x, balance.exponent) : 0;
        return new View(progress, pure, active, fortune, balance.statusEscapeChance * fortune);
    }

    /** Saved raw attributes contain the starting value plus manual allocation, before external modifiers. */
    public View savedAllocation(Map<String, Integer> attributes) {
        long other = 0, luck = 0;
        for (RpgAttribute attribute : RpgAttribute.values()) {
            // Development fixtures may set below-base raw values; these are zero spent points.
            long spent = Math.max(0L, (long) attributes.getOrDefault(attribute.name(), balance.startingAllocationAttribute)
                    - balance.startingAllocationAttribute);
            if (attribute == RpgAttribute.LUCK) luck = spent; else other += spent;
        }
        return allocated(luck, other);
    }
}
