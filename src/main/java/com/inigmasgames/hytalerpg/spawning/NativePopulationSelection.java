package com.inigmasgames.hytalerpg.spawning;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.environment.config.Environment;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.spawning.SpawningPlugin;
import com.hypixel.hytale.server.spawning.world.component.ChunkSpawnData;
import com.hypixel.hytale.server.spawning.world.component.WorldSpawnData;
import com.hypixel.hytale.server.spawning.world.system.WorldSpawningSystem;
import com.inigmasgames.hytalerpg.combat.hytale.NativeSystemReplacement;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.logging.Level;

/** Exact 0.7.2 native selection boundary. Hytale still creates and accounts for every job. */
public final class NativePopulationSelection extends WorldSpawningSystem {
    private static final String SERVER_SHA256 =
            "23f71f03763422ce7c04df191db49cd0e7d87d43b12baad75c43fdc202410aff";
    private static final MethodHandle CREATE_JOB = nativeJobMethod();
    private final ResourceType<EntityStore, WorldSpawnData> populationType;
    private final ComponentType<ChunkStore, ChunkSpawnData> chunkSpawnType;
    private final NativePopulationBalance balance;

    public NativePopulationSelection(SpawningPlugin spawning, NativePopulationBalance balance) {
        this(spawning.getWorldSpawnDataResourceType(), spawning.getChunkSpawnDataComponentType(),
                spawning.getChunkSpawnedNPCDataComponentType(), spawning.getSpawnJobDataComponentType(), balance);
    }

    NativePopulationSelection(ResourceType<EntityStore, WorldSpawnData> populationType,
            ComponentType<ChunkStore, ChunkSpawnData> chunkSpawnType,
            ComponentType<ChunkStore, com.hypixel.hytale.server.spawning.world.component.ChunkSpawnedNPCData> countedType,
            ComponentType<ChunkStore, com.hypixel.hytale.server.spawning.world.component.SpawnJobData> jobType,
            NativePopulationBalance balance) {
        super(populationType, chunkSpawnType, countedType, jobType);
        this.populationType = populationType;
        this.chunkSpawnType = chunkSpawnType;
        this.balance = Objects.requireNonNull(balance);
    }

    public NativeSystemReplacement<ChunkStore> install(ComponentRegistry<ChunkStore> registry) {
        verifyNativeIdentity();
        return NativeSystemReplacement.install(registry, WorldSpawningSystem.class,
                NativePopulationSelection.class, this, "WORLD_POPULATION_SELECTION");
    }

    /** Copy of the pinned native tick's control flow; only the marked call is added. */
    @Override public void tick(float dt, int index, Store<ChunkStore> chunks) {
        var world = chunks.getExternalData().getWorld();
        if (!world.getWorldConfig().isSpawningNPC() || world.getPlayerCount() == 0) return;
        var config = world.getGameplayConfig();
        var entities = world.getEntityStore().getStore();
        var population = entities.getResource(populationType);
        if (population.isUnspawnable() || world.getChunkStore().getStore().getEntityCount() == 0
                || config.getMaxEnvironmentalNPCSpawns() > 0
                    && population.getActualNPCs() >= config.getMaxEnvironmentalNPCSpawns()
                || population.getActualNPCs() > population.getExpectedNPCs()) return;
        var time = entities.getResource(WorldTimeResource.getResourceType());
        if (population.hasUnprocessedUnspawnableChunks()) {
            while (population.hasUnprocessedUnspawnableChunks()) {
                var entry = population.nextUnspawnableChunk();
                var chunkRef = world.getChunkStore().getChunkReference(entry.getChunkIndex());
                if (chunkRef == null) continue;
                int environmentIndex = entry.getEnvironmentIndex();
                var chunkData = chunks.getComponent(chunkRef, chunkSpawnType);
                if (chunkData == null) continue;
                var environmentChunk = chunkData.getEnvironmentSpawnData(environmentIndex);
                int removedSegments = -environmentChunk.getSegmentCount();
                population.adjustSegmentCount(removedSegments);
                var environment = population.getWorldEnvironmentSpawnData(environmentIndex);
                double before = environment.getExpectedNPCs();
                environment.adjustSegmentCount(removedSegments);
                environment.updateExpectedNPCs(time.getMoonPhase());
                environmentChunk.markProcessedAsUnspawnable();
                var log = com.hypixel.hytale.logger.HytaleLogger.getLogger().at(Level.FINEST);
                if (log.isEnabled()) {
                    var asset = Environment.getAssetMap().getAsset(environmentIndex);
                    if (asset != null) log.log("Reducing expected NPC count for %s due to un-spawnable chunk. Was %s, now %s",
                            asset.getId(), before, environment.getExpectedNPCs());
                }
            }
            population.recalculateWorldCount();
        }
        // The old native hook ran at this exact point, after native recalculation and before selection.
        balance.applySafely(world);
        int activeJobs = population.getActiveSpawnJobs();
        int maximumJobs = spawningMaximum();
        while (activeJobs < maximumJobs
                && population.getActualNPCs() < com.hypixel.hytale.math.util.MathUtil.floor(population.getExpectedNPCs())) {
            if (!createNativeJob(population, chunks, entities)) break;
            activeJobs = population.getActiveSpawnJobs();
        }
    }

    private static int spawningMaximum() { return SpawningPlugin.get().getMaxActiveJobs(); }

    private boolean createNativeJob(WorldSpawnData data, Store<ChunkStore> chunks,
            ComponentAccessor<EntityStore> entities) {
        try { return (boolean) CREATE_JOB.invoke(this, data, chunks, entities); }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("NATIVE_POPULATION_JOB_INVOKE", failure); }
    }

    private static MethodHandle nativeJobMethod() {
        try {
            return MethodHandles.privateLookupIn(WorldSpawningSystem.class, MethodHandles.lookup())
                    .findVirtual(WorldSpawningSystem.class, "createRandomSpawnJob",
                            MethodType.methodType(boolean.class, WorldSpawnData.class,
                                    Store.class, ComponentAccessor.class));
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static void verifyNativeIdentity() {
        try {
            Path source = Path.of(WorldSpawningSystem.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!Files.isRegularFile(source)) throw new IllegalStateException("NATIVE_POPULATION_SERVER_NOT_JAR");
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(source)) {
                byte[] block = new byte[65536]; int size;
                while ((size = input.read(block)) != -1) digest.update(block, 0, size);
            }
            if (!SERVER_SHA256.equals(HexFormat.of().formatHex(digest.digest())))
                throw new IllegalStateException("NATIVE_POPULATION_SERVER_IDENTITY_MISMATCH");
        } catch (Exception failure) {
            throw new IllegalStateException("NATIVE_POPULATION_SERVER_UNVERIFIED", failure);
        }
    }
}
