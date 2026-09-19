package com.inigmasgames.persistentnpcs.task;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.inigmasgames.persistentnpcs.memory.MemoryRecord;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.memory.MemoryType;
import com.inigmasgames.persistentnpcs.event.NpcEventBus;
import com.inigmasgames.persistentnpcs.event.NpcEventType;
import com.inigmasgames.persistentnpcs.event.NpcFrameworkEvent;
import com.inigmasgames.persistentnpcs.hytale.GroundPositionResolver;
import com.inigmasgames.persistentnpcs.hytale.NpcRuntimeRegistry;
import com.inigmasgames.persistentnpcs.autonomy.AgentOperationStore;
import com.inigmasgames.persistentnpcs.plan.SharedPlan;
import com.inigmasgames.persistentnpcs.plan.SharedPlanStatus;
import com.inigmasgames.persistentnpcs.plan.SharedPlanStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.joml.Vector3d;

/** Advances persisted tasks deterministically while an NPC entity is loaded. */
public final class NpcTaskScheduler {
    private static final double REACHED_DISTANCE_SQUARED = 0.8 * 0.8;
    public static final double FOLLOW_TRAILING_DISTANCE = 2.75;
    public static final double FOLLOW_STOP_DISTANCE = 2.0;
    public static final double FOLLOW_RESUME_DISTANCE = 3.5;
    private final NpcTaskStore tasks;
    private final MemoryStore memories;
    private final NpcTaskHandler taskHandler;
    private final NpcEventBus events;
    private final Map<UUID, Boolean> followMovement = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastFollowTrace = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> groundRecoveryChecked = ConcurrentHashMap.newKeySet();
    private final Consumer<String> diagnostics;
    private final NpcRuntimeRegistry runtimes;
    private final AgentOperationStore operations;
    private final SharedPlanStore sharedPlans;
    private final Map<UUID, Instant> guideTargetMissingSince = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> guidePlayerFarSince = new ConcurrentHashMap<>();
    private final Map<UUID, GuideProgress> guideProgress = new ConcurrentHashMap<>();

    public NpcTaskScheduler(NpcTaskStore tasks, MemoryStore memories) {
        this(tasks, memories, (task, npcRef, store, world) -> task.withState(
                NpcTaskState.FAILED, "No continuation handler is registered."),
                new NpcEventBus(), ignored -> { });
    }

    public NpcTaskScheduler(
            NpcTaskStore tasks, MemoryStore memories, NpcTaskHandler taskHandler) {
        this(tasks, memories, taskHandler, new NpcEventBus(), ignored -> { });
    }

    public NpcTaskScheduler(
            NpcTaskStore tasks,
            MemoryStore memories,
            NpcTaskHandler taskHandler,
            NpcEventBus events) {
        this(tasks, memories, taskHandler, events, ignored -> { });
    }

    public NpcTaskScheduler(
            NpcTaskStore tasks,
            MemoryStore memories,
            NpcTaskHandler taskHandler,
            NpcEventBus events,
            Consumer<String> diagnostics) {
        this(tasks, memories, taskHandler, events, diagnostics, null, null, null);
    }

    public NpcTaskScheduler(
            NpcTaskStore tasks,
            MemoryStore memories,
            NpcTaskHandler taskHandler,
            NpcEventBus events,
            Consumer<String> diagnostics,
            NpcRuntimeRegistry runtimes,
            AgentOperationStore operations,
            SharedPlanStore sharedPlans) {
        this.tasks = tasks;
        this.memories = memories;
        this.taskHandler = taskHandler;
        this.events = events;
        this.diagnostics = diagnostics;
        this.runtimes = runtimes;
        this.operations = operations;
        this.sharedPlans = sharedPlans;
    }

    public void tickNpc(
            UUID profileId,
            Ref<EntityStore> npcRef,
            NPCEntity npc,
            TransformComponent transform,
            Store<EntityStore> store) {
        WorldTimeResource worldTime = store.getResource(WorldTimeResource.getResourceType());
        Instant gameNow = worldTime == null ? null : worldTime.getGameTime();
        World world = store.getExternalData().getWorld();
        for (NpcTask task : tasks.activeFor(profileId)) {
            switch (task.type().toUpperCase(java.util.Locale.ROOT)) {
                case "FOLLOW_PLAYER" -> tickFollow(task, npcRef, npc, transform, store, world);
                case "GO_TO" -> tickGoTo(task, npc, transform, gameNow);
                case "PATROL" -> tickPatrol(task, npc, transform, gameNow);
                case "WANDER", "FLEE", "GO_TO_LOCATION", "FETCH_ITEM",
                        "FETCH_PERSON", "DELIVER_ITEM", "DELIVER_MESSAGE",
                        "WORK_SHIFT", "RETURN_HOME" ->
                        tickGoTo(task, npc, transform, gameNow);
                case "ESCORT", "SEARCH_WITH_PLAYER" -> {
                    if (ready(task, gameNow, NpcTaskState.ACTIVE)) {
                        tickFollow(task, npcRef, npc, transform, store, world);
                    }
                }
                case "WAIT" -> hold(npc, transform);
                case "WAIT_UNTIL" -> tickWaitUntil(task, npc, transform, gameNow);
                case "SCHEDULE_MEETING" ->
                        tickMeeting(task, npc, transform, store, gameNow);
                case "CRAFT_FOR_PLAYER", "BRING_ITEM" ->
                        tickCraft(task, npcRef, npc, transform, store, world);
                case "GUIDE_PLAYER_TO_NPC" ->
                        tickGuide(task, npc, transform, store, world, Instant.now());
                default -> fail(task, "Unsupported persisted task type " + task.type());
            }
        }
    }

    public boolean hasActiveTasks(UUID npcId) {
        return !tasks.activeFor(npcId).isEmpty();
    }

    private void tickCraft(
            NpcTask task,
            Ref<EntityStore> npcRef,
            NPCEntity npc,
            TransformComponent transform,
            Store<EntityStore> store,
            World world) {
        String phase = task.data().getOrDefault("phase", "CRAFT");
        if ("DELIVER".equals(phase)) {
            Ref<EntityStore> playerRef = store.getExternalData()
                    .getRefFromUUID(task.requesterPlayerId());
            TransformComponent player = playerRef == null || !playerRef.isValid()
                    ? null : store.getComponent(playerRef, TransformComponent.getComponentType());
            if (player == null) {
                return;
            }
            if (transform.getPosition().distanceSquared(player.getPosition())
                    > 4.0 * 4.0) {
                npc.setLeashPoint(trailingPosition(player.getPosition(),
                        new Vector3d(0, 0, 1)));
                return;
            }
            hold(npc, transform);
            persistContinuation(task, taskHandler.resume(task, npcRef, store, world));
            return;
        }
        Vector3d target = target(task);
        if (target == null) {
            fail(task, "The crafting task has no workstation target.");
        } else if (transform.getPosition().distanceSquared(target)
                > REACHED_DISTANCE_SQUARED) {
            npc.setLeashPoint(target);
        } else {
            hold(npc, transform);
            persistContinuation(task, taskHandler.resume(task, npcRef, store, world));
        }
    }

    private void persistContinuation(NpcTask prior, NpcTask updated) {
        if (updated == null) {
            fail(prior, "The task continuation returned no result.");
            return;
        }
        tasks.put(updated);
        if (updated.terminal()) {
            remember(updated, updated.lastResult());
        }
    }

    private void tickFollow(
            NpcTask task,
            Ref<EntityStore> npcRef,
            NPCEntity npc,
            TransformComponent npcTransform,
            Store<EntityStore> store,
            World world) {
        Ref<EntityStore> playerRef = store.getExternalData()
                .getRefFromUUID(task.requesterPlayerId());
        if (playerRef == null || !playerRef.isValid()) {
            return; // Keep the commitment persisted until the player/world entity is loaded.
        }
        TransformComponent target = store.getComponent(
                playerRef, TransformComponent.getComponentType());
        PlayerRef player = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (target == null || player == null) {
            return;
        }
        Vector3d desired = trailingPosition(target.getPosition(),
                new Vector3d(player.getTransform().getDirection()));
        desired = GroundPositionResolver.resolve(world, desired).orElse(desired);
        boolean grounded = GroundPositionResolver.isGrounded(world, npcTransform.getPosition());
        if (!grounded && groundRecoveryChecked.add(task.taskId())) {
            GroundPositionResolver.resolve(world, npcTransform.getPosition()).ifPresent(recovered -> {
                npcTransform.teleportPosition(recovered);
                resetGroundMotion(npcRef, store);
                diagnostics.accept("Follow grounding recovery task=" + task.taskId()
                        + " from=" + compact(npcTransform.getPosition())
                        + " to=" + compact(recovered));
            });
            grounded = GroundPositionResolver.isGrounded(world, npcTransform.getPosition());
        }
        double distance = npcTransform.getPosition().distance(desired);
        boolean moving = followMovement.getOrDefault(task.npcId(), true);
        if (moving && distance <= FOLLOW_STOP_DISTANCE) {
            moving = false;
        } else if (!moving && distance >= FOLLOW_RESUME_DISTANCE) {
            moving = true;
        }
        followMovement.put(task.npcId(), moving);
        if (moving) {
            npc.setLeashPoint(desired);
        } else {
            hold(npc, npcTransform);
        }
        MovementStatesComponent states = store.getComponent(
                npcRef, MovementStatesComponent.getComponentType());
        MovementStates movement = states == null ? null : states.getMovementStates();
        String movementName = movementState(movement);
        String trace = "followAuthorized=true actionRequested=true validation=PASSED"
                + " taskCreated=true navigationStarted=" + moving
                + " targetPosition=" + compact(desired)
                + " npcGrounded=" + grounded
                + " distanceToTarget=" + "%.2f".formatted(distance)
                + " movementState=" + movementName
                + " failureReason=NONE";
        String traceState = moving + ":" + grounded + ":" + movementName;
        if (!traceState.equals(lastFollowTrace.put(task.taskId(), traceState))) {
            diagnostics.accept("Follow trace task=" + task.taskId() + " " + trace);
        }
    }

    private void tickGuide(
            NpcTask task,
            NPCEntity npc,
            TransformComponent guideTransform,
            Store<EntityStore> store,
            World world,
            Instant now) {
        UUID operationId = uuid(task.data().get("operationId"));
        UUID targetProfileId = uuid(task.data().get("targetProfileId"));
        if (operationId == null || targetProfileId == null || runtimes == null
                || operations == null || sharedPlans == null) {
            finishGuide(task, npc, guideTransform, false,
                    "The persisted guide operation is incomplete.");
            return;
        }
        if (!operations.ownsActive(operationId, task.npcId(), now)) {
            finishGuide(task, npc, guideTransform, false,
                    "The guide operation expired or no longer owns this NPC.");
            return;
        }
        NpcRuntimeRegistry.RuntimeNpc targetRuntime = runtimes.forProfile(
                targetProfileId).orElse(null);
        Ref<EntityStore> targetRef = targetRuntime == null || targetRuntime.worldId() == null
                || task.worldId() == null || !task.worldId().equals(targetRuntime.worldId())
                        ? null : world.getEntityRef(targetRuntime.entityId());
        TransformComponent target = targetRef == null || !targetRef.isValid() ? null
                : store.getComponent(targetRef, TransformComponent.getComponentType());
        if (target == null) {
            Instant missing = guideTargetMissingSince.computeIfAbsent(task.taskId(),
                    ignored -> now);
            hold(npc, guideTransform);
            if (Duration.between(missing, now).toSeconds() >= 10) {
                finishGuide(task, npc, guideTransform, false,
                        "The destination NPC despawned or left loaded world state.");
            }
            return;
        }
        guideTargetMissingSince.remove(task.taskId());

        Ref<EntityStore> playerRef = store.getExternalData()
                .getRefFromUUID(task.requesterPlayerId());
        TransformComponent player = playerRef == null || !playerRef.isValid() ? null
                : store.getComponent(playerRef, TransformComponent.getComponentType());
        double playerDistance = player == null ? Double.POSITIVE_INFINITY
                : guideTransform.getPosition().distance(player.getPosition());
        if (playerDistance > 32.0) {
            Instant farSince = guidePlayerFarSince.computeIfAbsent(task.taskId(),
                    ignored -> now);
            hold(npc, guideTransform);
            if (Duration.between(farSince, now).toSeconds() >= 20) {
                finishGuide(task, npc, guideTransform, false,
                        "The player did not remain close enough to continue guidance.");
            }
            return;
        }
        guidePlayerFarSince.remove(task.taskId());

        double targetDistance = guideTransform.getPosition()
                .distance(target.getPosition());
        if (targetDistance > 500.0) {
            finishGuide(task, npc, guideTransform, false,
                    "The destination NPC left the bounded lookup range.");
            return;
        }
        if (targetDistance <= 3.0 && playerDistance <= 12.0) {
            finishGuide(task, npc, guideTransform, true,
                    "The guide brought the player to "
                            + task.data().getOrDefault("targetName", "the known NPC") + ".");
            return;
        }
        GuideProgress progress = guideProgress.get(task.taskId());
        if (progress == null || targetDistance + 1.0 < progress.bestDistance()) {
            guideProgress.put(task.taskId(), new GuideProgress(targetDistance, now));
        } else if (Duration.between(progress.lastProgressAt(), now).toSeconds() >= 45) {
            finishGuide(task, npc, guideTransform, false,
                    "Native navigation could not reach the destination NPC.");
            return;
        }
        // Native NPC pathing owns traversal; the target position is refreshed every tick.
        npc.setLeashPoint(new Vector3d(target.getPosition()));
    }

    private void finishGuide(NpcTask task, NPCEntity npc,
            TransformComponent transform, boolean success, String result) {
        hold(npc, transform);
        NpcTask terminal = task.withState(success
                ? NpcTaskState.COMPLETED : NpcTaskState.FAILED, result);
        tasks.put(terminal);
        remember(terminal, result);
        UUID operationId = uuid(task.data().get("operationId"));
        if (operationId != null && operations != null) {
            operations.complete(operationId, success, result);
        }
        UUID planId = uuid(task.data().get("sharedPlanId"));
        if (planId != null && sharedPlans != null) {
            SharedPlan plan = sharedPlans.get(planId);
            if (plan != null && !plan.status().terminal()) {
                sharedPlans.put(plan.withStatus(success
                        ? SharedPlanStatus.COMPLETED : SharedPlanStatus.FAILED));
            }
        }
        tasks.resumeAfterGuide(task.npcId(), task.taskId());
        guideTargetMissingSince.remove(task.taskId());
        guidePlayerFarSince.remove(task.taskId());
        guideProgress.remove(task.taskId());
        diagnostics.accept("Guide operation " + (success ? "completed" : "failed")
                + " npc=" + task.npcId() + " result=" + result);
    }

    /** Computes a reusable horizontal follow point behind an entity's current facing. */
    public static Vector3d trailingPosition(Vector3d playerPosition, Vector3d playerForward) {
        Vector3d horizontal = new Vector3d(playerForward.x, 0, playerForward.z);
        if (horizontal.lengthSquared() < 0.0001) {
            horizontal.set(0, 0, 1);
        } else {
            horizontal.normalize();
        }
        return new Vector3d(playerPosition).sub(
                horizontal.mul(FOLLOW_TRAILING_DISTANCE));
    }

    private void tickGoTo(
            NpcTask task, NPCEntity npc, TransformComponent transform, Instant gameNow) {
        if (!ready(task, gameNow, NpcTaskState.TRAVELING)) {
            return;
        }
        Vector3d target = target(task);
        if (target == null) {
            fail(task, "The task has no target position.");
            return;
        }
        if (transform.getPosition().distanceSquared(target) <= REACHED_DISTANCE_SQUARED) {
            hold(npc, transform);
            complete(task, "Mara reached the requested destination.");
        } else {
            npc.setLeashPoint(target);
        }
    }

    private void tickPatrol(
            NpcTask task, NPCEntity npc, TransformComponent transform, Instant gameNow) {
        if (!ready(task, gameNow, NpcTaskState.TRAVELING)) {
            return;
        }
        Vector3d target = target(task);
        if (target == null) {
            fail(task, "The patrol has no target position.");
            return;
        }
        if (transform.getPosition().distanceSquared(target) > REACHED_DISTANCE_SQUARED) {
            npc.setLeashPoint(target);
            return;
        }
        String nextLeg = "B".equals(task.data().getOrDefault("leg", "B")) ? "A" : "B";
        Vector3d next = dataPosition(task, nextLeg.toLowerCase(java.util.Locale.ROOT));
        if (next == null) {
            fail(task, "The patrol endpoint data is invalid.");
            return;
        }
        Map<String, String> data = new java.util.LinkedHashMap<>(task.data());
        data.put("leg", nextLeg);
        NpcTask updated = task.withTarget(next.x, next.y, next.z).withData(data)
                .withState(NpcTaskState.TRAVELING, "Continuing patrol leg " + nextLeg + ".");
        tasks.put(updated);
        npc.setLeashPoint(next);
    }

    private void tickWaitUntil(
            NpcTask task, NPCEntity npc, TransformComponent transform, Instant gameNow) {
        hold(npc, transform);
        if (gameNow == null || task.scheduledGameTime() == null) {
            fail(task, "World game time is unavailable for WAIT_UNTIL.");
        } else if (!gameNow.isBefore(task.scheduledGameTime())) {
            complete(task, "The scheduled wait time was reached.");
        }
    }

    private boolean ready(NpcTask task, Instant gameNow, NpcTaskState startedState) {
        if (task.scheduledGameTime() != null) {
            if (gameNow == null || gameNow.isBefore(task.scheduledGameTime())) {
                return false;
            }
            if (task.state() == NpcTaskState.PLANNED) {
                tasks.put(task.withState(startedState,
                        "Scheduled task began at game time " + gameNow + "."));
            }
        }
        return true;
    }

    private void tickMeeting(
            NpcTask task,
            NPCEntity npc,
            TransformComponent transform,
            Store<EntityStore> store,
            Instant gameNow) {
        if (gameNow == null || task.scheduledGameTime() == null) {
            fail(task, "World game time is unavailable.");
            return;
        }
        Vector3d target = target(task);
        if (target == null) {
            fail(task, "The meeting has no target position.");
            return;
        }
        double distance = transform.getPosition().distance(target);
        Instant departAt = task.scheduledGameTime().minusMillis(
                (long) (((distance / 2.5) + 10.0) * 1000.0));
        if (task.state() == NpcTaskState.PLANNED && gameNow.isBefore(departAt)) {
            return;
        }
        if (task.state() == NpcTaskState.PLANNED) {
            tasks.put(task.withState(NpcTaskState.TRAVELING,
                    "Travel began at game time " + gameNow + "."));
        }
        if (transform.getPosition().distanceSquared(target) > REACHED_DISTANCE_SQUARED) {
            npc.setLeashPoint(target);
            return;
        }
        hold(npc, transform);
        if (gameNow.isBefore(task.scheduledGameTime())) {
            if (task.state() != NpcTaskState.WAITING) {
                tasks.put(task.withState(NpcTaskState.WAITING,
                        "Arrived early and is waiting."));
            }
            return;
        }
        Ref<EntityStore> playerRef = store.getExternalData()
                .getRefFromUUID(task.requesterPlayerId());
        TransformComponent playerTransform = playerRef == null || !playerRef.isValid()
                ? null : store.getComponent(playerRef, TransformComponent.getComponentType());
        if (playerTransform != null
                && playerTransform.getPosition().distanceSquared(target) <= 5.0 * 5.0) {
            complete(task, "Mara kept the meeting with the requester.");
            return;
        }
        if (Duration.between(task.scheduledGameTime(), gameNow).toMinutes() >= 60) {
            fail(task, "Mara arrived, but the requester did not attend within one game hour.");
        } else if (task.state() != NpcTaskState.ACTIVE) {
            tasks.put(task.withState(NpcTaskState.ACTIVE,
                    "Mara is at the meeting point waiting for the requester."));
        }
    }

    private static void hold(NPCEntity npc, TransformComponent transform) {
        npc.getPathManager().setTransientPath(null);
        npc.setLeashPoint(new Vector3d(transform.getPosition()));
    }

    private static void resetGroundMotion(
            Ref<EntityStore> npcRef, Store<EntityStore> store) {
        Velocity velocity = store.getComponent(npcRef, Velocity.getComponentType());
        if (velocity != null) {
            velocity.getInstructions().clear();
            velocity.setZero();
            velocity.setClient(0, 0, 0);
        }
        MovementStatesComponent component = store.getComponent(
                npcRef, MovementStatesComponent.getComponentType());
        if (component != null) {
            MovementStates idle = new MovementStates();
            idle.idle = true;
            idle.horizontalIdle = true;
            idle.onGround = true;
            component.setMovementStates(idle);
        }
    }

    private static String movementState(MovementStates movement) {
        if (movement == null) return "UNKNOWN";
        if (movement.flying) return "FLYING";
        if (movement.falling) return "FALLING";
        if (movement.running) return "RUNNING";
        if (movement.walking) return "WALKING";
        if (movement.onGround && movement.idle) return "GROUNDED_IDLE";
        return movement.onGround ? "GROUNDED" : "AIRBORNE";
    }

    private static String compact(Vector3d value) {
        return "%.1f,%.1f,%.1f".formatted(value.x, value.y, value.z);
    }

    private void complete(NpcTask task, String result) {
        NpcTask completed = task.withState(NpcTaskState.COMPLETED, result);
        tasks.put(completed);
        remember(completed, result);
    }

    private void fail(NpcTask task, String result) {
        NpcTask failed = task.withState(NpcTaskState.FAILED, result);
        tasks.put(failed);
        remember(failed, result);
    }

    private void remember(NpcTask task, String result) {
        memories.append(new MemoryRecord(UUID.randomUUID(), task.npcId(),
                task.requesterPlayerId(), Instant.now(), MemoryType.ACTION_RESULT,
                task.state() == NpcTaskState.COMPLETED ? 0.8 : 0.65,
                "Task " + task.type() + " " + task.state() + ": " + result));
        NpcEventType eventType = task.state() == NpcTaskState.COMPLETED
                ? NpcEventType.TASK_COMPLETED : NpcEventType.TASK_FAILED;
        events.emit(new NpcFrameworkEvent(UUID.randomUUID(), eventType, task.npcId(),
                task.npcId(), task.requesterPlayerId(), Instant.now(),
                Map.of("taskType", task.type(), "result", result == null ? "" : result)));
    }

    private static Vector3d target(NpcTask task) {
        return task.targetX() == null || task.targetY() == null || task.targetZ() == null
                ? null : new Vector3d(task.targetX(), task.targetY(), task.targetZ());
    }

    private static Vector3d dataPosition(NpcTask task, String prefix) {
        try {
            return new Vector3d(Double.parseDouble(task.data().get(prefix + "x")),
                    Double.parseDouble(task.data().get(prefix + "y")),
                    Double.parseDouble(task.data().get(prefix + "z")));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private record GuideProgress(double bestDistance, Instant lastProgressAt) { }
}
