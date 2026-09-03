package com.inigmasgames.persistentnpcs.autonomy;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.builtin.weather.resources.WeatherResource;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.util.PhysicsMath;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.inigmasgames.persistentnpcs.hytale.GroundPositionResolver;
import com.inigmasgames.persistentnpcs.memory.MemoryRecord;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.memory.MemoryType;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.cognition.NpcEmotionStore;
import com.inigmasgames.persistentnpcs.cognition.NpcEmotionalState;
import com.inigmasgames.persistentnpcs.economy.ObligationStore;
import com.hypixel.hytale.protocol.AnimationSlot;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.joml.Vector3d;

/**
 * Grounded perceive -> attend -> choose -> act -> observe loop.
 * Common opportunities are selected deterministically; no LLM runs from this tick path.
 */
public final class HytaleAutonomousCognitionController {
    private static final GroundedSemanticClassifier CLASSIFIER =
            new GroundedSemanticClassifier();
    private static final int SENSOR_RADIUS = 8;
    private static final double ARRIVAL_DISTANCE_SQUARED = 1.1 * 1.1;
    private static final double UTILITY_THRESHOLD = 0.62;
    private static final Duration TARGET_COOLDOWN = Duration.ofMinutes(20);
    private static final Duration OBSERVATION_DURATION = Duration.ofSeconds(3);
    private static final Duration INTENT_TIMEOUT = Duration.ofSeconds(45);
    private final NpcCognitionStateStore states;
    private final MemoryStore memories;
    private final AffordanceRegistry affordances;
    private final NpcReflectionService reflections;
    private final AttentionScorer attentionScorer = new AttentionScorer();
    private final NpcEmotionStore emotions;
    private final ObligationStore obligations;
    private final AgentOperationStore operations;
    private final Consumer<String> diagnostics;

    public HytaleAutonomousCognitionController(
            NpcCognitionStateStore states,
            MemoryStore memories,
            AffordanceRegistry affordances,
            Consumer<String> diagnostics) {
        this(states, memories, affordances, diagnostics, null, null, null);
    }

    public HytaleAutonomousCognitionController(
            NpcCognitionStateStore states,
            MemoryStore memories,
            AffordanceRegistry affordances,
            Consumer<String> diagnostics,
            NpcEmotionStore emotions,
            ObligationStore obligations,
            AgentOperationStore operations) {
        this.states = states;
        this.memories = memories;
        this.affordances = affordances;
        this.reflections = new NpcReflectionService(memories);
        this.emotions = emotions;
        this.obligations = obligations;
        this.operations = operations;
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
    }

    /** Returns true only while this controller owns locomotion. */
    public boolean tick(
            NpcProfile profile,
            UUID worldId,
            Ref<EntityStore> npcRef,
            NPCEntity npc,
            TransformComponent transform,
            World world,
            Store<EntityStore> store,
            boolean blockedByHigherPriorityWork,
            CommandBuffer<EntityStore> commandBuffer,
            Instant now) {
        NpcCognitionRuntimeState state = states.get(profile.id());
        PersistentNpcIntent intent = state.activeIntent();
        if (blockedByHigherPriorityWork) {
            if (intent != null) {
                interrupt(state, intent, "higher-priority conversation, task, or emergency", now);
            }
            return false;
        }
        if (intent != null) {
            if (operations != null && (intent.operationId() == null
                    || !operations.ownsActive(intent.operationId(), profile.id(), now))) {
                try {
                    AgentOperation operation = operations.claim("AUTONOMOUS_" + intent.actionId(),
                            java.util.Set.of(profile.id()), intent.reason(), now, INTENT_TIMEOUT);
                    intent = intent.withOperation(operation.operationId());
                    updateIntent(state, intent);
                    state = states.get(profile.id());
                } catch (IllegalStateException busy) {
                    interrupt(state, intent, "another AgentOperation owns this NPC", now);
                    return false;
                }
            }
            return execute(state, intent, worldId, npcRef, npc, transform, world,
                    store, commandBuffer, now);
        }
        if (state.lastEvaluatedAt() != null && Duration.between(
                state.lastEvaluatedAt(), now).toMillis() < state.simulationTier().intervalMillis()) {
            return false;
        }
        evaluate(profile, state, worldId, npcRef, npc, transform.getPosition(), world,
                store, commandBuffer, now);
        return states.get(profile.id()).activeIntent() != null;
    }

    public String debug(UUID npcId) {
        NpcCognitionRuntimeState state = states.get(npcId);
        PersistentNpcIntent intent = state.activeIntent();
        return """
                COGNITION DEBUG
                simulationTier=%s
                currentNeed=%s
                currentGoal=%s
                activity=%s
                why=%s
                authoritativeAttention=%s
                rejectedCandidates=%s
                lastActionResult=%s
                lastEvaluatedAt=%s
                lastReflectionAt=%s
                """.formatted(state.simulationTier(), state.currentNeed(), state.currentGoal(),
                intent == null ? "IDLE" : intent.activity() + "/" + intent.intentType(),
                state.attentionReason(), state.attendedWorldFacts(),
                state.rejectedCandidates(), state.lastActionResult(),
                state.lastEvaluatedAt(), state.lastReflectionAt()).strip();
    }

    private void evaluate(NpcProfile profile, NpcCognitionRuntimeState state, UUID worldId,
            Ref<EntityStore> npcRef, NPCEntity npc, Vector3d origin, World world,
            Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer, Instant now) {
        List<GroundedStimulus> facts = new ArrayList<>(
                scanGroundedBlocks(worldId, origin, world, now));
        facts.addAll(scanGroundedEntities(worldId, origin, npcRef, store, commandBuffer, now));
        GroundedStimulus weather = scanWeather(worldId, origin, npc, store, now);
        if (weather != null) facts.add(weather);
        if (facts.isEmpty()) facts.addAll(ambientStimuli(profile, worldId, origin, now));
        facts.sort(Comparator.comparingDouble(GroundedStimulus::distanceMeters));
        diagnostics.accept("PERCEPTION npc=" + profile.id() + " groundedFacts="
                + facts.stream().limit(12).map(fact -> fact.semanticType() + ":"
                        + fact.assetId() + "@" + "%.1fm".formatted(fact.distanceMeters()))
                        .toList());
        List<ScoredOpportunity> candidates = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        NpcEmotionalState mood = emotions == null ? null : emotions.get(profile.id(), now);
        int obligationCount = obligations == null ? 0 : obligations.activeFor(profile.id()).size();
        for (GroundedStimulus fact : facts) {
            List<String> actions = affordances.forType(fact.semanticType());
            Instant cooldown = state.cooldowns().get(fact.targetId());
            if (actions.isEmpty()) {
                rejected.add(fact.assetId() + ": no registered affordance");
                continue;
            }
            boolean repeated = cooldown != null && now.isBefore(cooldown);
            AttentionScore score = attentionScorer.score(
                    profile, mood, fact, repeated, obligationCount);
            diagnostics.accept("ATTENTION npc=" + profile.id() + " target="
                    + fact.targetId() + " " + score.compact());
            if (repeated || score.total() < UTILITY_THRESHOLD) {
                rejected.add(fact.assetId() + (repeated ? ": repetition cooldown"
                        : ": attention %.2f below %.2f"
                                .formatted(score.total(), UTILITY_THRESHOLD)));
                continue;
            }
            candidates.add(new ScoredOpportunity(fact, actions.getFirst(), score));
        }
        ScoredOpportunity chosen = candidates.stream()
                .max(Comparator.comparingDouble(value -> value.score().total())).orElse(null);
        PersistentNpcIntent intent = chosen == null ? null : new PersistentNpcIntent(
                UUID.randomUUID(), intentType(chosen.action()), chosen.action(), chosen.fact(),
                startsInPlace(chosen.action()) ? CognitionActivity.OBSERVING
                        : CognitionActivity.APPROACHING,
                chosen.score().total(),
                "current authoritative " + chosen.fact().semanticType().toLowerCase(Locale.ROOT)
                        + " matched profile attention and available " + chosen.action()
                        + " affordance; " + chosen.score().compact(),
                now, now, 0, "");
        if (intent != null && operations != null) {
            try {
                AgentOperation operation = operations.claim("AUTONOMOUS_" + intent.actionId(),
                        java.util.Set.of(profile.id()), intent.reason(), now, INTENT_TIMEOUT);
                intent = intent.withOperation(operation.operationId());
            } catch (IllegalStateException busy) {
                rejected.add(chosen.fact().assetId() + ": AgentOperation busy");
                intent = null;
            }
        }
        NpcCognitionRuntimeState updated = new NpcCognitionRuntimeState(profile.id(),
                state.simulationTier(), intent, facts.stream().limit(8).toList(),
                state.cooldowns(), state.currentNeed(),
                intent == null ? "remain available near home" : "inspect "
                        + chosen.fact().assetId(),
                intent == null ? "no opportunity exceeded utility threshold" : intent.reason(),
                rejected.stream().limit(8).toList(), state.lastActionResult(), now,
                state.lastReflectionAt());
        states.put(updated);
        diagnostics.accept("DECISION npc=" + profile.id() + " selected="
                + (intent == null ? "NONE" : intent.actionId() + ":" + intent.target().targetId())
                + " rejected=" + updated.rejectedCandidates());
    }

    private boolean execute(NpcCognitionRuntimeState state, PersistentNpcIntent intent,
            UUID currentWorldId,
            Ref<EntityStore> npcRef, NPCEntity npc, TransformComponent transform, World world,
            Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer, Instant now) {
        if (Duration.between(intent.startedAt(), now).compareTo(INTENT_TIMEOUT) > 0) {
            finish(state, intent, false, "intent timed out", now);
            return false;
        }
        if (!java.util.Objects.equals(intent.target().worldId(), currentWorldId)
                || !stillExists(intent.target(), world, store, npc)) {
            finish(state, intent, false, "authoritative target no longer exists", now);
            return false;
        }
        Vector3d object = currentTargetPosition(intent.target(), world, store);
        if (intent.activity() == CognitionActivity.APPROACHING) {
            Vector3d approach = approachPoint(transform.getPosition(), object, world);
            if (transform.getPosition().distanceSquared(approach) > ARRIVAL_DISTANCE_SQUARED) {
                npc.setLeashPoint(approach);
                diagnostics.accept("ACTION npc=" + state.npcId() + " intent="
                        + intent.intentId() + " action=NAVIGATE target="
                        + intent.target().targetId());
                return true;
            }
            hold(npc, transform.getPosition());
            lookAt(npcRef, npc, transform.getPosition(), object, commandBuffer);
            updateIntent(state, intent.withState(CognitionActivity.OBSERVING, now, 1,
                    "arrived at validated target"));
            return true;
        }
        if (intent.activity() == CognitionActivity.OBSERVING) {
            hold(npc, transform.getPosition());
            if (!isAmbient(intent.actionId())) {
                lookAt(npcRef, npc, transform.getPosition(), object, commandBuffer);
            } else if (intent.planStep() == 0) {
                playAmbient(intent.actionId(), npcRef, commandBuffer);
                updateIntent(state, intent.withState(CognitionActivity.OBSERVING,
                        now, 1, "ambient performance started"));
                diagnostics.accept("ACTION npc=" + state.npcId() + " intent="
                        + intent.intentId() + " action=" + intent.actionId());
                return true;
            }
            if (Duration.between(intent.stateSince(), now).compareTo(OBSERVATION_DURATION) < 0) {
                return true;
            }
            finish(state, intent, true, isAmbient(intent.actionId())
                    ? "completed ambient " + intent.actionId().toLowerCase(Locale.ROOT)
                    : "observed authoritative " + intent.target().assetId(), now);
        }
        return false;
    }

    private void finish(NpcCognitionRuntimeState state, PersistentNpcIntent intent,
            boolean success, String result, Instant now) {
        Map<String, Instant> cooldowns = new LinkedHashMap<>(state.cooldowns());
        cooldowns.put(intent.target().targetId(), now.plus(TARGET_COOLDOWN));
        boolean meaningful = success && !isAmbient(intent.actionId())
                && !intent.target().semanticType().equals("NPC");
        if (meaningful) {
            GroundedStimulus fact = intent.target();
            memories.append(new MemoryRecord(UUID.randomUUID(), state.npcId(), null, now,
                    MemoryType.EPISODIC, 0.58,
                    "Investigated " + fact.assetId() + " at " + location(fact),
                    1.0, fact.source(), List.of(), location(fact),
                    "I noticed and examined a real " + fact.semanticType().toLowerCase(Locale.ROOT)
                            + " near my home."));
            diagnostics.accept("MEMORY npc=" + state.npcId() + " source=" + fact.source()
                    + " target=" + fact.targetId() + " importance=0.58");
        }
        Instant lastReflection = state.lastReflectionAt();
        if (success && reflections.maybeReflect(state.npcId(), lastReflection, now)) {
            lastReflection = now;
        }
        states.put(new NpcCognitionRuntimeState(state.npcId(), state.simulationTier(), null,
                state.attendedWorldFacts(), Map.copyOf(cooldowns), state.currentNeed(),
                "return to normal schedule", state.attentionReason(),
                state.rejectedCandidates(), (success ? "SUCCESS: " : "FAILED: ") + result,
                state.lastEvaluatedAt(), lastReflection));
        if (operations != null && intent.operationId() != null) {
            operations.complete(intent.operationId(), success, result);
        }
        diagnostics.accept("RESULT npc=" + state.npcId()
                + " intent=" + intent.intentId() + " success=" + success
                + " result=" + result);
    }

    private void interrupt(NpcCognitionRuntimeState state, PersistentNpcIntent intent,
            String reason, Instant now) {
        states.put(new NpcCognitionRuntimeState(state.npcId(), state.simulationTier(), null,
                state.attendedWorldFacts(), state.cooldowns(), state.currentNeed(),
                "handle higher-priority activity", "interrupted: " + reason,
                state.rejectedCandidates(), "INTERRUPTED: " + reason,
                now, state.lastReflectionAt()));
        if (operations != null && intent.operationId() != null) {
            operations.complete(intent.operationId(), false, "interrupted: " + reason);
        }
        diagnostics.accept("COGNITION_INTERRUPTED npc=" + state.npcId()
                + " intent=" + intent.intentId() + " reason=" + reason);
    }

    private void updateIntent(NpcCognitionRuntimeState state, PersistentNpcIntent intent) {
        states.put(new NpcCognitionRuntimeState(state.npcId(), state.simulationTier(), intent,
                state.attendedWorldFacts(), state.cooldowns(), state.currentNeed(),
                state.currentGoal(), state.attentionReason(), state.rejectedCandidates(),
                intent.lastResult(), state.lastEvaluatedAt(), state.lastReflectionAt()));
    }

    private static List<GroundedStimulus> scanGroundedBlocks(
            UUID worldId, Vector3d origin, World world, Instant now) {
        List<GroundedStimulus> result = new ArrayList<>();
        int ox = (int) Math.floor(origin.x);
        int oy = (int) Math.floor(origin.y);
        int oz = (int) Math.floor(origin.z);
        for (int x = ox - SENSOR_RADIUS; x <= ox + SENSOR_RADIUS; x++) {
            for (int z = oz - SENSOR_RADIUS; z <= oz + SENSOR_RADIUS; z++) {
                if (Math.hypot(x + 0.5 - origin.x, z + 0.5 - origin.z) > SENSOR_RADIUS) continue;
                WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
                if (chunk == null) continue;
                for (int y = oy - 3; y <= oy + 5; y++) {
                    BlockType block = chunk.getBlockType(x, y, z);
                    if (block == null || block == BlockType.EMPTY) continue;
                    String type = CLASSIFIER.classifyBlock(block);
                    if (type.isBlank()) continue;
                    double px = x + 0.5, py = y + 0.5, pz = z + 0.5;
                    result.add(new GroundedStimulus(
                            "block:" + worldId + ":" + x + ":" + y + ":" + z,
                            type, block.getId(), worldId, px, py, pz,
                            origin.distance(px, py, pz), "HYTALE_BLOCK_STATE", now));
                }
            }
        }
        return result.stream().sorted(Comparator.comparingDouble(
                GroundedStimulus::distanceMeters)).limit(24).toList();
    }

    private static List<GroundedStimulus> scanGroundedEntities(
            UUID worldId, Vector3d origin, Ref<EntityStore> self,
            Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer, Instant now) {
        List<GroundedStimulus> result = new ArrayList<>();
        Query<EntityStore> query = Archetype.of(NPCEntity.getComponentType(),
                TransformComponent.getComponentType(), UUIDComponent.getComponentType());
        store.forEachChunk(query, (chunk, buffer) -> {
            for (int index = 0; index < chunk.size(); index++) {
                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                if (ref.equals(self)) continue;
                NPCEntity entity = chunk.getComponent(index, NPCEntity.getComponentType());
                TransformComponent transform = chunk.getComponent(
                        index, TransformComponent.getComponentType());
                UUIDComponent uuid = chunk.getComponent(index, UUIDComponent.getComponentType());
                if (entity == null || transform == null || uuid == null) continue;
                double distance = origin.distance(transform.getPosition());
                if (distance > 12.0) continue;
                String asset = safe(entity.getNPCTypeId());
                String type = CLASSIFIER.classifyEntity(asset);
                Vector3d position = transform.getPosition();
                result.add(new GroundedStimulus("entity:" + uuid.getUuid(), type, asset,
                        worldId, position.x, position.y, position.z, distance,
                        "HYTALE_ENTITY_STATE", now));
            }
        });
        return result.stream().sorted(Comparator.comparingDouble(
                GroundedStimulus::distanceMeters)).limit(12).toList();
    }

    private static GroundedStimulus scanWeather(UUID worldId, Vector3d origin,
            NPCEntity npc, Store<EntityStore> store, Instant now) {
        WeatherResource resource = store.getResource(WeatherResource.getResourceType());
        if (resource == null) return null;
        int index = resource.getWeatherIndexForEnvironment(npc.getEnvironment());
        Weather weather = Weather.getAssetMap().getAssetOrDefault(index, Weather.UNKNOWN);
        if (weather == null || weather == Weather.UNKNOWN || weather.getId() == null) return null;
        String id = weather.getId();
        String type = CLASSIFIER.classifyWeather(id);
        if (type.isBlank()) return null;
        return new GroundedStimulus("weather:" + worldId + ":" + id, type, id, worldId,
                origin.x, origin.y, origin.z, 0, "HYTALE_WEATHER_STATE", now);
    }

    private static List<GroundedStimulus> ambientStimuli(
            NpcProfile profile, UUID worldId, Vector3d origin, Instant now) {
        long bucket = now.getEpochSecond() / 300;
        boolean hum = Math.floorMod(profile.id().getLeastSignificantBits() ^ bucket, 2) == 0;
        String type = hum ? "AMBIENT_HUM" : "AMBIENT_STRETCH";
        return List.of(new GroundedStimulus("self:" + profile.id() + ":" + type,
                type, type.toLowerCase(Locale.ROOT), worldId, origin.x, origin.y, origin.z,
                0, "NPC_IDLE_SELF_STATE", now));
    }

    private static boolean stillExists(
            GroundedStimulus fact, World world, Store<EntityStore> store, NPCEntity npc) {
        if (fact.source().equals("NPC_IDLE_SELF_STATE")) return true;
        if (fact.source().equals("HYTALE_WEATHER_STATE")) {
            GroundedStimulus current = scanWeather(fact.worldId(),
                    new Vector3d(fact.x(), fact.y(), fact.z()), npc, store, Instant.now());
            return current != null && current.assetId().equals(fact.assetId());
        }
        if (fact.source().equals("HYTALE_ENTITY_STATE")) {
            try {
                UUID id = UUID.fromString(fact.targetId().substring("entity:".length()));
                Ref<EntityStore> ref = world.getEntityRef(id);
                if (ref == null || !ref.isValid()) return false;
                NPCEntity current = store.getComponent(ref, NPCEntity.getComponentType());
                return current != null && fact.assetId().equals(current.getNPCTypeId());
            } catch (RuntimeException invalid) {
                return false;
            }
        }
        int x = (int) Math.floor(fact.x());
        int y = (int) Math.floor(fact.y());
        int z = (int) Math.floor(fact.z());
        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) return false;
        BlockType block = chunk.getBlockType(x, y, z);
        return block != null && block != BlockType.EMPTY && fact.assetId().equals(block.getId());
    }

    private static Vector3d currentTargetPosition(
            GroundedStimulus fact, World world, Store<EntityStore> store) {
        if (fact.source().equals("HYTALE_ENTITY_STATE")) {
            try {
                UUID id = UUID.fromString(fact.targetId().substring("entity:".length()));
                Ref<EntityStore> ref = world.getEntityRef(id);
                TransformComponent transform = ref == null ? null
                        : store.getComponent(ref, TransformComponent.getComponentType());
                if (transform != null) return new Vector3d(transform.getPosition());
            } catch (RuntimeException ignored) { }
        }
        return new Vector3d(fact.x(), fact.y(), fact.z());
    }

    private static boolean startsInPlace(String action) {
        return isAmbient(action) || action.equals("WATCH_RAIN")
                || action.equals("OBSERVE_DANGER");
    }

    private static boolean isAmbient(String action) {
        return action.equals("HUM") || action.equals("STRETCH");
    }

    private static String intentType(String action) {
        return isAmbient(action) ? "AMBIENT_SELF_ACTION"
                : action.equals("WATCH_RAIN") || action.equals("OBSERVE_DANGER")
                        ? "OBSERVE_WEATHER" : "INVESTIGATE_WORLD_OBJECT";
    }

    private static void playAmbient(String action, Ref<EntityStore> npcRef,
            CommandBuffer<EntityStore> commandBuffer) {
        if (action.equals("STRETCH")) {
            AnimationUtils.playAnimation(npcRef, AnimationSlot.Emote,
                    "Characters/Animations/Emote/Yawn.blockyanim", commandBuffer);
        }
        // HUM is deliberately non-verbal: it reserves a short ambient performance without
        // injecting synthetic dialogue or disturbing the established voice pipeline.
    }

    private static Vector3d approachPoint(Vector3d from, Vector3d target, World world) {
        Vector3d offset = new Vector3d(from.x - target.x, 0, from.z - target.z);
        if (offset.lengthSquared() < 0.01) offset.set(1, 0, 0);
        offset.normalize(1.5);
        Vector3d proposed = new Vector3d(target).add(offset);
        return GroundPositionResolver.resolve(world, proposed).orElse(proposed);
    }

    private static void lookAt(Ref<EntityStore> ref, NPCEntity npc, Vector3d from,
            Vector3d target, CommandBuffer<EntityStore> commandBuffer) {
        HeadRotation head = commandBuffer.getComponent(ref, HeadRotation.getComponentType());
        if (head == null || npc.getRole() == null || npc.getRole().getHeadSteering() == null) return;
        double dx = target.x - from.x, dy = target.y - from.y, dz = target.z - from.z;
        float yaw = PhysicsMath.normalizeTurnAngle(PhysicsMath.headingFromDirection(dx, dz));
        float pitch = PhysicsMath.pitchFromDirection(dx, dy, dz);
        npc.getRole().getHeadSteering().clearTranslation().setYaw(yaw).setPitch(pitch)
                .setRelativeTurnSpeed(0.8);
        Rotation3f rotation = new Rotation3f(head.getRotation());
        rotation.setYaw(yaw);
        rotation.setPitch(pitch);
        head.setRotation(rotation);
    }

    private static void hold(NPCEntity npc, Vector3d position) {
        npc.getPathManager().setTransientPath(null);
        npc.setLeashPoint(new Vector3d(position));
    }

    private static String location(GroundedStimulus fact) {
        return "%s:%.1f,%.1f,%.1f".formatted(
                fact.worldId(), fact.x(), fact.y(), fact.z());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private record ScoredOpportunity(
            GroundedStimulus fact, String action, AttentionScore score) { }
}
