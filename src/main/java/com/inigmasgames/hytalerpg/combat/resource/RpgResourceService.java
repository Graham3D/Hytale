package com.inigmasgames.hytalerpg.combat.resource;

import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Transaction coordinator over native EntityStatMap values. */
public final class RpgResourceService {
    private final CombatBalanceProfile profile;
    private final ReservationService reservations;
    private final Map<UUID, PendingCost> pending = new HashMap<>();
    private final Set<String> recoveredRootAttacks = new HashSet<>();
    public RpgResourceService(CombatBalanceProfile profile, ReservationService reservations) {
        this.profile = profile; this.reservations = reservations;
    }

    public ResourceCost evaluate(ResourceCost declared, CompiledSkillPlan.KernelModifiers modifiers) {
        return declared.modified(modifiers == null ? 1.0 : modifiers.resourceCostMultiplier());
    }
    public synchronized boolean canAfford(UUID actor, ResourceCost cost, NativeResourcePort resources) {
        if (cost.type() == ResourceType.NONE) return true;
        double held = pending.values().stream().filter(p -> p.actor.equals(actor) && p.cost.type() == cost.type() && !p.committed)
                .mapToDouble(p -> p.cost.amount()).sum();
        return resources.current(cost.type()) + 1.0e-9 >= cost.amount() + held;
    }
    public synchronized CostToken reserveCost(UUID actor, ResourceCost cost, NativeResourcePort resources) {
        if (!canAfford(actor, cost, resources)) throw new IllegalStateException("Insufficient " + cost.type());
        CostToken token = new CostToken(UUID.randomUUID(), actor, cost);
        pending.put(token.tokenId(), new PendingCost(actor, cost, false));
        return token;
    }
    public synchronized boolean commitCost(CostToken token, NativeResourcePort resources) {
        PendingCost hold = require(token);
        if (hold.committed) return false;
        if (hold.cost.type() != ResourceType.NONE) {
            double current = resources.current(hold.cost.type());
            if (current + 1.0e-9 < hold.cost.amount()) throw new IllegalStateException("Native resource changed before commit");
            resources.setCurrent(hold.cost.type(), current - hold.cost.amount());
        }
        pending.put(token.tokenId(), new PendingCost(hold.actor, hold.cost, true));
        return true;
    }
    public synchronized boolean refundIfUncommitted(CostToken token) {
        PendingCost hold = require(token);
        if (hold.committed) return false;
        pending.remove(token.tokenId());
        return true;
    }
    /** Explicit activation-transaction rollback. Never used for gameplay cancellation after dispatch. */
    public synchronized boolean refundCommittedCost(CostToken token, NativeResourcePort resources) {
        PendingCost hold = require(token);
        if (!hold.committed) return false;
        if (hold.cost.type() != ResourceType.NONE)
            addCapped(hold.cost.type(), hold.cost.amount(), resources.maximum(hold.cost.type()), resources);
        pending.remove(token.tokenId());
        return true;
    }
    public synchronized void finish(CostToken token) { pending.remove(token.tokenId()); }

    public double regenerate(UUID actor, ResourceType type, double seconds, NativeResourcePort resources) {
        if (type == ResourceType.NONE || seconds <= 0.0) return 0.0;
        double cap = type == ResourceType.MANA
                ? reservations.spendableMaximum(actor, resources.maximum(type)) : resources.maximum(type);
        return addCapped(type, resources.maximum(type) * profile.passiveRegenerationPerSecond * seconds, cap, resources);
    }
    public synchronized RecoveryResult recoverHostileWeaponHit(UUID actor, String rootAttackId, boolean charged,
                                                                NativeResourcePort resources) {
        String dedup = actor + ":" + rootAttackId;
        if (!recoveredRootAttacks.add(dedup)) return new RecoveryResult(false, 0.0, 0.0);
        double fraction = charged ? profile.chargedHostileHitRecovery : profile.normalHostileHitRecovery;
        double mana = addCapped(ResourceType.MANA, resources.maximum(ResourceType.MANA) * fraction,
                reservations.spendableMaximum(actor, resources.maximum(ResourceType.MANA)), resources);
        double stamina = addCapped(ResourceType.STAMINA, resources.maximum(ResourceType.STAMINA) * fraction,
                resources.maximum(ResourceType.STAMINA), resources);
        return new RecoveryResult(true, mana, stamina);
    }
    public void restoreBed(UUID actor, NativeResourcePort resources) { restoreFull(actor, resources); }
    public void restoreHome(UUID actor, NativeResourcePort resources) { restoreFull(actor, resources); }
    private void restoreFull(UUID actor, NativeResourcePort resources) {
        resources.setCurrent(ResourceType.MANA, reservations.spendableMaximum(actor, resources.maximum(ResourceType.MANA)));
        resources.setCurrent(ResourceType.STAMINA, resources.maximum(ResourceType.STAMINA));
    }
    private static double addCapped(ResourceType type, double amount, double cap, NativeResourcePort resources) {
        double before = resources.current(type);
        double after = Math.min(cap, Math.max(0.0, before + amount));
        resources.setCurrent(type, after);
        return after - before;
    }
    private PendingCost require(CostToken token) {
        PendingCost hold = pending.get(token.tokenId());
        if (hold == null || !hold.actor.equals(token.actor()) || !hold.cost.equals(token.cost()))
            throw new IllegalArgumentException("Unknown or mismatched cost token");
        return hold;
    }
    private record PendingCost(UUID actor, ResourceCost cost, boolean committed) { }
    public record CostToken(UUID tokenId, UUID actor, ResourceCost cost) { }
    public record RecoveryResult(boolean applied, double manaRecovered, double staminaRecovered) { }
}
