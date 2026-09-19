package com.inigmasgames.hytalerpg.execution.lightning;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Bounded, native-object-free authority for Lightning-specific cadence and shared state.
 * World/ECS mutation remains in the Hytale adapter; this owner only keeps immutable IDs,
 * monotonic deadlines and finite numeric ledgers.
 */
public final class LightningRuntime {
    public static final double ELECTRIFIED_SECONDS = 6.0;
    public static final double VULNERABILITY_SECONDS = 5.0;
    public static final double ALACRITY_SECONDS = 6.0;
    private static final int MAX_ENTRIES = 4096;

    private record RootTarget(String root, UUID target) {}
    private record OwnerTarget(UUID owner, UUID target) {}
    private record FieldTarget(String field, UUID target) {}
    private final Map<RootTarget, Boolean> chargedBoltStacks = new HashMap<>();
    private final Map<OwnerTarget, Double> ballLocks = new HashMap<>();
    private final Map<OwnerTarget, Double> stormTargetLocks = new HashMap<>();
    private final Map<UUID, Double> stormGlobalLocks = new HashMap<>();
    private final Map<FieldTarget, Exposure> exposures = new HashMap<>();
    private final Map<String, Coil> coils = new HashMap<>();
    private final Map<UUID, Mantle> mantles = new HashMap<>();

    public synchronized String chargedBolt(String root, UUID target, double roll) {
        requireProbability(roll);
        var key = new RootTarget(requireId(root), Objects.requireNonNull(target));
        if (chargedBoltStacks.containsKey(key)) return "ROOT_TARGET_STACK_CAP";
        if (roll >= .35) return "CHANCE_MISS";
        bounded(chargedBoltStacks.size());
        chargedBoltStacks.put(key, Boolean.TRUE);
        return "APPLY";
    }

    public synchronized String ballLightning(UUID owner, UUID target, double now, double roll) {
        requireTime(now); requireProbability(roll);
        var key = new OwnerTarget(Objects.requireNonNull(owner), Objects.requireNonNull(target));
        expire(ballLocks, now);
        if (ballLocks.getOrDefault(key, 0d) > now) return "TARGET_LOCK";
        if (roll >= .50) return "CHANCE_MISS";
        bounded(ballLocks.size());
        ballLocks.put(key, now + 1.5);
        return "APPLY";
    }

    /** Zero-to-one transition only; descendants and Storm itself are explicitly ineligible. */
    public synchronized String stormStrike(UUID owner, UUID target, boolean zeroToOne,
                                           boolean descendant, boolean stormSource, double now) {
        requireTime(now); Objects.requireNonNull(owner); Objects.requireNonNull(target);
        expire(stormTargetLocks, now); expire(stormGlobalLocks, now);
        if (!zeroToOne) return "NOT_ZERO_TO_ONE";
        if (descendant || stormSource) return "RECURSIVE_SOURCE";
        if (stormGlobalLocks.getOrDefault(owner, 0d) > now) return "GLOBAL_ICD";
        var key = new OwnerTarget(owner, target);
        if (stormTargetLocks.getOrDefault(key, 0d) > now) return "TARGET_ICD";
        bounded(stormTargetLocks.size() + stormGlobalLocks.size());
        stormTargetLocks.put(key, now + 6.0);
        stormGlobalLocks.put(owner, now + 1.0);
        return "TRIGGER";
    }

    public synchronized ExposureResult expose(String field, UUID target, boolean inside, double elapsedSeconds, double now) {
        requireTime(now);
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0) throw new IllegalArgumentException("INVALID_EXPOSURE_DELTA");
        var key = new FieldTarget(requireId(field), Objects.requireNonNull(target));
        if (!inside) { exposures.remove(key); return new ExposureResult("RESET", 0, false); }
        var old = exposures.getOrDefault(key, new Exposure(0, Double.NEGATIVE_INFINITY,now));
        if(now-old.lastObserved>1.10)old=new Exposure(0,Double.NEGATIVE_INFINITY,now);
        double total = Math.min(8, old.seconds + elapsedSeconds);
        boolean refresh = total >= 3 && now + 1e-9 >= old.nextRefresh;
        bounded(exposures.size());
        exposures.put(key, new Exposure(total, refresh ? now + 1 : old.nextRefresh,now));
        return new ExposureResult(refresh ? "APPLY_OR_REFRESH" : "ACCUMULATING", total, refresh);
    }

    public synchronized CoilResult placeCoil(String instance, UUID owner, double magicPower, double now) {
        requireTime(now);
        if (!Double.isFinite(magicPower) || magicPower <= 0) throw new IllegalArgumentException("INVALID_COIL_MAGIC_POWER");
        bounded(coils.size());
        var coil = new Coil(Objects.requireNonNull(owner), magicPower * 3, 0, now + 8, false);
        if (coils.putIfAbsent(requireId(instance), coil) != null) throw new IllegalStateException("COIL_ALREADY_EXISTS");
        return coil.result("PLACED");
    }

    public synchronized CoilResult chargeCoil(String instance, UUID contributor, double physicalDamage,
                                               boolean directPlayerWeaponRoot, double now) {
        requireTime(now); Objects.requireNonNull(contributor);
        if (!Double.isFinite(physicalDamage) || physicalDamage < 0) throw new IllegalArgumentException("INVALID_COIL_DAMAGE");
        var coil = coils.get(requireId(instance));
        if (coil == null) return new CoilResult("MISSING", 0, 0, false);
        if (now >= coil.expires) { coils.remove(instance); return coil.result("EXPIRED"); }
        if (!directPlayerWeaponRoot) return coil.result("INELIGIBLE_SOURCE");
        if (coil.discharged) return coil.result("ALREADY_DISCHARGED");
        double charge = Math.min(coil.capacity, coil.charge + physicalDamage);
        var next = new Coil(coil.owner, coil.capacity, charge, coil.expires, charge + 1e-9 >= coil.capacity);
        coils.put(instance, next);
        return next.result(next.discharged ? "DISCHARGE" : "CHARGED");
    }

    public synchronized String expireCoil(String instance, double now) {
        requireTime(now); var coil = coils.get(requireId(instance));
        if (coil == null) return "MISSING";
        if (now < coil.expires && !coil.discharged) return "ACTIVE";
        coils.remove(instance); return coil.discharged ? "DISCHARGED_CLEANUP" : "EXPIRED";
    }

    public synchronized void activateMantle(UUID owner, double now) {
        requireTime(now); Objects.requireNonNull(owner); bounded(mantles.size());
        mantles.put(owner, new Mantle(0, 0));
    }

    public synchronized MantleResult mantleDamage(UUID owner, double actualHealthLoss, double snapshottedMagicPower,
                                                  boolean eligible, double now) {
        requireTime(now);
        if (!Double.isFinite(actualHealthLoss) || actualHealthLoss < 0 || !Double.isFinite(snapshottedMagicPower) || snapshottedMagicPower <= 0)
            throw new IllegalArgumentException("INVALID_MANTLE_DAMAGE");
        var mantle = mantles.get(Objects.requireNonNull(owner));
        if (mantle == null) return new MantleResult("INACTIVE", 0, 0);
        if (now < mantle.alacrityEnds) return new MantleResult("ALACRITY_ACTIVE", 0, mantle.alacrityEnds - now);
        if (!eligible || actualHealthLoss <= 0) return new MantleResult("INELIGIBLE", mantle.charge, 0);
        double gained = Math.min(10, 100 * actualHealthLoss / Math.max(1, 8 * snapshottedMagicPower));
        double charge = Math.min(100, mantle.charge + gained);
        if (charge + 1e-9 >= 100) {
            mantles.put(owner, new Mantle(0, now + ALACRITY_SECONDS));
            return new MantleResult("ALACRITY_TRIGGERED", gained, ALACRITY_SECONDS);
        }
        mantles.put(owner, new Mantle(charge, 0));
        return new MantleResult("CHARGED", gained, charge);
    }

    public synchronized void forgetRoot(String root) { chargedBoltStacks.keySet().removeIf(k -> k.root.equals(root)); }
    public synchronized void forgetField(String field) { exposures.keySet().removeIf(k -> k.field.equals(field)); }
    public synchronized void forget(UUID owner) {
        ballLocks.keySet().removeIf(k -> k.owner.equals(owner) || k.target.equals(owner));
        stormTargetLocks.keySet().removeIf(k -> k.owner.equals(owner) || k.target.equals(owner));
        stormGlobalLocks.remove(owner); mantles.remove(owner);
        coils.entrySet().removeIf(e -> e.getValue().owner.equals(owner));
        exposures.keySet().removeIf(k -> k.target.equals(owner));
        chargedBoltStacks.keySet().removeIf(k -> k.target.equals(owner));
    }

    private static <K> void expire(Map<K, Double> values, double now) { values.values().removeIf(end -> end <= now); }
    private static void requireTime(double value) { if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("INVALID_TIME"); }
    private static void requireProbability(double value) { if (!Double.isFinite(value) || value < 0 || value >= 1) throw new IllegalArgumentException("INVALID_ROLL"); }
    private static String requireId(String value) { if (value == null || value.isBlank() || value.length() > 256) throw new IllegalArgumentException("INVALID_ID"); return value; }
    private static void bounded(int size) { if (size >= MAX_ENTRIES) throw new IllegalStateException("LIGHTNING_LEDGER_BUDGET"); }

    private record Exposure(double seconds, double nextRefresh,double lastObserved) {}
    private record Coil(UUID owner, double capacity, double charge, double expires, boolean discharged) {
        CoilResult result(String code) { return new CoilResult(code, charge, capacity, discharged); }
    }
    private record Mantle(double charge, double alacrityEnds) {}
    public record ExposureResult(String code, double exposureSeconds, boolean applyVulnerability) {}
    public record CoilResult(String code, double charge, double capacity, boolean discharge) {}
    public record MantleResult(String code, double amount, double state) {}
}
