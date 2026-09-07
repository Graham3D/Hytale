package com.inigmasgames.hytalerpg.execution.area;

import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** World-thread finite-effect ownership and per-cast ledgers shared by spatial families. */
public final class AreaRuntime {
    public static final int OWNER_CAP = 8, GLOBAL_CAP = 128;
    private final Map<String, Field> fields = new LinkedHashMap<>();

    public synchronized String admission(UUID owner, String skill, boolean trap) {
        if (fields.size() >= GLOBAL_CAP) return "GLOBAL_FIELD_BUDGET";
        long count = fields.values().stream().filter(f -> f.context.request().actorId().equals(owner)).count();
        if (count >= OWNER_CAP) return "OWNER_FIELD_BUDGET";
        if (trap && fields.values().stream().anyMatch(f -> f.context.request().actorId().equals(owner)
                && f.context.profile().skillId().equals(skill))) return "TRAP_ALREADY_DEPLOYED";
        return "PASS";
    }

    /** Placement is already validated; immutable position/direction survive later owner aim changes. */
    public synchronized void start(SkillExecutionContext context, Vec3 point, Vec3 direction, double now,
                      double radiusFactor, AreaWorldPort port) {
        AreaSkillProfile profile = context.profile().area();
        if (profile == null || !Double.isFinite(now) || !Double.isFinite(radiusFactor) || radiusFactor <= 0)
            throw new IllegalArgumentException("Invalid area start");
        String admission = admission(context.request().actorId(), context.profile().skillId(), profile.trap());
        if (!admission.equals("PASS")) throw new IllegalStateException(admission);
        if (fields.containsKey(context.skillInstanceId())) throw new IllegalStateException("DUPLICATE_FIELD_INSTANCE");
        Field field = new Field(context, profile.footprint(point, direction, radiusFactor), now, radiusFactor);
        fields.put(context.skillInstanceId(), field);
        port.trace(context, "AREA_STARTED", Map.of("origin", point.toString(), "radius", field.geometry.radius(),
                "height", field.geometry.height(), "lifetimeSeconds", profile.lifetimeSeconds()));
        try { tickField(field, now, port); }
        catch (RuntimeException error) { finish(field, "NATIVE_ADAPTER_FAILURE_" + error.getClass().getSimpleName(), port); throw error; }
        finally { if (field.done) fields.remove(context.skillInstanceId()); }
    }

    public synchronized void tick(UUID owner, double now, AreaWorldPort port) {
        if (!Double.isFinite(now)) throw new IllegalArgumentException("Invalid clock");
        for (Field field : new ArrayList<>(fields.values())) {
            if (!field.context.request().actorId().equals(owner)) continue;
            try { tickField(field, now, port); }
            catch (RuntimeException error) { finish(field, "NATIVE_ADAPTER_FAILURE_" + error.getClass().getSimpleName(), port); }
            if (field.done) fields.remove(field.context.skillInstanceId());
        }
    }

    /** No retained native references; owner teardown is idempotent and drops every ledger. */
    public synchronized List<SkillExecutionContext> cancel(UUID owner) {
        List<SkillExecutionContext> removed = new ArrayList<>();
        fields.values().removeIf(f -> {
            if (!f.context.request().actorId().equals(owner)) return false;
            removed.add(f.context); return true;
        });
        return List.copyOf(removed);
    }
    public synchronized int size() { return fields.size(); }

    private void tickField(Field field, double now, AreaWorldPort port) {
        if (field.done || now < field.lastTick) return;
        double gap = now - field.lastTick;
        field.lastTick = now;
        AreaSkillProfile profile = field.context.profile().area();
        double elapsed = now - field.started;
        if (profile.trap()) {
            // Expiry takes priority: no detonation when a lagged tick arrives after lifetime.
            if (elapsed >= profile.lifetimeSeconds()) { finish(field, "TRAP_EXPIRED", port); return; }
            if (now < field.nextScan) return;
            field.nextScan = now + .25;
            port.present(field.context, field.geometry, elapsed < profile.armingSeconds() ? "ARMING" : "ARMED", .3);
            if (elapsed < profile.armingSeconds()) return;
            List<AreaWorldPort.Target> targets = targets(field, field.geometry, port);
            if (targets == null || targets.isEmpty()) return;
            AreaGeometry blast = profile.impactRadius() > 0
                    ? field.geometry.at(field.geometry.origin(), profile.impactRadius() * field.radiusFactor) : field.geometry;
            List<AreaWorldPort.Target> selected = profile.firstTargetOnly() ? targets : targets(field, blast, port);
            if (selected == null) return;
            int applied = hit(field, blast, selected, 0, now, port);
            if (applied > 0) { port.present(field.context, blast, "IMPACT", .3); finish(field, "TRAP_TRIGGERED", port); }
            return;
        }
        if ((profile.periodic() || profile.impactCount() > 1) && gap > 1 + 1e-9) {
            // Current entity positions cannot reconstruct an arbitrarily long missed simulation interval.
            finish(field, "SIMULATION_GAP_EXCEEDS_ONE_SECOND", port); return;
        }
        if (profile.periodic()) { tickPeriodic(field, now, port); return; }
        if (profile.impactCount() > 1) { tickImpacts(field, now, port); return; }
        if (elapsed < profile.warningSeconds()) {
            if (now >= field.nextScan) {
                field.nextScan = now + .25;
                port.present(field.context, field.geometry, "WARNING", Math.min(.3, profile.warningSeconds() - elapsed));
            }
            return;
        }
        List<AreaWorldPort.Target> targets = targets(field, field.geometry, port);
        if (targets == null) { finish(field, "CANDIDATE_BUDGET_REJECTED", port); return; }
        hit(field, field.geometry, targets, 0, now, port);
        port.present(field.context, field.geometry, "IMPACT", .3);
        finish(field, "AREA_COMPLETE", port);
    }

    private void tickPeriodic(Field field, double now, AreaWorldPort port) {
        AreaSkillProfile profile = field.context.profile().area();
        double elapsed = now - field.started;
        if (now >= field.nextScan && elapsed < profile.lifetimeSeconds()) {
            field.nextScan = now + .25; port.present(field.context, field.geometry, "ACTIVE", .3);
        }
        double end = Math.min(elapsed, profile.lifetimeSeconds());
        int index = field.nextImpact;
        while (field.integrated < end - 1e-9) {
            double next = Math.min(field.integrated + profile.intervalSeconds(), profile.lifetimeSeconds());
            if (elapsed < next - 1e-9) break;
            List<AreaWorldPort.Target> targets = targets(field, field.geometry, port);
            if (targets == null) { finish(field, "CANDIDATE_BUDGET_REJECTED", port); return; }
            double seconds = next - field.integrated;
            hit(field, field.geometry, targets, index++, field.started + next, port, seconds, true);
            field.integrated = next;
        }
        field.nextImpact = index;
        if (elapsed >= profile.lifetimeSeconds()) finish(field, "AREA_EXPIRED", port);
    }

    private void tickImpacts(Field field, double now, AreaWorldPort port) {
        AreaSkillProfile profile = field.context.profile().area();
        // Instant COMMIT starts warning preparation. Authored impact offsets are relative to the active epoch.
        // This supplies the first offset-zero Blizzard impact its mandatory warning without free pre-commit damage.
        double epoch = field.started + (profile.stratified() ? profile.warningSeconds() : 0);
        if (now >= field.nextScan && now < epoch + profile.lifetimeSeconds()) {
            field.nextScan = now + .25; port.present(field.context, field.geometry, "ACTIVE", .3);
        }
        for (int i = 0; i < profile.impactCount(); i++) {
            if (field.impacted[i]) continue;
            double impactAt = epoch + profile.firstImpactSeconds() + i * profile.intervalSeconds();
            if (now < impactAt - profile.warningSeconds() - 1e-9) continue;
            AreaGeometry footprint = profile.stratified()
                    ? field.geometry.at(field.geometry.origin().add(field.offsets.get(i)), profile.impactRadius() * field.radiusFactor)
                    : field.geometry;
            if (profile.stratified()) {
                if (!field.prepared[i]) {
                    field.prepared[i] = true;
                    field.impactGeometry[i] = port.prepareImpact(field.geometry.origin(), footprint).orElse(null);
                }
                footprint = field.impactGeometry[i];
                if (footprint == null) {
                    field.impacted[i] = true; field.nextImpact++;
                    port.trace(field.context, "AREA_QUERY_REJECTED", Map.of("reason", "NO_LEGAL_IMPACT_SURFACE", "impactIndex", i));
                    continue;
                }
            }
            if (profile.stratified() && Double.isNaN(field.warnedAt[i]) && now >= impactAt - profile.warningSeconds() - 1e-9) {
                field.warnedAt[i] = now; port.present(field.context, footprint, "WARNING", profile.warningSeconds());
            }
            if (now < impactAt - 1e-9 || profile.stratified()
                    && (Double.isNaN(field.warnedAt[i]) || now - field.warnedAt[i] < profile.warningSeconds() - 1e-9)) continue;
            if (profile.stratified()) {
                var fresh = port.prepareImpact(field.geometry.origin(), footprint);
                if (fresh.isEmpty() || fresh.get().origin().distanceSquared(footprint.origin()) > .0001) {
                    field.impacted[i] = true; field.nextImpact++;
                    port.trace(field.context, "AREA_QUERY_REJECTED", Map.of("reason", "WARNED_SURFACE_CHANGED", "impactIndex", i));
                    continue;
                }
            }
            List<AreaWorldPort.Target> targets = targets(field, footprint, port);
            if (targets == null) { finish(field, "CANDIDATE_BUDGET_REJECTED", port); return; }
            hit(field, footprint, targets, i, now, port);
            port.present(field.context, footprint, "IMPACT", .3); field.impacted[i] = true; field.nextImpact++;
        }
        if (field.nextImpact == profile.impactCount() && now >= epoch + profile.lifetimeSeconds())
            finish(field, "AREA_COMPLETE", port);
    }

    private List<AreaWorldPort.Target> targets(Field field, AreaGeometry geometry, AreaWorldPort port) {
        int budget = field.context.profile().area().candidateBudget();
        AreaWorldPort.Query query = port.query(geometry, budget);
        if (query.overflow() || query.targets().size() > budget) {
            port.trace(field.context, "AREA_QUERY_REJECTED", Map.of("reason", "CANDIDATE_BUDGET", "budget", budget));
            return null;
        }
        return query.targets().stream().filter(t -> geometry.intersects(t.bounds()))
                .filter(t -> port.lineOfSight(geometry.origin(), t))
                .sorted(Comparator.comparingDouble((AreaWorldPort.Target t) -> geometry.horizontalDistance(t.bounds()))
                        .thenComparing(AreaWorldPort.Target::id)).toList();
    }

    private int hit(Field field, AreaGeometry footprint, List<AreaWorldPort.Target> targets,
                    int impactIndex, double now, AreaWorldPort port) {
        return hit(field, footprint, targets, impactIndex, now, port, 1, false);
    }
    private int hit(Field field, AreaGeometry footprint, List<AreaWorldPort.Target> targets,
                    int impactIndex, double now, AreaWorldPort port, double seconds, boolean periodic) {
        AreaSkillProfile profile = field.context.profile().area();
        int applied = 0;
        for (AreaWorldPort.Target target : targets) {
            Ledger previous = field.ledger.get(target.id());
            if (previous != null && (previous.hits >= profile.perTargetHitCap()
                    || now - previous.lastHit < profile.targetIntervalSeconds() - 1e-9
                    || previous.lastImpact == impactIndex)) continue;
            double distance = footprint.horizontalDistance(target.bounds());
            boolean inner = profile.innerRadius() > 0 && distance <= profile.innerRadius() * field.radiusFactor;
            double coefficient = inner && profile.innerCoefficient() > 0 ? profile.innerCoefficient() : profile.coefficient();
            coefficient *= 1 - profile.edgeFalloff() * Math.clamp(distance / Math.max(.001, footprint.radius()), 0, 1);
            double duration = profile.statusInnerRadius() > 0 && distance <= profile.statusInnerRadius() * field.radiusFactor
                    ? profile.statusInnerSeconds() : profile.statusSeconds();
            int chill = inner && profile.innerChillStacks() > 0 ? profile.innerChillStacks() : profile.chillStacks();
            boolean statusReady = now - field.statusLastHit.getOrDefault(target.id(), Double.NEGATIVE_INFINITY)
                    >= profile.statusIntervalSeconds() - 1e-9;
            AreaWorldPort.Payload payload = new AreaWorldPort.Payload(impactIndex, coefficient * seconds,
                    statusReady ? profile.status() : "", duration, chill, profile.displacement() * seconds, periodic, profile.element(), footprint.origin());
            if (!port.apply(field.context, target, payload)) continue;
            if (statusReady && !profile.status().isBlank()) field.statusLastHit.put(target.id(), now);
            field.ledger.put(target.id(), new Ledger(previous == null ? 1 : previous.hits + 1, now, impactIndex));
            applied++;
            port.trace(field.context, "AREA_HIT", Map.of("target", target.id(), "impactIndex", impactIndex,
                    "coefficient", coefficient, "distance", distance));
            if (profile.firstTargetOnly()) break;
        }
        return applied;
    }
    private void finish(Field field, String reason, AreaWorldPort port) {
        field.done = true;
        port.trace(field.context, "AREA_TERMINATED", Map.of("reason", reason, "hitTargets", field.ledger.size()));
    }
    private record Ledger(int hits, double lastHit, int lastImpact) { }
    private static final class Field {
        final SkillExecutionContext context; final AreaGeometry geometry; final double started, radiusFactor;
        final Map<String, Ledger> ledger = new HashMap<>();
        final Map<String, Double> statusLastHit = new HashMap<>();
        final List<Vec3> offsets; final double[] warnedAt;
        final boolean[] prepared, impacted; final AreaGeometry[] impactGeometry;
        double nextScan, lastTick, integrated; int nextImpact; boolean done;
        Field(SkillExecutionContext context, AreaGeometry geometry, double started, double radiusFactor) {
            this.context = context; this.geometry = geometry; this.started = started; this.radiusFactor = radiusFactor;
            nextScan = started; lastTick = started;
            var profile = context.profile().area();
            offsets = profile.stratified() ? StratifiedAreaPattern.offsets(context.rootCastId() + "/" + context.profile().skillId(),
                    profile.impactCount(), geometry.radius(), profile.impactRadius() * radiusFactor) : List.of();
            warnedAt = new double[profile.impactCount()]; java.util.Arrays.fill(warnedAt, Double.NaN);
            prepared = new boolean[profile.impactCount()]; impacted = new boolean[profile.impactCount()];
            impactGeometry = new AreaGeometry[profile.impactCount()];
        }
    }
}
