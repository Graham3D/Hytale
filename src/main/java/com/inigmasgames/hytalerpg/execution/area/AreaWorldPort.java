package com.inigmasgames.hytalerpg.execution.area;

import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.List;
import java.util.Map;

/** Native world authority. Geometry and timers never write Health or emulate a damage engine. */
public interface AreaWorldPort {
    record OwnerAnchor(java.util.UUID actorId, java.util.UUID worldId, Vec3 position) {
        public OwnerAnchor {
            java.util.Objects.requireNonNull(actorId); java.util.Objects.requireNonNull(worldId);
            java.util.Objects.requireNonNull(position);
        }
    }
    /** Current live caster Transform in the committed world; absent means stop, not use stale coordinates. */
    default java.util.Optional<OwnerAnchor> ownerAnchor(SkillExecutionContext context) { return java.util.Optional.empty(); }
    record Target(String id, AreaGeometry.Bounds bounds, boolean boss) { }
    record Query(List<Target> targets, boolean overflow) {
        public Query { targets = List.copyOf(targets); }
    }
    record Payload(int impactIndex, double coefficient, String status, double statusSeconds,
                   int chillStacks, double displacement, boolean periodic, String element, Vec3 origin,
                   double pull, double pullCoreRadius, boolean pullBeforeDamage) { }

    /** Returns only living, hostile, unprotected candidates; overflow must not silently truncate hits. */
    Query query(AreaGeometry geometry, int candidateBudget);
    boolean lineOfSight(Vec3 origin, Target target);
    /** Resolves a sub-impact onto legal terrain without crossing a wall from the parent footprint. */
    default java.util.Optional<AreaGeometry> prepareImpact(Vec3 parentOrigin, AreaGeometry footprint) {
        return java.util.Optional.of(footprint);
    }
    default boolean overheadClear(AreaGeometry footprint, double height) { return true; }
    /** A finite visual position only; the native damage query stays on the ground footprint. */
    default void descendingVisual(SkillExecutionContext context, Vec3 position, double seconds) { }
    /** Final native revalidation and the existing RPG calculation -> Hytale damage path. */
    boolean apply(SkillExecutionContext context, Target target, Payload payload);
    /** Uses this exact footprint, not a particle/model as collision authority. */
    void present(SkillExecutionContext context, AreaGeometry footprint, String phase, double seconds);
    void trace(SkillExecutionContext context, String event, Map<String, ?> details);
}
