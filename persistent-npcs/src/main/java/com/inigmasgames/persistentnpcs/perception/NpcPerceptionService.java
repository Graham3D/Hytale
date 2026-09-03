package com.inigmasgames.persistentnpcs.perception;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.PositionCache;
import com.inigmasgames.persistentnpcs.hytale.NpcRuntimeRegistry;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Vector3d;

/** Builds bounded, detached facts on the owning Hytale world thread. */
public final class NpcPerceptionService implements NpcPerceptionGateway {
    private static final double ENTITY_RADIUS = 12.0;
    private static final int BLOCK_RADIUS = 14;
    private static final long ENVIRONMENT_TTL_MILLIS = 2_500;
    private static final double NPC_REFRESH_DISTANCE = 3.0;
    private static final double PLAYER_REFRESH_DISTANCE = 6.0;
    private static final int MAX_LIST = 20;
    private final NpcRuntimeRegistry runtimes;
    private final NpcProfileRegistry profiles;
    private final EnvironmentSemanticAnalyzer environmentAnalyzer =
            new EnvironmentSemanticAnalyzer();
    private final ConcurrentHashMap<UUID, EnvironmentCapture> environmentCache =
            new ConcurrentHashMap<>();

    public NpcPerceptionService(NpcRuntimeRegistry runtimes) {
        this(runtimes, null);
    }

    public NpcPerceptionService(NpcRuntimeRegistry runtimes, NpcProfileRegistry profiles) {
        this.runtimes = runtimes;
        this.profiles = profiles;
    }

    public CompletableFuture<NpcPerceptionSnapshot> capture(
            NpcProfile profile, UUID focusedPlayerId) {
        return capture(profile, focusedPlayerId, false);
    }

    public CompletableFuture<NpcPerceptionSnapshot> capture(
            NpcProfile profile, UUID focusedPlayerId, boolean forceEnvironmentRefresh) {
        return captureRaw(profile, focusedPlayerId, forceEnvironmentRefresh, null)
                .thenApply(RawPerceptionSnapshot::engineSnapshot);
    }

    public CompletableFuture<RawPerceptionSnapshot> captureRaw(
            NpcProfile profile, UUID focusedPlayerId, UUID responseId) {
        return captureRaw(profile, focusedPlayerId, false, responseId);
    }

    public CompletableFuture<RawPerceptionSnapshot> captureRaw(
            NpcProfile profile, UUID focusedPlayerId, boolean forceEnvironmentRefresh,
            UUID responseId) {
        NpcRuntimeRegistry.RuntimeNpc runtime = runtimes.forProfile(profile.id()).orElse(null);
        if (runtime == null) {
            return CompletableFuture.completedFuture(
                    RawPerceptionSnapshot.unavailable(responseId, profile.id()));
        }
        World world = Universe.get().getWorld(runtime.worldId());
        if (world == null || !world.isAlive()) {
            return CompletableFuture.completedFuture(
                    RawPerceptionSnapshot.unavailable(responseId, profile.id()));
        }
        CompletableFuture<RawPerceptionSnapshot> future = new CompletableFuture<>();
        world.execute(() -> {
            try {
                future.complete(captureOnWorld(responseId, profile.id(), focusedPlayerId,
                        runtime, world, forceEnvironmentRefresh));
            } catch (Throwable failure) {
                future.completeExceptionally(failure);
            }
        });
        return future;
    }

    private RawPerceptionSnapshot captureOnWorld(
            UUID responseId,
            UUID profileId,
            UUID focusedPlayerId,
            NpcRuntimeRegistry.RuntimeNpc runtime,
            World world,
            boolean forceEnvironmentRefresh) {
        long captureStarted = System.nanoTime();
        Instant capturedAt = Instant.now();
        String captureThread = Thread.currentThread().getName();
        Store<EntityStore> store = world.getEntityStore().getStore();
        Ref<EntityStore> npcRef = world.getEntityRef(runtime.entityId());
        if (npcRef == null || !npcRef.isValid()) {
            return RawPerceptionSnapshot.unavailable(responseId, profileId);
        }
        TransformComponent npcTransform = store.getComponent(
                npcRef, TransformComponent.getComponentType());
        if (npcTransform == null) {
            return RawPerceptionSnapshot.unavailable(responseId, profileId);
        }
        Vector3d origin = new Vector3d(npcTransform.getPosition());
        List<PerceivedEntity> players = new ArrayList<>();
        List<PerceivedEntity> npcs = new ArrayList<>();
        List<PerceivedEntity> hostiles = new ArrayList<>();
        List<PerceivedItem> items = new ArrayList<>();
        PositionCache positionCache = PositionCache.get(npcRef, store);

        Query<EntityStore> entityQuery = Archetype.of(
                TransformComponent.getComponentType(), UUIDComponent.getComponentType());
        store.forEachChunk(entityQuery, (chunk, commandBuffer) -> {
            for (int index = 0; index < chunk.size(); index++) {
                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                TransformComponent transform = chunk.getComponent(
                        index, TransformComponent.getComponentType());
                UUIDComponent uuid = chunk.getComponent(index, UUIDComponent.getComponentType());
                if (transform == null || uuid == null
                        || uuid.getUuid().equals(runtime.entityId())) {
                    continue;
                }
                double distance = origin.distance(transform.getPosition());
                if (distance > ENTITY_RADIUS) {
                    continue;
                }
                if (positionCache != null
                        && !positionCache.hasLineOfSight(npcRef, ref, commandBuffer)) {
                    continue;
                }
                PlayerRef player = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
                if (player != null) {
                    String label = uuid.getUuid().equals(focusedPlayerId)
                            ? "focused player" : "nearby player";
                    players.add(new PerceivedEntity(uuid.getUuid(), label,
                            "player", distance));
                    continue;
                }
                NPCEntity otherNpc = commandBuffer.getComponent(ref, NPCEntity.getComponentType());
                if (otherNpc != null) {
                    // Update 6 moved relationship resolution out of Role. Until the
                    // authoritative replacement is exposed here, do not invent hostility.
                    boolean hostile = false;
                    String resolvedName = profiles == null ? otherNpc.getNPCTypeId()
                            : runtimes.profileForEntity(uuid.getUuid())
                                    .flatMap(profiles::byId).map(NpcProfile::name)
                                    .orElse(otherNpc.getNPCTypeId());
                    PerceivedEntity fact = new PerceivedEntity(uuid.getUuid(),
                            resolvedName, hostile ? "hostile_npc" : "npc", distance);
                    (hostile ? hostiles : npcs).add(fact);
                    continue;
                }
                ItemComponent item = commandBuffer.getComponent(ref, ItemComponent.getComponentType());
                if (item != null && !ItemStack.isEmpty(item.getItemStack())) {
                    items.add(item(uuid.getUuid(), item.getItemStack(), distance));
                }
            }
        });

        PerceivedItem held = null;
        Integer heldSlot = null;
        Vector3d playerPosition = null;
        if (focusedPlayerId != null) {
            Ref<EntityStore> playerRef = world.getEntityRef(focusedPlayerId);
            if (playerRef != null && playerRef.isValid()) {
                TransformComponent playerTransform = store.getComponent(
                        playerRef, TransformComponent.getComponentType());
                if (playerTransform != null) {
                    playerPosition = new Vector3d(playerTransform.getPosition());
                }
                InventoryComponent.Hotbar hotbar = store.getComponent(
                        playerRef, InventoryComponent.Hotbar.getComponentType());
                int selected = hotbar == null ? -1 : hotbar.getActiveSlot();
                heldSlot = hotbar == null ? null : selected;
                ItemStack stack = hotbar == null || selected < 0
                        || selected >= hotbar.getInventory().getCapacity()
                        ? null : hotbar.getInventory().getItemStack((short) selected);
                if (!ItemStack.isEmpty(stack)) {
                    held = item(null, stack, 0);
                }
            }
        }
        List<PerceivedItem> inventory = inventory(store, npcRef);
        List<PerceivedEntity> interactables = new ArrayList<>();
        List<PerceivedEntity> stations = new ArrayList<>();
        EnvironmentCapture environmentCapture = environmentFor(profileId, runtime.worldId(), world,
                origin, playerPosition, interactables, stations, forceEnvironmentRefresh);
        EnvironmentSnapshot environment = environmentCapture.snapshot();
        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());

        NpcPerceptionSnapshot snapshot = new NpcPerceptionSnapshot(
                profileId, runtime.entityId(), runtime.worldId(),
                time == null ? null : time.getGameDateTime(),
                origin.x, origin.y, origin.z,
                limited(players), limited(npcs), limited(hostiles), limitedItems(items),
                limited(interactables), limited(stations), heldSlot, held, inventory,
                environment);
        return new RawPerceptionSnapshot(responseId, capturedAt, captureThread,
                Duration.ofNanos(System.nanoTime() - captureStarted).toMillis(), snapshot,
                environmentCapture.totalSamples(), environmentCapture.debugSamples());
    }

    private EnvironmentCapture environmentFor(
            UUID profileId,
            UUID worldId,
            World world,
            Vector3d origin,
            Vector3d playerPosition,
            List<PerceivedEntity> interactables,
            List<PerceivedEntity> stations,
            boolean forceRefresh) {
        Instant now = Instant.now();
        EnvironmentCapture cachedCapture = environmentCache.get(profileId);
        EnvironmentSnapshot cached = cachedCapture == null ? null : cachedCapture.snapshot();
        if (!forceRefresh && cacheValid(cached, worldId, origin, playerPosition, now)) {
            cached.importantObjects().stream()
                    .filter(feature -> feature.category().equals("interactable"))
                    .map(feature -> new PerceivedEntity(null, feature.label(),
                            "interactable_block", feature.distanceMeters()))
                    .forEach(interactables::add);
            cached.importantObjects().stream()
                    .filter(feature -> feature.category().equals("crafting_station"))
                    .map(feature -> new PerceivedEntity(null, feature.label(),
                            "crafting_station", feature.distanceMeters()))
                    .forEach(stations::add);
            return cachedCapture;
        }
        long started = System.nanoTime();
        List<EnvironmentSample> samples = scanBlocks(world, origin, interactables, stations);
        long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
        EnvironmentSnapshot refreshed = environmentAnalyzer.summarize(worldId, now,
                origin.x, origin.y, origin.z,
                playerPosition == null ? null : playerPosition.x,
                playerPosition == null ? null : playerPosition.y,
                playerPosition == null ? null : playerPosition.z,
                BLOCK_RADIUS, samples, elapsed);
        EnvironmentCapture capture = new EnvironmentCapture(refreshed, samples.size(),
                samples.stream().sorted(Comparator.comparingDouble(sample -> distance(
                        origin.x, origin.y, origin.z, sample))).limit(160).toList());
        environmentCache.put(profileId, capture);
        return capture;
    }

    private static boolean cacheValid(EnvironmentSnapshot snapshot, UUID worldId,
            Vector3d origin, Vector3d playerPosition, Instant now) {
        if (snapshot == null || !java.util.Objects.equals(snapshot.worldId(), worldId)
                || snapshot.ageMillis(now) > ENVIRONMENT_TTL_MILLIS
                || distance(snapshot.npcX(), snapshot.npcY(), snapshot.npcZ(), origin)
                        > NPC_REFRESH_DISTANCE) {
            return false;
        }
        if (playerPosition == null || snapshot.playerX() == null) {
            return playerPosition == null && snapshot.playerX() == null;
        }
        return distance(snapshot.playerX(), snapshot.playerY(), snapshot.playerZ(), playerPosition)
                <= PLAYER_REFRESH_DISTANCE;
    }

    private static double distance(double x, double y, double z, Vector3d other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double distance(double x, double y, double z, EnvironmentSample other) {
        double dx = x - other.x();
        double dy = y - other.y();
        double dz = z - other.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static List<EnvironmentSample> scanBlocks(
            World world,
            Vector3d origin,
            List<PerceivedEntity> interactables,
            List<PerceivedEntity> stations) {
        List<EnvironmentSample> samples = new ArrayList<>();
        int ox = (int) Math.floor(origin.x);
        int oy = (int) Math.floor(origin.y);
        int oz = (int) Math.floor(origin.z);
        for (int x = ox - BLOCK_RADIUS; x <= ox + BLOCK_RADIUS; x++) {
            for (int z = oz - BLOCK_RADIUS; z <= oz + BLOCK_RADIUS; z++) {
                if (Math.hypot(x + 0.5 - origin.x, z + 0.5 - origin.z) > BLOCK_RADIUS) {
                    continue;
                }
                WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
                if (chunk == null) {
                    continue;
                }
                for (int y = oy - 4; y <= oy + 10; y++) {
                    BlockType block = chunk.getBlockType(x, y, z);
                    double distance = origin.distance(x + 0.5, y + 0.5, z + 0.5);
                    if (block != null && block != BlockType.EMPTY) {
                        boolean station = block.getBench() != null;
                        boolean interactable = block.getInteractions() != null
                                && !block.getInteractions().isEmpty();
                        boolean container = block.getBlockEntity() != null
                                && block.getBlockEntity().getComponent(
                                        ItemContainerBlock.getComponentType()) != null;
                        if (station) {
                            stations.add(new PerceivedEntity(null, block.getId(),
                                    "crafting_station:" + block.getBench().getId(), distance));
                        } else if (interactable) {
                            interactables.add(new PerceivedEntity(null, block.getId(),
                                    "interactable_block", distance));
                        }
                        samples.add(new EnvironmentSample(block.getId(), block.getGroup(),
                                block.getMaterial() == null ? "" : block.getMaterial().name(),
                                block.getCustomModel(), x + 0.5, y + 0.5, z + 0.5,
                                interactable, station, block.isDoor(), container,
                                block.getSeats() != null || block.getBeds() != null,
                                block.getLight() != null && Byte.toUnsignedInt(
                                        block.getLight().radius) > 0
                                        || block.getId().toLowerCase(java.util.Locale.ROOT)
                                                .contains("portal")
                                                && block.getParticles() != null
                                                && block.getParticles().length > 0,
                                false));
                    }
                    int fluidId = chunk.getFluidId(x, y, z);
                    if (fluidId != Fluid.EMPTY_ID) {
                        Fluid fluid = Fluid.getAssetMap().getAssetOrDefault(fluidId, Fluid.UNKNOWN);
                        samples.add(new EnvironmentSample(fluid.getId(), "fluid", "fluid", "",
                                x + 0.5, y + 0.5, z + 0.5,
                                fluid.getInteractions() != null
                                        && !fluid.getInteractions().isEmpty(),
                                false, false, false, false, fluid.getLight() != null, true));
                    }
                }
            }
        }
        return samples;
    }

    private static List<PerceivedItem> inventory(
            Store<EntityStore> store, Ref<EntityStore> npcRef) {
        CombinedItemContainer inventory = InventoryComponent.getCombined(
                store, npcRef, InventoryComponent.STORAGE_HOTBAR_BACKPACK);
        List<PerceivedItem> facts = new ArrayList<>();
        for (short slot = 0; slot < inventory.getCapacity(); slot++) {
            ItemStack stack = inventory.getItemStack(slot);
            if (!ItemStack.isEmpty(stack)) {
                facts.add(item(null, stack, 0));
            }
        }
        return List.copyOf(facts);
    }

    private static PerceivedItem item(UUID entityId, ItemStack stack, double distance) {
        return new PerceivedItem(entityId, stack.getItemId(), displayName(stack),
                stack.getQuantity(), stack.getDurability(), stack.getMaxDurability(),
                stack.getMetadata() == null ? "{}" : stack.getMetadata().toJson(), distance);
    }

    private static String displayName(ItemStack stack) {
        Message display = stack.getDisplayName();
        if (display != null && display.getRawText() != null
                && !display.getRawText().isBlank()) {
            return display.getRawText();
        }
        if (display != null && display.getMessageId() != null
                && !display.getMessageId().isBlank()) {
            return display.getMessageId();
        }
        return stack.getItemId();
    }

    private static List<PerceivedEntity> limited(List<PerceivedEntity> values) {
        return values.stream().sorted(Comparator.comparingDouble(PerceivedEntity::distanceMeters))
                .limit(MAX_LIST).toList();
    }

    private static List<PerceivedItem> limitedItems(List<PerceivedItem> values) {
        return values.stream().sorted(Comparator.comparingDouble(PerceivedItem::distanceMeters))
                .limit(MAX_LIST).toList();
    }

    private record EnvironmentCapture(
            EnvironmentSnapshot snapshot, int totalSamples,
            List<EnvironmentSample> debugSamples) {
        private EnvironmentCapture {
            debugSamples = List.copyOf(debugSamples == null ? List.of() : debugSamples);
        }
    }
}
