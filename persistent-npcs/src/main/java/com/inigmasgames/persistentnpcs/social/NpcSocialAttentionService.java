package com.inigmasgames.persistentnpcs.social;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.util.PhysicsMath;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.PositionCache;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.inigmasgames.persistentnpcs.cognition.AttentionAction;
import com.inigmasgames.persistentnpcs.cognition.NpcResponsePlan;
import com.inigmasgames.persistentnpcs.cognition.NpcSocialPerformance;
import com.inigmasgames.persistentnpcs.perception.EnvironmentFeature;
import com.inigmasgames.persistentnpcs.perception.EnvironmentSnapshot;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.conversation.ConversationSessionManager;
import com.inigmasgames.persistentnpcs.hytale.HytaleNpcAdapter;
import com.inigmasgames.persistentnpcs.hytale.GroundPositionResolver;
import com.inigmasgames.persistentnpcs.hytale.NpcRuntimeRegistry;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.joml.Vector3d;

/** Proximity attention with 4m enter / 5m exit hysteresis. Never calls the LLM. */
public final class NpcSocialAttentionService implements NpcSocialPerformance {
    public static final double ENTER_DISTANCE = 4.0;
    public static final double EXIT_DISTANCE = 5.0;
    public static final double CONVERSATION_DISTANCE = 1.75;
    private static final double CONVERSATION_MIN_DISTANCE = 1.45;
    private static final double CONVERSATION_MAX_DISTANCE = 2.10;
    private static final double PLAYER_REPATH_DISTANCE_SQUARED = 0.75 * 0.75;
    private static final double TARGET_REACHED_DISTANCE_SQUARED = 0.55 * 0.55;
    private static final String TARGET_SLOT = "SocialFocus";
    private static final long CURIOSITY_COOLDOWN_SECONDS = 180;
    private static final long CURIOSITY_RECONSIDER_SECONDS = 45;
    private final Supplier<NpcProfile> profile;
    private final NpcProfileRegistry profileRegistry;
    private final ConversationSessionManager sessions;
    private final NpcRuntimeRegistry runtimes;
    private final Consumer<String> diagnostics;
    private final Predicate<UUID> movementTaskActive;
    private final double conversationEnterRadius;
    private final double conversationListenRadius;
    private final double remoteHailRadius;
    private final Map<UUID, RuntimeAttention> states = new ConcurrentHashMap<>();
    private final Map<CuriosityKey, Instant> nextCuriosityAt = new ConcurrentHashMap<>();
    private volatile BiConsumer<PlayerRef, ConversationSession> curiosityConsumer =
            (ignoredPlayer, ignoredSession) -> { };
    private volatile BiConsumer<UUID, UUID> focusLostConsumer =
            (ignoredNpc, ignoredPlayer) -> { };

    public NpcSocialAttentionService(
            Supplier<NpcProfile> profile,
            ConversationSessionManager sessions,
            NpcRuntimeRegistry runtimes) {
        this(profile, sessions, runtimes, ignored -> { }, ignored -> false);
    }

    public NpcSocialAttentionService(
            Supplier<NpcProfile> profile,
            ConversationSessionManager sessions,
            NpcRuntimeRegistry runtimes,
            Consumer<String> diagnostics) {
        this(profile, sessions, runtimes, diagnostics, ignored -> false);
    }

    public NpcSocialAttentionService(
            Supplier<NpcProfile> profile,
            ConversationSessionManager sessions,
            NpcRuntimeRegistry runtimes,
            Consumer<String> diagnostics,
            Predicate<UUID> movementTaskActive) {
        this.profile = profile;
        this.profileRegistry = null;
        this.sessions = sessions;
        this.runtimes = runtimes;
        this.diagnostics = diagnostics;
        this.movementTaskActive = movementTaskActive;
        this.conversationListenRadius = EXIT_DISTANCE;
        this.conversationEnterRadius = ENTER_DISTANCE;
        this.remoteHailRadius = EXIT_DISTANCE * 3.0;
    }

    public NpcSocialAttentionService(
            NpcProfileRegistry profiles,
            ConversationSessionManager sessions,
            NpcRuntimeRegistry runtimes,
            Consumer<String> diagnostics,
            Predicate<UUID> movementTaskActive) {
        this(profiles, sessions, runtimes, diagnostics, movementTaskActive,
                EXIT_DISTANCE, EXIT_DISTANCE * 3.0);
    }

    public NpcSocialAttentionService(
            NpcProfileRegistry profiles,
            ConversationSessionManager sessions,
            NpcRuntimeRegistry runtimes,
            Consumer<String> diagnostics,
            Predicate<UUID> movementTaskActive,
            double conversationListenRadius,
            double remoteHailRadius) {
        this.profileRegistry = profiles;
        this.profile = profiles::defaultProfile;
        this.sessions = sessions;
        this.runtimes = runtimes;
        this.diagnostics = diagnostics;
        this.movementTaskActive = movementTaskActive;
        this.conversationListenRadius = Math.max(2.0, conversationListenRadius);
        this.conversationEnterRadius = Math.max(1.0,
                this.conversationListenRadius - 1.0);
        this.remoteHailRadius = Math.max(this.conversationListenRadius, remoteHailRadius);
    }

    @Override
    public void perform(
            UUID npcId, UUID playerId, NpcResponsePlan plan, EnvironmentSnapshot environment) {
        if (plan == null || !plan.attentionActions().contains(AttentionAction.LOOK_AROUND)) {
            return;
        }
        states.values().stream()
                .filter(runtime -> runtime.profileId.equals(npcId)
                        && playerId.equals(runtime.state.focusedPlayerUuid()))
                .findFirst().ifPresent(runtime -> {
                    List<Vector3d> points = scanPoints(runtime.position, environment);
                    runtime.performance = new PerformanceSequence(points,
                            System.nanoTime(), !plan.emote().isBlank(), false);
                    diagnostics.accept("Social performance start npc=" + npcId
                            + " action=LOOK_AROUND poiCount=" + points.size()
                            + " emote=" + (plan.emote().isBlank() ? "NONE" : plan.emote()));
                });
    }

    public void tickNpc(
            Ref<EntityStore> npcRef,
            NPCEntity npc,
            TransformComponent transform,
            UUID entityId,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer) {
        tickNpc(profile.get(), npcRef, npc, transform, entityId, store, commandBuffer);
    }

    public void tickNpc(
            NpcProfile current,
            Ref<EntityStore> npcRef,
            NPCEntity npc,
            TransformComponent transform,
            UUID entityId,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer) {
        if (!HytaleNpcAdapter.isManagedRole(npc.getNPCTypeId())) {
            return;
        }
        World world = store.getExternalData().getWorld();
        UUID worldId = worldId(world);
        if (worldId == null) {
            return;
        }
        runtimes.register(current.id(), worldId, entityId);
        RuntimeAttention runtime = states.computeIfAbsent(entityId,
                ignored -> new RuntimeAttention(current.id()));
        runtime.position.set(transform.getPosition());
        runtime.worldId = worldId;
        Instant now = Instant.now();

        List<PlayerDistance> nearby = new ArrayList<>();
        runtime.potentialPlayers.clear();
        for (PlayerRef player : world.getPlayerRefs()) {
            if (player == null || !player.isValid() || player.getReference() == null
                    || !player.getReference().isValid()) {
                continue;
            }
            TransformComponent playerTransform = commandBuffer.getComponent(
                    player.getReference(), TransformComponent.getComponentType());
            if (playerTransform == null) {
                continue;
            }
            double distanceSquared = transform.getPosition()
                    .distanceSquared(playerTransform.getPosition());
            if (distanceSquared <= remoteHailRadius * remoteHailRadius) {
                runtime.potentialPlayers.add(player.getUuid());
            }
            PositionCache positionCache = PositionCache.get(npcRef, commandBuffer);
            boolean visible = positionCache != null && positionCache.hasLineOfSight(
                    npcRef, player.getReference(), commandBuffer);
            if (distanceSquared <= conversationListenRadius * conversationListenRadius
                    && visible) {
                runtime.state.perceive(player.getUuid(), now);
                nearby.add(new PlayerDistance(player, distanceSquared));
            } else {
                runtime.state.forget(player.getUuid());
            }
        }

        UUID focused = runtime.state.focusedPlayerUuid();
        PlayerDistance focusedDistance = nearby.stream()
                .filter(value -> value.player().getUuid().equals(focused))
                .findFirst().orElse(null);
        if (focused != null && focusedDistance == null) {
            release(runtime, npcRef, npc, transform, commandBuffer);
            sessions.end(focused, current.id());
            // This is a world-thread callback into an enqueue-only Orbis adapter. It ensures
            // a long provider request cannot continue consuming resources after the physical
            // conversation has ended.
            focusLostConsumer.accept(current.id(), focused);
        }
        if (runtime.state.focusedPlayerUuid() == null) {
            nearby.stream()
                    .filter(value -> value.distanceSquared()
                            <= conversationEnterRadius * conversationEnterRadius)
                    .min(Comparator.comparingDouble(PlayerDistance::distanceSquared))
                    .ifPresent(value -> acquire(runtime, value.player(), npcRef, npc,
                            commandBuffer, now));
        }
        if (movementTaskActive.test(current.id())) {
            runtime.approachAnchor = null;
            runtime.approachTarget = null;
        } else {
            updateApproach(runtime, npc, transform, world, commandBuffer);
        }
        applyLook(runtime, npcRef, npc, transform, commandBuffer);
    }

    public Optional<UUID> focusedNpcFor(PlayerRef player, String message) {
        UUID playerId = player.getUuid();
        String normalized = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        Vector3d playerPosition = player.getTransform().getPosition();
        Vector3d look = new Vector3d(player.getTransform().getDirection());
        if (look.lengthSquared() > 0.0001) {
            look.normalize();
        }
        return states.values().stream()
                .filter(runtime -> playerId.equals(runtime.state.focusedPlayerUuid()))
                .max(Comparator.comparingDouble(runtime -> {
                    NpcProfile current = profileRegistry == null ? profile.get()
                            : profileRegistry.byId(runtime.profileId)
                                    .orElseGet(profileRegistry::defaultProfile);
                    double explicitName = normalized.contains(
                            current.name().toLowerCase(java.util.Locale.ROOT)) ? 1000.0 : 0.0;
                    Vector3d direction = new Vector3d(runtime.position).sub(playerPosition);
                    double distance = Math.max(0.001, direction.length());
                    direction.div(distance);
                    double crosshair = Math.max(-1.0, look.dot(direction)) * 100.0;
                    return explicitName + crosshair - distance;
                }))
                .map(runtime -> runtime.profileId);
    }

    public Optional<NpcSocialAttentionState> stateForProfile(UUID profileId) {
        return states.values().stream().filter(value -> value.profileId.equals(profileId))
                .map(value -> value.state).findFirst();
    }

    /** Lock-free conservative capture gate used by the voice interceptor thread. */
    public boolean hasPotentialListener(UUID playerId) {
        return playerId != null && states.values().stream()
                .anyMatch(runtime -> runtime.potentialPlayers.contains(playerId));
    }

    public void disconnected(UUID playerId) {
        states.values().forEach(runtime -> {
            runtime.state.forget(playerId);
            if (playerId.equals(runtime.state.focusedPlayerUuid())) {
                runtime.state.release();
            }
        });
        nextCuriosityAt.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    public void setCuriosityConsumer(
            BiConsumer<PlayerRef, ConversationSession> curiosityConsumer) {
        this.curiosityConsumer = curiosityConsumer == null
                ? (ignoredPlayer, ignoredSession) -> { } : curiosityConsumer;
    }

    public void setFocusLostConsumer(BiConsumer<UUID, UUID> focusLostConsumer) {
        this.focusLostConsumer = focusLostConsumer == null
                ? (ignoredNpc, ignoredPlayer) -> { } : focusLostConsumer;
    }

    /** Clears only ephemeral world attention when an NPC entity is removed/refreshed. */
    public void entityRemoved(UUID profileId) {
        states.entrySet().removeIf(entry -> entry.getValue().profileId.equals(profileId));
        nextCuriosityAt.keySet().removeIf(key -> key.npcId().equals(profileId));
        diagnostics.accept("Social attention cleared npc=" + profileId
                + " reason=entity-removed");
    }

    public void entityRefreshed(UUID profileId, UUID retainedEntityId) {
        states.entrySet().removeIf(entry -> entry.getValue().profileId.equals(profileId)
                && !entry.getKey().equals(retainedEntityId));
        diagnostics.accept("Social attention refreshed npc=" + profileId
                + " retainedEntity=" + retainedEntityId);
    }

    private void acquire(
            RuntimeAttention runtime,
            PlayerRef player,
            Ref<EntityStore> npcRef,
            NPCEntity npc,
            CommandBuffer<EntityStore> commandBuffer,
            Instant now) {
        ConversationSession session = sessions.focus(
                runtime.profileId, player.getUuid(), now);
        runtime.state.focus(player.getUuid(), session.sessionId(), now);
        runtime.focusRef = player.getReference();
        runtime.approachAnchor = null;
        runtime.approachTarget = null;
        runtime.performance = null;
        if (npc.getRole() != null) {
            npc.getRole().setMarkedTarget(npcRef, commandBuffer,
                    TARGET_SLOT, runtime.focusRef);
        }
        player.sendMessage(Message.raw(profile.get().name()
                + " notices you and is listening."));
        diagnostics.accept("VOICE_LISTENING_ACQUIRED npc=" + runtime.profileId
                + " player=" + player.getUuid() + " session=" + session.sessionId());
        maybeInitiateCuriosity(player, session, now);
    }

    private void maybeInitiateCuriosity(
            PlayerRef player, ConversationSession session, Instant now) {
        NpcProfile current = profile.get();
        CuriosityKey key = new CuriosityKey(current.id(), player.getUuid());
        Instant blockedUntil = nextCuriosityAt.get(key);
        if (blockedUntil != null && now.isBefore(blockedUntil)) return;
        if (movementTaskActive.test(current.id()) || session.requestInFlight()
                || !session.recentTurns(1).isEmpty() || current.curiosity() < 0.55) {
            nextCuriosityAt.put(key, now.plusSeconds(CURIOSITY_RECONSIDER_SECONDS));
            return;
        }
        if (!shouldInitiateCuriosity(current.id(), player.getUuid(),
                current.curiosity(), now)) {
            nextCuriosityAt.put(key, now.plusSeconds(CURIOSITY_RECONSIDER_SECONDS));
            diagnostics.accept("NPC_CURIOSITY skipped npc=" + current.id()
                    + " player=" + player.getUuid() + " reason=bounded-random-check");
            return;
        }
        nextCuriosityAt.put(key, now.plusSeconds(CURIOSITY_COOLDOWN_SECONDS));
        diagnostics.accept("NPC_CURIOSITY triggered npc=" + current.id()
                + " player=" + player.getUuid() + " cooldownSeconds="
                + CURIOSITY_COOLDOWN_SECONDS);
        curiosityConsumer.accept(player, session);
    }

    /** Stable per-minute sampling prevents repeated re-entry from rerolling question spam. */
    public static boolean shouldInitiateCuriosity(
            UUID npcId, UUID playerId, double curiosity, Instant now) {
        if (npcId == null || playerId == null || now == null || curiosity < 0.55) return false;
        long minuteBucket = now.getEpochSecond() / 60L;
        long mixed = npcId.getMostSignificantBits() ^ npcId.getLeastSignificantBits()
                ^ Long.rotateLeft(playerId.getMostSignificantBits(), 17)
                ^ playerId.getLeastSignificantBits() ^ minuteBucket * 0x9E3779B97F4A7C15L;
        double roll = Math.floorMod(mixed, 10_000L) / 10_000.0;
        double chance = Math.min(0.60, 0.20 + curiosity * 0.40);
        return roll < chance;
    }

    private static void release(
            RuntimeAttention runtime,
            Ref<EntityStore> npcRef,
            NPCEntity npc,
            TransformComponent transform,
            CommandBuffer<EntityStore> commandBuffer) {
        runtime.state.release();
        runtime.focusRef = null;
        runtime.approachAnchor = null;
        runtime.approachTarget = null;
        if (npc.getRole() != null) {
            npc.getRole().setMarkedTarget(npcRef, commandBuffer, TARGET_SLOT, null);
            if (npc.getRole().getHeadSteering() != null) {
                npc.getRole().getHeadSteering().clear();
            }
        }
        HeadRotation head = commandBuffer.getComponent(
                runtime.npcRef, HeadRotation.getComponentType());
        if (head != null) {
            head.setRotation(transform.getRotation());
        }
        hold(npc, transform);
    }

    private static void updateApproach(
            RuntimeAttention runtime,
            NPCEntity npc,
            TransformComponent npcTransform,
            World world,
            CommandBuffer<EntityStore> commandBuffer) {
        if (runtime.focusRef == null || !runtime.focusRef.isValid()) {
            return;
        }
        TransformComponent playerTransform = commandBuffer.getComponent(
                runtime.focusRef, TransformComponent.getComponentType());
        PlayerRef player = commandBuffer.getComponent(
                runtime.focusRef, PlayerRef.getComponentType());
        if (playerTransform == null || player == null) {
            return;
        }
        Vector3d playerPosition = playerTransform.getPosition();
        double playerDistance = horizontalDistance(
                npcTransform.getPosition(), playerPosition);
        if (playerDistance >= CONVERSATION_MIN_DISTANCE
                && playerDistance <= CONVERSATION_MAX_DISTANCE) {
            hold(npc, npcTransform);
            return;
        }

        boolean playerMoved = runtime.approachAnchor == null
                || runtime.approachAnchor.distanceSquared(playerPosition)
                        >= PLAYER_REPATH_DISTANCE_SQUARED;
        if (runtime.approachTarget == null || playerMoved) {
            runtime.approachAnchor = new Vector3d(playerPosition);
            runtime.approachTarget = nearestWalkableConversationPoint(
                    world, playerPosition,
                    new Vector3d(player.getTransform().getDirection()));
        }
        if (runtime.approachTarget == null
                || npcTransform.getPosition().distanceSquared(runtime.approachTarget)
                        <= TARGET_REACHED_DISTANCE_SQUARED) {
            hold(npc, npcTransform);
            return;
        }
        npc.setLeashPoint(new Vector3d(runtime.approachTarget));
    }

    /** Preferred conversational point in front of the player's horizontal facing. */
    public static Vector3d conversationalPosition(
            Vector3d playerPosition, Vector3d playerForward) {
        Vector3d horizontal = horizontalForward(playerForward);
        return new Vector3d(playerPosition).add(horizontal.mul(CONVERSATION_DISTANCE));
    }

    private static Vector3d nearestWalkableConversationPoint(
            World world, Vector3d playerPosition, Vector3d playerForward) {
        Vector3d forward = horizontalForward(playerForward);
        double[] angleOffsets = {0, Math.PI / 4, -Math.PI / 4,
                Math.PI / 2, -Math.PI / 2, 3 * Math.PI / 4,
                -3 * Math.PI / 4, Math.PI};
        for (double angle : angleOffsets) {
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            Vector3d direction = new Vector3d(
                    forward.x * cos - forward.z * sin,
                    0,
                    forward.x * sin + forward.z * cos);
            Vector3d candidate = new Vector3d(playerPosition)
                    .add(direction.mul(CONVERSATION_DISTANCE));
            Vector3d walkable = walkableAt(world, candidate);
            if (walkable != null) {
                return walkable;
            }
        }
        return null;
    }

    private static Vector3d walkableAt(World world, Vector3d candidate) {
        return GroundPositionResolver.resolve(world, candidate).orElse(null);
    }

    private static boolean empty(BlockType block) {
        return block == null || block == BlockType.EMPTY
                || block.getMaterial() == BlockMaterial.Empty;
    }

    private static Vector3d horizontalForward(Vector3d forward) {
        Vector3d horizontal = new Vector3d(forward.x, 0, forward.z);
        return horizontal.lengthSquared() < 0.0001
                ? horizontal.set(0, 0, 1) : horizontal.normalize();
    }

    private static double horizontalDistance(Vector3d left, Vector3d right) {
        double dx = left.x - right.x;
        double dz = left.z - right.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void hold(NPCEntity npc, TransformComponent transform) {
        npc.getPathManager().setTransientPath(null);
        npc.setLeashPoint(new Vector3d(transform.getPosition()));
    }

    private void applyLook(
            RuntimeAttention runtime,
            Ref<EntityStore> npcRef,
            NPCEntity npc,
            TransformComponent npcTransform,
            CommandBuffer<EntityStore> commandBuffer) {
        runtime.npcRef = npcRef;
        if (runtime.focusRef == null || !runtime.focusRef.isValid()
                || npc.getRole() == null || npc.getRole().getHeadSteering() == null) {
            return;
        }
        TransformComponent target = commandBuffer.getComponent(
                runtime.focusRef, TransformComponent.getComponentType());
        HeadRotation head = commandBuffer.getComponent(npcRef, HeadRotation.getComponentType());
        if (target == null || head == null) {
            return;
        }
        Vector3d from = npcTransform.getPosition();
        Vector3d to = target.getPosition();
        PerformanceSequence performance = runtime.performance;
        if (performance != null) {
            long elapsedMs = Math.max(0,
                    (System.nanoTime() - performance.startedNanos()) / 1_000_000L);
            int index = (int) (elapsedMs / 700L);
            if (index < performance.points().size()) {
                to = performance.points().get(index);
                npc.getRole().setMarkedTarget(npcRef, commandBuffer, TARGET_SLOT, null);
                if (performance.playEmote() && !performance.emotePlayed()) {
                    AnimationUtils.playAnimation(npcRef, AnimationSlot.Emote,
                            "Characters/Animations/Emote/Shrug.blockyanim", commandBuffer);
                    runtime.performance = performance.withEmotePlayed();
                }
            } else {
                runtime.performance = null;
                runtime.performanceCompletedMillis = elapsedMs;
                diagnostics.accept("Social performance complete npc=" + runtime.profileId
                        + " durationMs=" + elapsedMs + " returnedLookToPlayer=true");
            }
        }
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        float yaw = PhysicsMath.normalizeTurnAngle(
                PhysicsMath.headingFromDirection(dx, dz));
        float pitch = PhysicsMath.pitchFromDirection(dx, dy, dz);
        if (runtime.performance == null) {
            npc.getRole().setMarkedTarget(npcRef, commandBuffer,
                    TARGET_SLOT, runtime.focusRef);
        }
        npc.getRole().getHeadSteering().clearTranslation()
                .setYaw(yaw).setPitch(pitch).setRelativeTurnSpeed(1.0);
        Rotation3f rotation = new Rotation3f(head.getRotation());
        rotation.setYaw(yaw);
        rotation.setPitch(pitch);
        head.setRotation(rotation);
    }

    private static UUID worldId(World world) {
        for (PlayerRef player : world.getPlayerRefs()) {
            if (player != null && player.getWorldUuid() != null) {
                return player.getWorldUuid();
            }
        }
        return null;
    }

    private static List<Vector3d> scanPoints(
            Vector3d origin, EnvironmentSnapshot environment) {
        List<EnvironmentFeature> features = environment == null ? List.of()
                : java.util.stream.Stream.concat(environment.importantObjects().stream(),
                        environment.structuralFeatures().stream())
                        .filter(feature -> !"player".equalsIgnoreCase(feature.category()))
                        .limit(3).toList();
        List<Vector3d> points = new ArrayList<>();
        for (EnvironmentFeature feature : features) {
            points.add(pointInDirection(origin, feature.direction(), feature.distanceMeters()));
        }
        if (points.size() < 2) {
            points.add(new Vector3d(origin).add(4.0, 1.5, 0));
        }
        if (points.size() < 2) {
            points.add(new Vector3d(origin).add(-3.0, 1.5, 2.0));
        }
        return points.stream().limit(3).toList();
    }

    private static Vector3d pointInDirection(Vector3d origin, String direction, double distance) {
        double angle = switch (direction == null ? "" : direction.toLowerCase(
                java.util.Locale.ROOT)) {
            case "north" -> 0;
            case "northeast" -> Math.PI / 4;
            case "east" -> Math.PI / 2;
            case "southeast" -> 3 * Math.PI / 4;
            case "south" -> Math.PI;
            case "southwest" -> 5 * Math.PI / 4;
            case "west" -> 3 * Math.PI / 2;
            case "northwest" -> 7 * Math.PI / 4;
            default -> Math.PI / 2;
        };
        double range = Math.max(3.0, Math.min(12.0, distance));
        return new Vector3d(origin.x + Math.sin(angle) * range,
                origin.y + 1.5, origin.z - Math.cos(angle) * range);
    }

    private record PlayerDistance(PlayerRef player, double distanceSquared) {
    }

    private record CuriosityKey(UUID npcId, UUID playerId) { }

    private static final class RuntimeAttention {
        private final UUID profileId;
        private final NpcSocialAttentionState state;
        private final Vector3d position = new Vector3d();
        private final Set<UUID> potentialPlayers = ConcurrentHashMap.newKeySet();
        private UUID worldId;
        private Ref<EntityStore> npcRef;
        private Ref<EntityStore> focusRef;
        private Vector3d approachAnchor;
        private Vector3d approachTarget;
        private PerformanceSequence performance;
        private long performanceCompletedMillis;

        private RuntimeAttention(UUID profileId) {
            this.profileId = profileId;
            state = new NpcSocialAttentionState(profileId);
        }
    }

    private record PerformanceSequence(
            List<Vector3d> points, long startedNanos, boolean playEmote, boolean emotePlayed) {
        private PerformanceSequence withEmotePlayed() {
            return new PerformanceSequence(points, startedNanos, playEmote, true);
        }
    }
}
