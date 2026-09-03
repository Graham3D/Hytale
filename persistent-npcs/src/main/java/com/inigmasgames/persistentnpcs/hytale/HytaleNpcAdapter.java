package com.inigmasgames.persistentnpcs.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.protocol.MovementStates;
import it.unimi.dsi.fastutil.Pair;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import org.joml.Vector3d;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.AppearanceRepository;
import com.inigmasgames.persistentnpcs.profile.NpcInventoryRepository;
import com.inigmasgames.persistentnpcs.profile.NpcInventoryState;
import com.inigmasgames.persistentnpcs.home.NpcHomeBehaviorController;
import java.time.Instant;
import java.util.function.Consumer;

public final class HytaleNpcAdapter {
    public static final String TEST_ROLE_ID = "ImmersiveNPCs_Character";
    public static final String LEGACY_ROLE_ID = "PersistentNPCs_Mara";
    private final NpcRuntimeRegistry runtimes;
    private final Supplier<NpcProfile> profile;
    private final AppearanceRepository appearances;
    private final Consumer<String> diagnostics;
    private final RuntimeApiCompatibility compatibility;
    private final NpcHomeBehaviorController homeBehavior;
    private final NpcInventoryRepository authoredInventories;

    /** Component registrations exist only after the Update 6 NPC plugin lifecycle begins. */
    private static Query<EntityStore> testNpcQuery() {
        return Archetype.of(NPCEntity.getComponentType(),
                TransformComponent.getComponentType(), UUIDComponent.getComponentType());
    }

    public HytaleNpcAdapter(NpcRuntimeRegistry runtimes, Supplier<NpcProfile> profile) {
        this(runtimes, profile, null, ignored -> { },
                RuntimeApiCompatibility.supportedForTests(), null, null);
    }

    public HytaleNpcAdapter(
            NpcRuntimeRegistry runtimes,
            Supplier<NpcProfile> profile,
            AppearanceRepository appearances,
            Consumer<String> diagnostics) {
        this(runtimes, profile, appearances, diagnostics,
                RuntimeApiCompatibility.supportedForTests(), null, null);
    }

    public HytaleNpcAdapter(
            NpcRuntimeRegistry runtimes,
            Supplier<NpcProfile> profile,
            AppearanceRepository appearances,
            Consumer<String> diagnostics,
            RuntimeApiCompatibility compatibility) {
        this(runtimes, profile, appearances, diagnostics, compatibility, null, null);
    }

    public HytaleNpcAdapter(
            NpcRuntimeRegistry runtimes,
            Supplier<NpcProfile> profile,
            AppearanceRepository appearances,
            Consumer<String> diagnostics,
            RuntimeApiCompatibility compatibility,
            NpcHomeBehaviorController homeBehavior) {
        this(runtimes, profile, appearances, diagnostics, compatibility, homeBehavior, null);
    }

    public HytaleNpcAdapter(
            NpcRuntimeRegistry runtimes,
            Supplier<NpcProfile> profile,
            AppearanceRepository appearances,
            Consumer<String> diagnostics,
            RuntimeApiCompatibility compatibility,
            NpcHomeBehaviorController homeBehavior,
            NpcInventoryRepository authoredInventories) {
        this.runtimes = runtimes;
        this.profile = profile;
        this.appearances = appearances;
        this.diagnostics = diagnostics;
        this.compatibility = compatibility;
        this.homeBehavior = homeBehavior;
        this.authoredInventories = authoredInventories;
    }

    public UUID spawnTestNpc(
            Store<EntityStore> store, PlayerRef playerRef, String displayName) {
        return spawnNpc(store, playerRef, profile.get());
    }

    public UUID spawnNpc(
            Store<EntityStore> store, PlayerRef playerRef, NpcProfile currentProfile) {
        if (!compatibility.update6NpcApi()) {
            throw new IllegalStateException(compatibility.blockerMessage());
        }
        runtimes.forProfile(currentProfile.id()).ifPresent(existing -> {
            Ref<EntityStore> existingRef = store.getExternalData()
                    .getRefFromUUID(existing.entityId());
            if (existingRef != null && existingRef.isValid()) {
                throw new IllegalStateException(currentProfile.name()
                        + " is already spawned in this world.");
            }
            runtimes.unregisterEntity(existing.entityId());
        });
        Vector3d playerPosition = new Vector3d(playerRef.getTransform().getPosition());
        Vector3d direction = playerRef.getTransform().getDirection();
        direction.y = 0;
        if (direction.lengthSquared() < 0.0001) {
            direction.set(1, 0, 0);
        } else {
            direction.normalize();
        }
        Vector3d requestedPosition = new Vector3d(playerPosition).fma(2.0, direction);
        World world = store.getExternalData().getWorld();
        Vector3d spawnPosition = GroundPositionResolver.resolve(world, requestedPosition)
                .orElseThrow(() -> new IllegalStateException(
                        "No loaded, walkable ground was found near the player."));
        diagnostics.accept("NPC spawn grounding playerY=" + playerPosition.y
                + " resolvedFeetY=" + spawnPosition.y
                + " creativeAltitudeIgnored="
                + (Math.abs(playerPosition.y - spawnPosition.y) > 0.35));
        Rotation3f rotation = rotationFacing(
                new Vector3d(playerPosition).sub(spawnPosition));
        Pair<Ref<EntityStore>, INonPlayerCharacter> spawned = NPCPlugin.get().spawnNPC(
                store, TEST_ROLE_ID, null, spawnPosition, rotation);
        if (spawned == null || spawned.first() == null
                || !(spawned.second() instanceof NPCEntity)) {
            throw new IllegalStateException("Hytale rejected the test NPC spawn");
        }
        Ref<EntityStore> ref = spawned.first();
        initializeIdleState(store, ref);
        ensureInventory(store, ref);
        if (authoredInventories != null
                && authoredInventories.applyToSpawnedNpc(currentProfile.name(), store, ref)) {
            diagnostics.accept("NPC authored inventory applied profile=" + currentProfile.id()
                    + " storageCapacity=" + NpcInventoryState.INVENTORY_CAPACITY);
        }
        if (appearances != null) {
            if (!appearances.apply(currentProfile.name(), ref,
                    (NPCEntity) spawned.second(), store)) {
                appearances.apply(currentProfile.appearancePreset(), ref,
                        (NPCEntity) spawned.second(), store);
            }
        }
        store.putComponent(ref, PersistentDisplayName.getComponentType(),
                new PersistentDisplayName(Message.raw(currentProfile.name())));
        store.putComponent(ref, Nameplate.getComponentType(), new Nameplate(currentProfile.name()));
        UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuid == null) {
            throw new IllegalStateException("Spawned NPC has no UUIDComponent");
        }
        runtimes.register(currentProfile.id(), playerRef.getWorldUuid(), uuid.getUuid());
        if (homeBehavior != null) {
            homeBehavior.initialize(currentProfile.id(), playerRef.getWorldUuid(),
                    spawnPosition, Instant.now());
        }
        return uuid.getUuid();
    }

    /** Refreshes the nearest loaded Mara in place and removes invalid duplicates safely. */
    public RefreshResult refreshTestNpc(Store<EntityStore> store, PlayerRef playerRef) {
        return refreshNpc(store, playerRef, profile.get());
    }

    public RefreshResult refreshNpc(
            Store<EntityStore> store, PlayerRef playerRef, NpcProfile currentProfile) {
        List<LocatedNpc> found = locateNpcs(store, currentProfile);
        if (found.isEmpty()) {
            throw new IllegalStateException(currentProfile.name() + " is not spawned.");
        }
        Vector3d playerPosition = playerRef.getTransform().getPosition();
        LocatedNpc selected = found.stream().min(Comparator.comparingDouble(
                value -> value.position().distanceSquared(playerPosition))).orElseThrow();
        int duplicates = Math.max(0, found.size() - 1);
        store.forEachChunk(testNpcQuery(), (chunk, commandBuffer) -> {
            for (int index = 0; index < chunk.size(); index++) {
                NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
                UUIDComponent uuid = chunk.getComponent(index, UUIDComponent.getComponentType());
                if (npc == null || uuid == null || !isManagedRole(npc.getNPCTypeId())
                        || found.stream().noneMatch(value -> value.entityId().equals(uuid.getUuid()))) {
                    continue;
                }
                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                if (!uuid.getUuid().equals(selected.entityId())) {
                    commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
                    runtimes.unregisterEntity(uuid.getUuid());
                    continue;
                }
                queueRuntimeRefresh(currentProfile, npc, ref, commandBuffer);
            }
        });
        if (authoredInventories != null && selected.ref().isValid()) {
            authoredInventories.applyToSpawnedNpc(currentProfile.name(), store, selected.ref());
        }
        runtimes.unregisterProfile(currentProfile.id());
        runtimes.register(currentProfile.id(), playerRef.getWorldUuid(), selected.entityId());
        diagnostics.accept("NPC runtime refreshed profile=" + currentProfile.id()
                + " entity=" + selected.entityId() + " duplicatesRemoved=" + duplicates
                + " appearance=COMMAND_BUFFER");
        return new RefreshResult(selected.entityId(), duplicates);
    }

    /** Removes world entities only; authored and persistent profile data are untouched. */
    public int removeTestNpc(Store<EntityStore> store) {
        return removeNpc(store, profile.get());
    }

    public int removeNpc(Store<EntityStore> store, NpcProfile currentProfile) {
        List<LocatedNpc> found = locateNpcs(store, currentProfile);
        if (found.isEmpty()) {
            throw new IllegalStateException(currentProfile.name() + " is not spawned.");
        }
        store.forEachChunk(testNpcQuery(), (chunk, commandBuffer) -> {
            for (int index = 0; index < chunk.size(); index++) {
                NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
                UUIDComponent uuid = chunk.getComponent(index, UUIDComponent.getComponentType());
                if (npc != null && uuid != null && isManagedRole(npc.getNPCTypeId())
                        && found.stream().anyMatch(value -> value.entityId().equals(uuid.getUuid()))) {
                    commandBuffer.removeEntity(chunk.getReferenceTo(index), RemoveReason.REMOVE);
                    runtimes.unregisterEntity(uuid.getUuid());
                }
            }
        });
        runtimes.unregisterProfile(currentProfile.id());
        diagnostics.accept("NPC world entity removed profile=" + currentProfile.id()
                + " count=" + found.size() + " persistentDataPreserved=true");
        return found.size();
    }

    /**
     * Resolves the nearest loaded authoritative entity for an authored profile without
     * refreshing, replacing, or otherwise mutating that NPC. Used by isolated native
     * window validation and safe runtime inspection.
     */
    public Ref<EntityStore> requireLiveNpcRef(
            Store<EntityStore> store,
            NpcProfile selectedProfile,
            Vector3d viewerPosition) {
        List<LocatedNpc> found = locateNpcs(store, selectedProfile);
        if (found.isEmpty()) {
            throw new IllegalStateException(selectedProfile.name() + " is not spawned.");
        }
        if (viewerPosition == null) return found.getFirst().ref();
        return found.stream().min(Comparator.comparingDouble(
                value -> value.position().distanceSquared(viewerPosition)))
                .orElseThrow().ref();
    }

    private List<LocatedNpc> locateNpcs(Store<EntityStore> store, NpcProfile selectedProfile) {
        List<LocatedNpc> found = new ArrayList<>();
        store.forEachChunk(testNpcQuery(), (chunk, ignored) -> {
            for (int index = 0; index < chunk.size(); index++) {
                NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
                UUIDComponent uuid = chunk.getComponent(index, UUIDComponent.getComponentType());
                TransformComponent transform = chunk.getComponent(
                        index, TransformComponent.getComponentType());
                boolean matchingRuntime = uuid != null && runtimes.profileForEntity(uuid.getUuid())
                        .map(selectedProfile.id()::equals).orElse(false);
                PersistentDisplayName display = uuid == null ? null : store.getComponent(
                        chunk.getReferenceTo(index), PersistentDisplayName.getComponentType());
                boolean matchingName = display != null && display.getDisplayName() != null
                        && selectedProfile.name().equalsIgnoreCase(
                                display.getDisplayName().getRawText());
                if (npc != null && uuid != null && transform != null
                        && isManagedRole(npc.getNPCTypeId())
                        && (matchingRuntime || matchingName)) {
                    found.add(new LocatedNpc(uuid.getUuid(),
                            new Vector3d(transform.getPosition()), chunk.getReferenceTo(index)));
                }
            }
        });
        return found;
    }

    private void queueRuntimeRefresh(
            NpcProfile currentProfile,
            NPCEntity npc,
            Ref<EntityStore> ref,
            CommandBuffer<EntityStore> commandBuffer) {
        if (appearances != null) {
            if (!appearances.queueApply(currentProfile.name(), ref, npc, commandBuffer)) {
                appearances.queueApply(currentProfile.appearancePreset(), ref, npc, commandBuffer);
            }
        }
        commandBuffer.putComponent(ref, PersistentDisplayName.getComponentType(),
                new PersistentDisplayName(Message.raw(currentProfile.name())));
        commandBuffer.putComponent(ref, Nameplate.getComponentType(),
                new Nameplate(currentProfile.name()));
        if (commandBuffer.getComponent(ref,
                InventoryComponent.Hotbar.getComponentType()) == null) {
            commandBuffer.putComponent(ref, InventoryComponent.Hotbar.getComponentType(),
                    new InventoryComponent.Hotbar((short) 8));
        }
        if (commandBuffer.getComponent(ref,
                InventoryComponent.Storage.getComponentType()) == null) {
            commandBuffer.putComponent(ref, InventoryComponent.Storage.getComponentType(),
                    new InventoryComponent.Storage(NpcInventoryState.INVENTORY_CAPACITY));
        }
        if (commandBuffer.getComponent(ref,
                MovementStatesComponent.getComponentType()) == null) {
            MovementStates idle = new MovementStates();
            idle.idle = true;
            idle.horizontalIdle = true;
            idle.onGround = true;
            MovementStatesComponent movement = new MovementStatesComponent();
            movement.setMovementStates(idle);
            commandBuffer.putComponent(ref, MovementStatesComponent.getComponentType(),
                    movement);
        }
    }

    public record RefreshResult(UUID entityId, int duplicatesRemoved) { }

    private record LocatedNpc(
            UUID entityId, Vector3d position, Ref<EntityStore> ref) { }

    /**
     * Builds the spawn rotation without linking to Rotation3f.lookAt. Hytale 0.5.9
     * accepted Vector3d while Update 6 changed that binary signature to Vector3dc.
     * The three-float constructor is stable across both builds.
     */
    static Rotation3f rotationFacing(Vector3d direction) {
        if (direction == null || !direction.isFinite()
                || direction.lengthSquared() < 0.00000001) {
            return new Rotation3f();
        }
        double horizontal = Math.sqrt(
                direction.x * direction.x + direction.z * direction.z);
        float pitch = (float) Math.atan2(direction.y, horizontal);
        float yaw = (float) Math.atan2(-direction.x, -direction.z);
        return new Rotation3f(pitch, yaw, 0.0f);
    }

    private static void ensureInventory(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (store.getComponent(ref, InventoryComponent.Hotbar.getComponentType()) == null) {
            store.putComponent(ref, InventoryComponent.Hotbar.getComponentType(),
                    new InventoryComponent.Hotbar((short) 8));
        }
        if (store.getComponent(ref, InventoryComponent.Storage.getComponentType()) == null) {
            store.putComponent(ref, InventoryComponent.Storage.getComponentType(),
                    new InventoryComponent.Storage((short) 24));
        }
    }

    private static void initializeIdleState(Store<EntityStore> store, Ref<EntityStore> ref) {
        Velocity velocity = store.getComponent(ref, Velocity.getComponentType());
        if (velocity != null) {
            velocity.getInstructions().clear();
            velocity.setZero();
            velocity.setClient(0, 0, 0);
        }

        MovementStatesComponent component = store.getComponent(
                ref, MovementStatesComponent.getComponentType());
        if (component != null) {
            MovementStates idle = new MovementStates();
            idle.idle = true;
            idle.horizontalIdle = true;
            idle.onGround = true;
            component.setMovementStates(idle);
        }
    }

    public boolean isTestNpc(Object targetEntity) {
        return targetEntity instanceof NPCEntity npc
                && isManagedRole(npc.getNPCTypeId());
    }

    public java.util.Optional<UUID> profileIdForEntity(UUID entityId) {
        return runtimes.profileForEntity(entityId);
    }

    public static boolean isManagedRole(String roleId) {
        return TEST_ROLE_ID.equals(roleId) || LEGACY_ROLE_ID.equals(roleId)
                || ManagedNpcRoles.contains(roleId);
    }

    public static void registerManagedRole(String roleId) {
        ManagedNpcRoles.register(roleId);
    }
}
