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

    public String admission(UUID owner, String skill, boolean trap) {
        if (fields.size() >= GLOBAL_CAP) return "GLOBAL_FIELD_BUDGET";
        long count = fields.values().stream().filter(f -> f.context.request().actorId().equals(owner)).count();
        if (count >= OWNER_CAP) return "OWNER_FIELD_BUDGET";
        if (trap && fields.values().stream().anyMatch(f -> f.context.request().actorId().equals(owner)
                && f.context.profile().skillId().equals(skill))) return "TRAP_ALREADY_DEPLOYED";
        return "PASS";
    }

    /** Placement is already validated; immutable position/direction survive later owner aim changes. */
    public void start(SkillExecutionContext context, Vec3 point, Vec3 direction, double now,
                      double radiusFactor, AreaWorldPort port) {
        AreaSkillProfile profile = context.profile().area();
        if (profile == null || !Double.isFinite(now) || !Double.isFinite(radiusFactor) || radiusFactor <= 0)
            throw new IllegalArgumentException("Invalid area start");
        if (!profile.trap() && (profile.impactCount() != 1 || profile.lifetimeSeconds() > 0))
            throw new IllegalStateException("AREA_DELIVERY_NOT_REGISTERED");
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

    public void tick(UUID owner, double now, AreaWorldPort port) {
        if (!Double.isFinite(now)) throw new IllegalArgumentException("Invalid clock");
        for (Field field : new ArrayList<>(fields.values())) {
            if (!field.context.request().actorId().equals(owner)) continue;
            try { tickField(field, now, port); }
            catch (RuntimeException error) { finish(field, "NATIVE_ADAPTER_FAILURE_" + error.getClass().getSimpleName(), port); }
            if (field.done) fields.remove(field.context.skillInstanceId());
        }
    }

    /** No retained native references; owner teardown is idempotent and drops every ledger. */
    public List<SkillExecutionContext> cancel(UUID owner) {
        List<SkillExecutionContext> removed = new ArrayList<>();
        fields.values().removeIf(f -> {
            if (!f.context.request().actorId().equals(owner)) return false;
            removed.add(f.context); return true;
        });
        return List.copyOf(removed);
    }
    public int size() { return fields.size(); }

    private void tickField(Field field, double now, AreaWorldPort port) {
        if (field.done || now < field.lastTick) return;
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
        // Instant/telegraphed burst foundation. Scheduled zone/stratified delivery is added in later Stage 06 cohorts.
        if (profile.impactCount() != 1 || profile.lifetimeSeconds() > 0)
            throw new IllegalStateException("AREA_DELIVERY_NOT_REGISTERED");
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
            AreaWorldPort.Payload payload = new AreaWorldPort.Payload(impactIndex, coefficient,
                    profile.status(), duration, chill, profile.displacement(), false, profile.element());
            if (!port.apply(field.context, target, payload)) continue;
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
        double nextScan, lastTick; boolean done;
        Field(SkillExecutionContext context, AreaGeometry geometry, double started, double radiusFactor) {
            this.context = context; this.geometry = geometry; this.started = started; this.radiusFactor = radiusFactor;
            nextScan = started; lastTick = started;
        }
    }
}
