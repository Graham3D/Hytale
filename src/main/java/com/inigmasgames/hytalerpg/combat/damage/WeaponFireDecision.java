package com.inigmasgames.hytalerpg.combat.damage;

import com.inigmasgames.hytalerpg.combat.resource.*;
import java.util.*;
import java.util.function.Supplier;

/** One decision object owned by one authored execution (never a per-victim or evicting time cache).
 * Reuses the existing resource transaction owner. Nothing is wired into native combat until a
 * producer can supply an authoritative WeaponDamageExecution and own this object's lifetime. */
public final class WeaponFireDecision {
    public enum Status { PASS_THROUGH, ECHO_COMMITTED, INSUFFICIENT_MANA }
    public record Recipient(UUID targetId, double generatedFire) {
        public Recipient {
            Objects.requireNonNull(targetId);
            if (!Double.isFinite(generatedFire) || generatedFire <= 0 || generatedFire > Float.MAX_VALUE)
                throw new IllegalArgumentException("INVALID_GENERATED_FIRE");
        }
    }
    public record Result(Status status, double sourceFire, double generatedFire, double manaCost,
            List<Recipient> recipients, UUID resourceReceipt) {
        public Result { recipients = List.copyOf(recipients); }
    }
    private final WeaponDamageExecution execution;
    private Result result;
    private boolean evaluating, failed, pulseClaimed, closed;

    public WeaponFireDecision(WeaponDamageExecution execution) { this.execution = Objects.requireNonNull(execution); }
    public WeaponDamageExecution execution() { return execution; }
    public synchronized boolean resolved(){return result!=null;}
    /** Explicit proportional-profile formula. Flat/nonlinear profiles must use eventCost instead. */
    public static double proportionalEventCost(double maximum,double sumMagnitudeFactors,double resourceFactor){
        if(!Double.isFinite(maximum)||maximum<=0||!Double.isFinite(sumMagnitudeFactors)||sumMagnitudeFactors<0
                ||!Double.isFinite(resourceFactor)||resourceFactor<0)throw new IllegalArgumentException("INVALID_MANTLE_COST_INPUT");
        double cost=maximum*.0075*sumMagnitudeFactors*resourceFactor;
        if(!Double.isFinite(cost))throw new IllegalArgumentException("MANTLE_COST_OVERFLOW");return cost;
    }

    /** The query must preflight the complete accepted set and existing spatial/derived budgets.
     * Amounts already use the compiled Mantle modifier buckets, but exclude mitigation/HP/overkill.
     * No per-target callbacks run inside resource mutation; no ECS/persistence work is queued here. */
    public synchronized Result decide(boolean active, Supplier<List<Recipient>> preflight,
            double compiledCostFactor, RpgResourceService resources, NativeResourcePort port, Runnable deactivate) {
        return decide(active,preflight,compiledCostFactor,resources,port,deactivate,false);
    }
    public synchronized Result decideProportional(boolean active,Supplier<List<Recipient>> preflight,
            double compiledCostFactor,RpgResourceService resources,NativeResourcePort port,Runnable deactivate){
        return decide(active,preflight,compiledCostFactor,resources,port,deactivate,true);
    }
    private Result decide(boolean active,Supplier<List<Recipient>> preflight,double compiledCostFactor,
            RpgResourceService resources,NativeResourcePort port,Runnable deactivate,boolean proportional){
        requireOpen();
        if (failed) throw new IllegalStateException("WEAPON_FIRE_DECISION_UNCERTAIN_OR_REENTRANT");
        if (result != null) return result;
        if (evaluating || failed) throw new IllegalStateException("WEAPON_FIRE_DECISION_UNCERTAIN_OR_REENTRANT");
        evaluating = true;
        try {
            double source = execution.sourceFire();
            if (!active || source <= 0) return result = new Result(Status.PASS_THROUGH, source, 0, 0, List.of(), null);
            if (!Double.isFinite(compiledCostFactor) || compiledCostFactor < 0)
                throw new IllegalArgumentException("INVALID_EVENT_COST_FACTOR");
            var targets = List.copyOf(preflight.get());
            if (targets.size() > 64) throw new IllegalStateException("AURA_TARGET_BUDGET");
            var seen = new HashSet<UUID>();
            double generated = 0;
            for (var target : targets) {
                if (target.targetId().equals(execution.identity().actorId()) || !seen.add(target.targetId()))
                    throw new IllegalArgumentException("INVALID_OR_DUPLICATE_MANTLE_RECIPIENT");
                generated += target.generatedFire();
            }
            double max = port.maximum(ResourceType.MANA);
            double cost = proportional ? proportionalEventCost(max,targets.stream().mapToDouble(r->r.generatedFire()/(source*.25)).sum(),compiledCostFactor)
                    : eventCost(max, source, generated, compiledCostFactor);
            if (cost == 0) return result = new Result(Status.ECHO_COMMITTED, source, generated, 0, targets, null);
            var declared = new ResourceCost(ResourceType.MANA, cost);
            // A reservation narrows current spendability, not the maximum used in eventCost.
            if (!resources.canAfford(execution.identity().actorId(), declared, port)) {
                result = new Result(Status.INSUFFICIENT_MANA, source, 0, 0, List.of(), null);
                deactivate.run();
                return result;
            }
            var token = resources.reserveCost(execution.identity().actorId(), declared, port);
            try {
                if (!resources.commitCost(token, port)) throw new IllegalStateException("MANTLE_RESOURCE_RECEIPT_REUSED");
                result = new Result(Status.ECHO_COMMITTED, source, generated, cost, targets, token.tokenId());
                return result;
            } finally { resources.finish(token); }
        } catch (RuntimeException failure) {
            // A throwing native writer may already have mutated a resource. Never retry a debit,
            // dispatch, or fabricate a successful echo decision after such uncertainty.
            failed = true;
            throw failure;
        } finally { evaluating = false; }
    }
    public static double eventCost(double totalMaximumMana, double source, double generated, double factor) {
        if (!Double.isFinite(totalMaximumMana) || totalMaximumMana <= 0 || !Double.isFinite(source) || source <= 0
                || !Double.isFinite(generated) || generated < 0 || !Double.isFinite(factor) || factor < 0)
            throw new IllegalArgumentException("INVALID_MANTLE_COST_INPUT");
        double cost = totalMaximumMana * .03 * (generated / source) * factor;
        if (!Double.isFinite(cost)) throw new IllegalArgumentException("MANTLE_COST_OVERFLOW");
        return cost;
    }
    /** Mantle never owns direct damage, even before its decision or after an echo failure. */
    public synchronized double directAmount(String componentId, double victimAmount) {
        if (!Double.isFinite(victimAmount) || victimAmount < 0 || victimAmount > Float.MAX_VALUE)
            throw new IllegalArgumentException("INVALID_DIRECT_AMOUNT");
        execution.component(componentId); // Retain component/provenance validation, never suppression.
        return victimAmount;
    }
    /** Claim before dispatch. Failure or late target invalidation cannot replay a pulse or refund it. */
    public synchronized List<Recipient> claimPulse() {
        requireResolved();
        if (pulseClaimed || result.status() != Status.ECHO_COMMITTED) return List.of();
        pulseClaimed = true;
        return result.recipients();
    }
    public synchronized void close() { closed = true; }
    private void requireOpen() { if (closed) throw new IllegalStateException("WEAPON_EXECUTION_CLOSED"); }
    private void requireResolved() {
        requireOpen();
        if (result == null || failed || evaluating) throw new IllegalStateException("WEAPON_FIRE_DECISION_NOT_RESOLVED");
    }
}
