package com.inigmasgames.hytalerpg.difficulty;

import java.util.*;

/** Authored elemental contract. Physical defense, status avoidance and control immunity have other owners. */
public record MonsterResistanceProfile(Map<Channel, Double> resistance, Set<Channel> immunities) {
    public enum Channel { FIRE, COLD, LIGHTNING, WIND, VOID, NATURE, NECROTIC, POISON }
    public static final double CAP = .75;
    public static final MonsterResistanceProfile NONE = new MonsterResistanceProfile(Map.of(), Set.of());
    public MonsterResistanceProfile {
        var checked = new EnumMap<Channel, Double>(Channel.class);
        resistance.forEach((channel, value) -> {
            if (value == null || !Double.isFinite(value) || value < 0) throw new IllegalArgumentException("INVALID_RESISTANCE");
            checked.put(Objects.requireNonNull(channel), Math.min(CAP, value));
        });
        resistance = Collections.unmodifiableMap(checked);
        var ordered=EnumSet.noneOf(Channel.class);ordered.addAll(immunities);immunities=Collections.unmodifiableSet(ordered);
    }
    public double effective(Channel channel) { return resistance.getOrDefault(Objects.requireNonNull(channel), 0.0); }
    /** Pure preview/adapter seam, not a second native Health mutation or an automatic damage filter. */
    public Mitigation resolve(Channel channel, double amount) {
        Objects.requireNonNull(channel);
        if (!Double.isFinite(amount) || amount < 0) throw new IllegalArgumentException("INVALID_DAMAGE");
        return immunities.contains(channel) ? new Mitigation(0, "AUTHORED_ELEMENTAL_IMMUNITY")
                : new Mitigation(amount * (1 - effective(channel)), "ORDINARY_RESISTANCE");
    }
    public record Mitigation(double amount, String reason) {}
}
