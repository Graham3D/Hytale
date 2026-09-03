package com.inigmasgames.persistentnpcs.home;

import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.inigmasgames.persistentnpcs.hytale.GroundPositionResolver;
import com.inigmasgames.persistentnpcs.task.NpcTask;
import com.inigmasgames.persistentnpcs.task.NpcTaskStore;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.joml.Vector3d;

/** Deterministic home idle/investigate/return controller; it never calls the LLM. */
public final class NpcHomeBehaviorController {
    private static final double REACHED_SQUARED = 0.8 * 0.8;
    private static final double DRIFT_SQUARED = 1.25 * 1.25;
    private final NpcHomeAnchorStore anchors;
    private final NpcTaskStore tasks;
    private final HomeBehaviorConfig config;
    private final Consumer<String> diagnostics;

    public NpcHomeBehaviorController(
            NpcHomeAnchorStore anchors,
            NpcTaskStore tasks,
            HomeBehaviorConfig config,
            Consumer<String> diagnostics) {
        this.anchors = anchors;
        this.tasks = tasks;
        this.config = config;
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
    }

    public NpcHomeAnchor initialize(
            UUID npcId, UUID worldId, Vector3d position, Instant now) {
        NpcHomeAnchor prior = anchors.get(npcId);
        NpcHomeAnchor anchor = anchors.initialize(npcId, worldId,
                new Vector3d(position), nextWander(npcId, now));
        int cancelled = tasks.cancelLegacyFollowTasks(npcId,
                "Legacy follow state had no explicit player action provenance.");
        if (prior == null || cancelled > 0) {
            diagnostics.accept("Home anchor initialized npc=" + npcId
                    + " position=" + compact(anchor.anchor())
                    + " legacyFollowTasksCancelled=" + cancelled
                    + " movementState=" + anchor.movementState());
        }
        return anchor;
    }

    public void beginFollowing(UUID npcId) {
        NpcHomeAnchor current = anchors.get(npcId);
        if (current != null && current.movementState() != NpcMovementState.FOLLOWING_PLAYER) {
            anchors.put(current.withState(NpcMovementState.FOLLOWING_PLAYER,
                    null, null, current.nextWanderAt()));
        }
    }

    public NpcHomeAnchor stopFollowing(
            UUID npcId, UUID worldId, Vector3d currentPosition,
            boolean waitHere, Instant now) {
        NpcHomeAnchor current = initialize(npcId, worldId, currentPosition, now);
        NpcHomeAnchor updated = waitHere
                ? current.withAnchor(new Vector3d(currentPosition), true,
                        nextWander(npcId, now))
                : current.withState(NpcMovementState.RETURNING_HOME,
                        current.anchor(), null, current.nextWanderAt());
        anchors.put(updated);
        diagnostics.accept("Home movement transition npc=" + npcId
                + " state=" + updated.movementState()
                + " anchor=" + compact(updated.anchor())
                + " temporary=" + updated.temporaryAnchor());
        return updated;
    }

    public void tick(
            UUID npcId,
            UUID worldId,
            NPCEntity npc,
            TransformComponent transform,
            World world,
            boolean sociallyEngaged,
            Instant now) {
        if (!config.isEnabled()) {
            return;
        }
        NpcHomeAnchor current = initialize(
                npcId, worldId, transform.getPosition(), now);
        List<NpcTask> active = tasks.activeFor(npcId);
        NpcTask follow = active.stream().filter(NpcHomeBehaviorController::isFollow)
                .min(Comparator.comparing(NpcTask::createdAt)).orElse(null);
        if (follow != null) {
            beginFollowing(npcId);
            return;
        }
        if (active.stream().anyMatch(NpcHomeBehaviorController::isLocomotionTask)) {
            return;
        }
        if (current.movementState() == NpcMovementState.FOLLOWING_PLAYER) {
            current = anchors.put(current.withState(NpcMovementState.RETURNING_HOME,
                    current.anchor(), null, current.nextWanderAt()));
        }
        if (sociallyEngaged) {
            hold(npc, transform.getPosition());
            return;
        }
        switch (current.movementState()) {
            case IDLE_HOME -> tickIdle(current, npc, transform, world, now);
            case INVESTIGATING -> tickInvestigating(current, npc, transform, now);
            case RETURNING_HOME -> tickReturning(current, npc, transform, now);
            case FOLLOWING_PLAYER -> { }
        }
    }

    private void tickIdle(
            NpcHomeAnchor current, NPCEntity npc, TransformComponent transform,
            World world, Instant now) {
        if (transform.getPosition().distanceSquared(current.anchor()) > DRIFT_SQUARED) {
            transition(current, NpcMovementState.RETURNING_HOME,
                    current.anchor(), null, current.nextWanderAt());
            npc.setLeashPoint(current.anchor());
            return;
        }
        hold(npc, transform.getPosition());
        if (now.isBefore(current.nextWanderAt())) {
            return;
        }
        Vector3d proposed = wanderTarget(current.anchor(), config.effectiveRadius(),
                current.npcId().getMostSignificantBits() ^ now.getEpochSecond());
        Vector3d target = GroundPositionResolver.resolve(world, proposed).orElse(null);
        if (target == null || target.distanceSquared(current.anchor())
                > config.effectiveRadius() * config.effectiveRadius() + 0.01) {
            anchors.put(current.withState(NpcMovementState.IDLE_HOME, null, null,
                    nextWander(current.npcId(), now)));
            return;
        }
        transition(current, NpcMovementState.INVESTIGATING, target, null,
                current.nextWanderAt());
        npc.setLeashPoint(target);
    }

    private void tickInvestigating(
            NpcHomeAnchor current, NPCEntity npc, TransformComponent transform, Instant now) {
        Vector3d target = current.target();
        if (target == null) {
            transition(current, NpcMovementState.RETURNING_HOME,
                    current.anchor(), null, current.nextWanderAt());
            return;
        }
        if (transform.getPosition().distanceSquared(target) > REACHED_SQUARED) {
            npc.setLeashPoint(target);
            return;
        }
        hold(npc, transform.getPosition());
        if (current.stateDueAt() == null) {
            anchors.put(current.withState(NpcMovementState.INVESTIGATING, target,
                    now.plusSeconds(config.effectiveInvestigationPauseSeconds()),
                    current.nextWanderAt()));
        } else if (!now.isBefore(current.stateDueAt())) {
            transition(current, NpcMovementState.RETURNING_HOME,
                    current.anchor(), null, current.nextWanderAt());
        }
    }

    private void tickReturning(
            NpcHomeAnchor current, NPCEntity npc, TransformComponent transform, Instant now) {
        Vector3d anchor = current.anchor();
        if (transform.getPosition().distanceSquared(anchor) > REACHED_SQUARED) {
            npc.setLeashPoint(anchor);
            return;
        }
        hold(npc, transform.getPosition());
        transition(current, NpcMovementState.IDLE_HOME, null, null,
                nextWander(current.npcId(), now));
    }

    private void transition(
            NpcHomeAnchor current,
            NpcMovementState state,
            Vector3d target,
            Instant dueAt,
            Instant nextWander) {
        anchors.put(current.withState(state, target, dueAt, nextWander));
        diagnostics.accept("Home movement transition npc=" + current.npcId()
                + " state=" + state
                + " target=" + (target == null ? "NONE" : compact(target)));
    }

    private Instant nextWander(UUID npcId, Instant now) {
        int minimum = config.effectiveMinimumIdleSeconds();
        int range = config.effectiveMaximumIdleSeconds() - minimum + 1;
        long seed = npcId.getLeastSignificantBits() ^ now.getEpochSecond();
        int offset = range <= 1 ? 0 : Math.floorMod(seed, range);
        return now.plusSeconds(minimum + offset);
    }

    public static Vector3d wanderTarget(Vector3d anchor, double radius, long seed) {
        double boundedRadius = Math.max(1.5, Math.min(10.0, radius));
        double angle = Math.floorMod(seed, 6283L) / 1000.0;
        double distance = boundedRadius * (0.55 + Math.floorMod(seed >>> 8, 46L) / 100.0);
        return new Vector3d(anchor).add(
                Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
    }

    private static boolean isFollow(NpcTask task) {
        return "FOLLOW_PLAYER".equalsIgnoreCase(task.type());
    }

    public static boolean isLocomotionTask(NpcTask task) {
        return switch (task.type().toUpperCase(java.util.Locale.ROOT)) {
            case "FOLLOW_PLAYER", "GO_TO", "PATROL", "WANDER", "FLEE",
                    "ESCORT", "SEARCH_WITH_PLAYER", "GO_TO_LOCATION",
                    "FETCH_ITEM", "FETCH_PERSON", "DELIVER_ITEM",
                    "DELIVER_MESSAGE", "WORK_SHIFT", "RETURN_HOME",
                    "BRING_ITEM", "CRAFT_FOR_PLAYER", "GUIDE_PLAYER_TO_NPC" -> true;
            default -> false;
        };
    }

    private static void hold(NPCEntity npc, Vector3d position) {
        npc.getPathManager().setTransientPath(null);
        npc.setLeashPoint(new Vector3d(position));
    }

    private static String compact(Vector3d value) {
        return "%.1f,%.1f,%.1f".formatted(value.x, value.y, value.z);
    }
}
