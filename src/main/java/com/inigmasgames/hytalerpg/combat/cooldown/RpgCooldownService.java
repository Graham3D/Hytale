package com.inigmasgames.hytalerpg.combat.cooldown;

import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Runtime-only cooldown state. Recovery is a work-rate divisor, never a silent duration subtraction. */
public final class RpgCooldownService {
    private final CombatBalanceProfile profile;
    private final LongSupplier nanoTime;
    private final Map<Key, Long> endsAtNanos = new HashMap<>();
    public RpgCooldownService(CombatBalanceProfile profile, LongSupplier nanoTime) {
        this.profile = profile; this.nanoTime = nanoTime;
    }
    public synchronized boolean canActivate(UUID actor, String skillId) { return remaining(actor, skillId) <= 0.0; }
    public synchronized Calculation startCooldown(UUID actor, String skillId, double baseSeconds,
                                                  double durationFactor, double wisdomRecovery,
                                                  CompiledSkillPlan.KernelModifiers modifiers) {
        if (!canActivate(actor, skillId)) throw new IllegalStateException("Skill is already on cooldown");
        Calculation calculation = calculate(baseSeconds, durationFactor, wisdomRecovery, modifiers);
        endsAtNanos.put(new Key(actor, skillId), nanoTime.getAsLong() + Math.round(calculation.finalSeconds * 1_000_000_000.0));
        return calculation;
    }
    public Calculation calculate(double baseSeconds, double durationFactor, double wisdomRecovery,
                                 CompiledSkillPlan.KernelModifiers modifiers) {
        if (baseSeconds < 0.0 || durationFactor < 0.0) throw new IllegalArgumentException("Cooldown values cannot be negative");
        double passiveRecovery = modifiers == null ? 0.0 : modifiers.cooldownRecoveryBonus();
        double totalRecovery = clamp(wisdomRecovery + passiveRecovery, 0.0, profile.cooldownRecoveryCap);
        double seconds = Math.max(profile.minimumCooldownSeconds, baseSeconds * durationFactor / (1.0 + totalRecovery));
        return new Calculation(baseSeconds, durationFactor, wisdomRecovery, passiveRecovery, totalRecovery, seconds);
    }
    public synchronized double remaining(UUID actor, String skillId) {
        Key key = new Key(actor, skillId);
        Long end = endsAtNanos.get(key);
        if (end == null) return 0.0;
        long remaining = end - nanoTime.getAsLong();
        if (remaining <= 0) { endsAtNanos.remove(key); return 0.0; }
        return remaining / 1_000_000_000.0;
    }
    public synchronized boolean clear(UUID actor, String skillId) { return endsAtNanos.remove(new Key(actor, skillId)) != null; }
    public synchronized void clear(UUID actor) { endsAtNanos.keySet().removeIf(key -> key.actor.equals(actor)); }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private record Key(UUID actor, String skill) { }
    public record Calculation(double baseSeconds, double durationFactor, double wisdomRecovery,
                              double passiveRecovery, double appliedRecovery, double finalSeconds) { }
}
