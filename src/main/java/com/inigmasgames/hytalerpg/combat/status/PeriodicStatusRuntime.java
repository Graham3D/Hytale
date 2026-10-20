package com.inigmasgames.hytalerpg.combat.status;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Source-owned Burn/Poison timing and stack accounting. The port still owns all damage calculation/native filtering.
 * Accrual is integrated before source changes; ticks remain one second with a proportional final remainder. */
public final class PeriodicStatusRuntime<C, T> {
    public enum Kind { BURN, POISON, BLEED }
    public record Source(UUID owner, String skill, UUID victim, Kind kind) {
        public Source {
            if (owner == null || victim == null || skill == null || skill.isBlank() || kind == null)
                throw new IllegalArgumentException("Invalid periodic source");
        }
        String stableKey() { return owner + "/" + skill; }
    }
    public record View(int stacks, double remainingSeconds) { }
    public record PackageView(int stacks,int sourceCap,double coefficientPerSecond,double remainingSeconds){}
    public record DeathPackage<C>(Source source,C context,int stacks,int sourceCap,double coefficient,double strength,double remaining){}
    /** Claim before callbacks: dead victims cannot receive pending ticks or replay status propagation. */
    public synchronized List<DeathPackage<C>> takeForDeath(UUID victim,double now){
        if(victim==null||!Double.isFinite(now))throw new IllegalArgumentException("Invalid periodic death observation");
        var result=new ArrayList<DeathPackage<C>>();
        for(var entry:new ArrayList<>(packages.entrySet()))if(entry.getKey().victim.equals(victim)){
            var value=entry.getValue();packages.remove(entry.getKey());
            if(value.endsAt>now)result.add(new DeathPackage<>(entry.getKey(),value.context,value.stacks,value.sourceCap,value.coefficient,value.strength,value.endsAt-now));
        }
        return List.copyOf(result);
    }
    public synchronized java.util.Optional<PackageView> sourceView(Source source,double now){
        var value=packages.get(source);
        return value==null||value.endsAt<=now?java.util.Optional.empty():java.util.Optional.of(
                new PackageView(value.stacks,value.sourceCap,value.coefficient,value.endsAt-now));
    }
    public interface Port<C, T> {
        /** Returning false terminates this source (dead/friendly/protected/native rejection). */
        boolean tick(Source source, C context, T target, int tickIndex, double coefficient, double seconds);
        void changed(Source source, T target, View aggregate);
        default void terminated(Source source, C context, T target, String reason) { }
    }
    private static final int OWNER_CAP = 256, GLOBAL_CAP = 4096, MAX_ACCRUAL_SEGMENTS = 32;
    private final Map<Source, Package<C,T>> packages = new HashMap<>();

    public synchronized String admission(Source source) {
        if (packages.containsKey(source)) return "PASS";
        if (packages.size() >= GLOBAL_CAP) return "GLOBAL_PERIODIC_SOURCE_BUDGET";
        return packages.keySet().stream().filter(s -> s.owner.equals(source.owner)).count() >= OWNER_CAP
                ? "OWNER_PERIODIC_SOURCE_BUDGET" : "PASS";
    }
    /** Strength ranks immutable resolved offensive snapshots, not victim-mitigated damage. */
    public synchronized String apply(Source source, C context, T target, double coefficientPerSecond,
            double strength, double duration, int addedStacks, int sourceCap, double now, Port<C,T> port) {
        if (context == null || target == null || !finitePositive(coefficientPerSecond) || !finitePositive(strength)
                || !finitePositive(duration) || duration > 120 || !Double.isFinite(now) || addedStacks < 1
                || sourceCap < 1 || sourceCap > 12) throw new IllegalArgumentException("Invalid periodic application");
        if (source.kind != Kind.POISON) { addedStacks = 1; sourceCap = 1; }
        // A source's world tick need not precede another caster's application. Settle
        // all competing packages before ranking; expired poison cannot evict a live
        // package and stack changes cannot push accrual across an undelivered tick.
        var competing = new ArrayList<>(packages.entrySet()).stream()
                .filter(e -> e.getKey().equals(source) || source.kind == Kind.POISON
                        && e.getKey().kind == Kind.POISON && e.getKey().victim.equals(source.victim)).toList();
        for (var entry : competing) now = Math.max(now, entry.getValue().accountedAt);
        for (var entry : competing)
            if (packages.get(entry.getKey()) == entry.getValue()) advance(entry.getKey(), entry.getValue(), now, port);
        String admission = admission(source); if (!admission.equals("PASS")) return admission;
        var previous = packages.get(source);
        Package<C,T> value;
        if (previous == null) {
            value = new Package<>(context, target, coefficientPerSecond, strength, Math.min(addedStacks, sourceCap), sourceCap, now, now + duration);
            packages.put(source, value);
        } else {
            value = previous;
            accrue(value, now);
            if (strength > value.strength) {
                value.context = context; value.coefficient = coefficientPerSecond; value.strength = strength; value.sourceCap=sourceCap;
            }
            // Cap belongs to the retained offensive snapshot. A weaker unlinked refresh must
            // not expand a concentrated 1.75x package to three empowered stacks.
            value.stacks = Math.min(value.sourceCap, value.stacks + addedStacks);
            value.endsAt = now + duration;
        }
        if (source.kind == Kind.POISON) enforcePoisonCap(source.victim, now, port);
        port.changed(source, target, view(source.victim, source.kind, now));
        return packages.containsKey(source) ? previous == null ? "APPLIED" : "REFRESHED" : "WEAKER_POISON_PACKAGE_REJECTED";
    }
    public synchronized void tick(UUID owner, double now, Port<C,T> port) {
        if (!Double.isFinite(now)) throw new IllegalArgumentException("Invalid periodic clock");
        for (var entry : new ArrayList<>(packages.entrySet()))
            if (entry.getKey().owner.equals(owner) && packages.get(entry.getKey()) == entry.getValue())
                advance(entry.getKey(), entry.getValue(), now, port);
    }
    private void advance(Source source, Package<C,T> value, double now, Port<C,T> port) {
        if (now < value.accountedAt) return;
        if (now - value.accountedAt > 2) {
            // Do not deliver unbounded catch-up damage against positions/protection we did not observe.
            finish(source, value, now, "SIMULATION_GAP_EXCEEDS_TWO_SECONDS", port); return;
        }
        while (now >= Math.min(value.nextTick, value.endsAt) - 1e-9) {
            double at = Math.min(value.nextTick, value.endsAt); accrue(value, at); value.tickIndex++;
            var pending = List.copyOf(value.accrued);
            value.accrued.clear(); value.nextTick += 1;
            try {
                for (var part : pending) {
                    if (!port.tick(source, part.context, value.target, value.tickIndex, part.coefficient, part.seconds)) {
                        finish(source, value, now, "NATIVE_TARGET_OR_DAMAGE_REJECTED", port); return;
                    }
                }
            } catch (RuntimeException error) {
                finish(source, value, now, "NATIVE_PERIODIC_FAILURE_" + error.getClass().getSimpleName(), port); throw error;
            }
            if (at >= value.endsAt - 1e-9) {
                finish(source, value, now, "PERIODIC_EXPIRED", port); return;
            }
        }
        accrue(value, now);
    }
    private void accrue(Package<C,T> value, double now) {
        double delta = Math.min(now, value.endsAt) - value.accountedAt;
        if (delta <= 1e-12) return;
        double coefficient = delta * value.coefficient * value.stacks;
        var last = value.accrued.isEmpty() ? null : value.accrued.getLast();
        if (last != null && last.context == value.context) {
            value.accrued.set(value.accrued.size() - 1, new Part<>(last.context, last.coefficient + coefficient, last.seconds + delta));
        } else {
            if (value.accrued.size() >= MAX_ACCRUAL_SEGMENTS) throw new IllegalStateException("PERIODIC_ACCRUAL_SEGMENT_BUDGET");
            value.accrued.add(new Part<>(value.context, coefficient, delta));
        }
        value.accountedAt = Math.min(now, value.endsAt);
    }
    private void enforcePoisonCap(UUID victim, double now, Port<C,T> port) {
        var sources = packages.entrySet().stream().filter(e -> e.getKey().victim.equals(victim) && e.getKey().kind == Kind.POISON)
                .sorted(Comparator.<Map.Entry<Source,Package<C,T>>>comparingDouble(e -> e.getValue().strength).reversed()
                        .thenComparing(e -> e.getKey().stableKey())).toList();
        int available = 12;
        for (var entry : sources) {
            var value = entry.getValue(); accrue(value, now);
            int retained = Math.min(available, value.stacks); available -= retained;
            if (retained == 0) {
                // Already-earned fractional damage belongs to the evicted snapshot.
                // Remove it before callback so a failed native call cannot replay it.
                var pending = List.copyOf(value.accrued); value.accrued.clear(); value.tickIndex++;
                try {
                    for (var part : pending)
                        if (!port.tick(entry.getKey(), part.context, value.target, value.tickIndex, part.coefficient, part.seconds)) break;
                } finally {
                    finish(entry.getKey(), value, now, "STRONGER_POISON_PACKAGE_REPLACEMENT", port);
                }
            }
            else value.stacks = retained;
        }
    }
    public synchronized View view(UUID victim, Kind kind, double now) {
        int stacks = 0; double remaining = 0;
        for (var entry : packages.entrySet()) if (entry.getKey().victim.equals(victim) && entry.getKey().kind == kind && entry.getValue().endsAt > now) {
            stacks += entry.getValue().stacks; remaining = Math.max(remaining, entry.getValue().endsAt - now);
        }
        return new View(stacks, remaining);
    }
    public synchronized void cancel(UUID owner, double now, Port<C,T> port) {
        for (var entry : new ArrayList<>(packages.entrySet())) if (entry.getKey().owner.equals(owner)) {
            finish(entry.getKey(), entry.getValue(), now, "OWNER_CANCELLED", port);
        }
    }
    private void finish(Source source, Package<C,T> value, double now, String reason, Port<C,T> port) {
        if (!packages.remove(source, value)) return;
        port.terminated(source, value.context, value.target, reason);
        port.changed(source, value.target, view(source.victim, source.kind, now));
    }
    public synchronized int size() { return packages.size(); }
    private static boolean finitePositive(double value) { return Double.isFinite(value) && value > 0; }
    private record Part<C>(C context, double coefficient, double seconds) { }
    private static final class Package<C,T> {
        C context; final T target; double coefficient, strength; int stacks,sourceCap;
        double accountedAt, nextTick, endsAt; int tickIndex; final List<Part<C>> accrued = new ArrayList<>();
        Package(C context, T target, double coefficient, double strength, int stacks, int sourceCap, double now, double end) {
            this.context=context; this.target=target; this.coefficient=coefficient; this.strength=strength;
            this.stacks=stacks; this.sourceCap=sourceCap; accountedAt=now; nextTick=now+1; endsAt=end;
        }
    }
}
