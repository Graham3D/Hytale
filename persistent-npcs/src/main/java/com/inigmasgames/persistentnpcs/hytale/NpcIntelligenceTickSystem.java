package com.inigmasgames.persistentnpcs.hytale;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import com.inigmasgames.persistentnpcs.social.NpcSocialAttentionService;
import com.inigmasgames.persistentnpcs.task.NpcTaskScheduler;
import com.inigmasgames.persistentnpcs.voice.HytaleSpatialVoiceAdapter;
import com.inigmasgames.persistentnpcs.home.NpcHomeBehaviorController;
import com.inigmasgames.persistentnpcs.profile.AppearanceRepository;
import com.inigmasgames.persistentnpcs.autonomy.HytaleAutonomousCognitionController;
import com.inigmasgames.persistentnpcs.background.BackgroundLifeSimulator;
import com.inigmasgames.persistentnpcs.background.BackgroundActivityType;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

public final class NpcIntelligenceTickSystem extends EntityTickingSystem<EntityStore> {
    private final Query<EntityStore> query = Archetype.of(
            NPCEntity.getComponentType(),
            TransformComponent.getComponentType(),
            UUIDComponent.getComponentType());
    private final Supplier<NpcProfile> profile;
    private final NpcProfileRegistry profileRegistry;
    private final NpcSocialAttentionService attention;
    private final NpcTaskScheduler tasks;
    private final HytaleSpatialVoiceAdapter voice;
    private final NpcRuntimeRegistry runtimes;
    private final NpcHomeBehaviorController home;
    private final AppearanceRepository appearances;
    private final HytaleAutonomousCognitionController autonomousCognition;
    private final BackgroundLifeSimulator backgroundLife;
    private final ImmersiveNpcRoleService nativeRoles;
    private final java.util.function.DoubleConsumer serverFrameObserver;
    private final Set<UUID> appearanceApplied = ConcurrentHashMap.newKeySet();

    public NpcIntelligenceTickSystem(
            Supplier<NpcProfile> profile,
            NpcSocialAttentionService attention,
            NpcTaskScheduler tasks) {
        this(profile, attention, tasks, null, null, null, null, null, null);
    }

    public NpcIntelligenceTickSystem(
            Supplier<NpcProfile> profile,
            NpcSocialAttentionService attention,
            NpcTaskScheduler tasks,
            HytaleSpatialVoiceAdapter voice) {
        this(profile, attention, tasks, voice, null, null, null, null, null);
    }

    public NpcIntelligenceTickSystem(
            Supplier<NpcProfile> profile,
            NpcSocialAttentionService attention,
            NpcTaskScheduler tasks,
            HytaleSpatialVoiceAdapter voice,
            NpcRuntimeRegistry runtimes,
            NpcHomeBehaviorController home,
            AppearanceRepository appearances) {
        this(profile, attention, tasks, voice, runtimes, home, appearances, null, null);
    }

    public NpcIntelligenceTickSystem(
            Supplier<NpcProfile> profile,
            NpcSocialAttentionService attention,
            NpcTaskScheduler tasks,
            HytaleSpatialVoiceAdapter voice,
            NpcRuntimeRegistry runtimes,
            NpcHomeBehaviorController home,
            AppearanceRepository appearances,
            HytaleAutonomousCognitionController autonomousCognition) {
        this(profile, attention, tasks, voice, runtimes, home, appearances,
                autonomousCognition, null);
    }

    public NpcIntelligenceTickSystem(
            Supplier<NpcProfile> profile,
            NpcSocialAttentionService attention,
            NpcTaskScheduler tasks,
            HytaleSpatialVoiceAdapter voice,
            NpcRuntimeRegistry runtimes,
            NpcHomeBehaviorController home,
            AppearanceRepository appearances,
            HytaleAutonomousCognitionController autonomousCognition,
            BackgroundLifeSimulator backgroundLife) {
        this.profile = profile;
        this.profileRegistry = null;
        this.attention = attention;
        this.tasks = tasks;
        this.voice = voice;
        this.runtimes = runtimes;
        this.home = home;
        this.appearances = appearances;
        this.autonomousCognition = autonomousCognition;
        this.backgroundLife = backgroundLife;
        this.nativeRoles = null;
        this.serverFrameObserver = ignored -> { };
    }

    public NpcIntelligenceTickSystem(
            NpcProfileRegistry profiles,
            NpcSocialAttentionService attention,
            NpcTaskScheduler tasks,
            HytaleSpatialVoiceAdapter voice,
            NpcRuntimeRegistry runtimes,
            NpcHomeBehaviorController home,
            AppearanceRepository appearances,
            HytaleAutonomousCognitionController autonomousCognition,
            BackgroundLifeSimulator backgroundLife,
            ImmersiveNpcRoleService nativeRoles) {
        this(profiles, attention, tasks, voice, runtimes, home, appearances,
                autonomousCognition, backgroundLife, nativeRoles, ignored -> { });
    }

    public NpcIntelligenceTickSystem(
            NpcProfileRegistry profiles,
            NpcSocialAttentionService attention,
            NpcTaskScheduler tasks,
            HytaleSpatialVoiceAdapter voice,
            NpcRuntimeRegistry runtimes,
            NpcHomeBehaviorController home,
            AppearanceRepository appearances,
            HytaleAutonomousCognitionController autonomousCognition,
            BackgroundLifeSimulator backgroundLife,
            ImmersiveNpcRoleService nativeRoles,
            java.util.function.DoubleConsumer serverFrameObserver) {
        this.profileRegistry = profiles;
        this.profile = profiles::defaultProfile;
        this.attention = attention;
        this.tasks = tasks;
        this.voice = voice;
        this.runtimes = runtimes;
        this.home = home;
        this.appearances = appearances;
        this.autonomousCognition = autonomousCognition;
        this.backgroundLife = backgroundLife;
        this.nativeRoles = nativeRoles;
        this.serverFrameObserver = serverFrameObserver == null ? ignored -> { }
                : serverFrameObserver;
    }

    @Override
    public void tick(
            float delta,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
        if (npc == null || !HytaleNpcAdapter.isManagedRole(npc.getNPCTypeId())) {
            return;
        }
        serverFrameObserver.accept(delta);
        TransformComponent transform = chunk.getComponent(
                index, TransformComponent.getComponentType());
        UUIDComponent uuid = chunk.getComponent(index, UUIDComponent.getComponentType());
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        if (transform == null || uuid == null) {
            return;
        }
        NpcProfile currentProfile = resolveProfile(
                ref, uuid.getUuid(), npc.getNPCTypeId(), commandBuffer);
        if (appearances != null && !appearanceApplied.contains(uuid.getUuid())
                && (appearances.queueApply(currentProfile.name(), ref, npc, commandBuffer)
                        || appearances.queueApply(currentProfile.appearancePreset(), ref,
                                npc, commandBuffer))) {
                appearanceApplied.add(uuid.getUuid());
        }
        UUID worldId = worldId(store);
        if (runtimes != null && !runtimes.registerIfAbsent(
                currentProfile.id(), worldId, uuid.getUuid(), entityId -> {
                    Ref<EntityStore> existing = store.getExternalData().getRefFromUUID(entityId);
                    return existing != null && existing.isValid();
                })) {
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
            return;
        }
        if (nativeRoles != null && nativeRoles.isManagedRole(npc.getNPCTypeId())) {
            commandBuffer.putComponent(ref, PersistentDisplayName.getComponentType(),
                    new PersistentDisplayName(Message.raw(currentProfile.name())));
            commandBuffer.putComponent(ref, Nameplate.getComponentType(),
                    new Nameplate(currentProfile.name()));
        }
        boolean listening = attention.stateForProfile(currentProfile.id())
                .map(state -> state.focusedPlayerUuid() != null)
                .orElse(false);
        boolean backgroundWork = false;
        if (backgroundLife != null) {
            Vector3d position = transform.getPosition();
            WorldTimeResource gameClock = store.getResource(WorldTimeResource.getResourceType());
            Instant gameNow = gameClock == null || gameClock.getGameDateTime() == null
                    ? null : gameClock.getGameDateTime().toInstant(java.time.ZoneOffset.UTC);
            if (hasNearbyPlayer(store, position, 32.0)) {
                var life = backgroundLife.markLoaded(currentProfile, worldId,
                        "%.1f,%.1f,%.1f".formatted(position.x, position.y, position.z),
                        gameNow == null ? Instant.now() : gameNow);
                backgroundWork = life.activity() == BackgroundActivityType.WORK;
            } else if (gameNow != null) {
                backgroundLife.advanceUnloaded(currentProfile, worldId, gameNow);
            }
        }
        if (home != null) {
            home.initialize(currentProfile.id(), worldId,
                    transform.getPosition(), Instant.now());
        }
        tasks.tickNpc(currentProfile.id(), ref, npc, transform, store);
        boolean cognitionControlsMovement = autonomousCognition != null
                && autonomousCognition.tick(currentProfile, worldId, ref, npc, transform,
                        store.getExternalData().getWorld(), store,
                        listening || backgroundWork || tasks.hasActiveTasks(currentProfile.id()),
                        commandBuffer, Instant.now());
        if (home != null && !cognitionControlsMovement) {
            home.tick(currentProfile.id(), worldId, npc, transform,
                    store.getExternalData().getWorld(), listening, Instant.now());
        }
        // Attention runs last so task/navigation completion cannot overwrite the
        // conversational stop point or focused head target during LISTENING.
        attention.tickNpc(currentProfile, ref, npc, transform, uuid.getUuid(), store,
                commandBuffer);
        if (voice != null) {
            voice.observeNpc(currentProfile.id(), ref, listening);
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    private NpcProfile resolveProfile(
            Ref<EntityStore> ref, UUID entityId, String roleId,
            CommandBuffer<EntityStore> commandBuffer) {
        if (profileRegistry == null) return profile.get();
        NpcProfile registered = runtimes == null ? null : runtimes.profileForEntity(entityId)
                .flatMap(profileRegistry::byId).orElse(null);
        if (registered != null) return registered;
        NpcProfile nativeRoleProfile = nativeRoles == null ? null
                : nativeRoles.profileForRole(roleId).orElse(null);
        if (nativeRoleProfile != null) return nativeRoleProfile;
        PersistentDisplayName display = commandBuffer.getComponent(
                ref, PersistentDisplayName.getComponentType());
        String name = display == null || display.getDisplayName() == null
                ? "" : display.getDisplayName().getRawText();
        return profileRegistry.byName(name).orElseGet(profileRegistry::defaultProfile);
    }

    private static UUID worldId(Store<EntityStore> store) {
        for (PlayerRef player : store.getExternalData().getWorld().getPlayerRefs()) {
            if (player != null && player.getWorldUuid() != null) {
                return player.getWorldUuid();
            }
        }
        return null;
    }

    private static boolean hasNearbyPlayer(
            Store<EntityStore> store, Vector3d npcPosition, double radius) {
        double radiusSquared = radius * radius;
        for (PlayerRef player : store.getExternalData().getWorld().getPlayerRefs()) {
            if (player != null && player.isValid() && player.getTransform() != null
                    && player.getTransform().getPosition().distanceSquared(npcPosition)
                            <= radiusSquared) {
                return true;
            }
        }
        return false;
    }
}
