package com.inigmasgames.hytalerpg.spawning;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.gameplay.GameplayConfig;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.spawning.SpawningPlugin;
import com.hypixel.hytale.server.spawning.world.component.WorldSpawnData;
import com.hypixel.hytale.server.spawning.world.system.WorldSpawningSystem;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Projects a save-wide factor onto native per-environment population targets, before native scheduling. */
public final class NativeWorldSpawnDensity implements AutoCloseable {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Field CAP_FIELD;
    static {
        try {
            CAP_FIELD = GameplayConfig.class.getDeclaredField("maxEnvironmentalNPCSpawns");
            CAP_FIELD.setAccessible(true);
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private record Applied(double density, int segments, int chunks) {}
    public record Snapshot(String world, int actual, double nativeTarget, double effectiveTarget,
                           int baselineCap, int effectiveCap, int jobs) {}
    private final WorldSpawnDensitySettings settings;
    private final Map<World, Map<Integer, Applied>> applied = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<World, Long> lastChunkRefresh = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<GameplayConfig, Integer> baselineCaps = Collections.synchronizedMap(new IdentityHashMap<>());
    private volatile boolean closed;

    public NativeWorldSpawnDensity(WorldSpawnDensitySettings settings) { this.settings = settings; }
    public double multiplier() { return settings.multiplier(); }

    public CompletableFuture<Void> set(double multiplier) {
        if (closed) throw new IllegalStateException("World spawn density service stopped");
        settings.set(multiplier);
        LOGGER.atInfo().log("RPG_WORLD_SPAWN_DENSITY multiplier=%.2f baselineCap=%d effectiveCap=%d",
                multiplier, defaultBaselineCap(), WorldSpawnDensitySettings.scaledCap(defaultBaselineCap(), multiplier));
        return applyLoaded();
    }

    public CompletableFuture<Void> applyLoaded() {
        var futures = new ArrayList<CompletableFuture<Void>>();
        for (var world : Universe.get().getWorlds().values()) {
            if (!world.getWorldConfig().isSpawningNPC()) continue;
            var future = new CompletableFuture<Void>();
            futures.add(future);
            world.execute(() -> {
                try { apply(world, world.getChunkStore().getStore(), settings.multiplier()); future.complete(null); }
                catch (Throwable error) { future.completeExceptionally(error); }
            });
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    public CompletableFuture<List<Snapshot>> status() {
        var futures = new ArrayList<CompletableFuture<Snapshot>>();
        for (var world : Universe.get().getWorlds().values()) {
            if (!world.getWorldConfig().isSpawningNPC()) continue;
            var future = new CompletableFuture<Snapshot>();
            futures.add(future);
            world.execute(() -> {
                try { future.complete(snapshot(world)); }
                catch (Throwable error) { future.completeExceptionally(error); }
            });
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> futures.stream().map(CompletableFuture::join).toList());
    }

    public int defaultBaselineCap() {
        var config = GameplayConfig.getAssetMap().getAsset("Default");
        if (config == null) config = GameplayConfig.DEFAULT;
        synchronized (baselineCaps) { return baselineCaps.computeIfAbsent(config, GameplayConfig::getMaxEnvironmentalNPCSpawns); }
    }

    private void apply(World world, Store<ChunkStore> chunks, double multiplier) {
        if (closed || !world.getWorldConfig().isSpawningNPC()) return;
        var config = world.getGameplayConfig();
        synchronized (baselineCaps) {
            int baseline = baselineCaps.computeIfAbsent(config, GameplayConfig::getMaxEnvironmentalNPCSpawns);
            int effective = WorldSpawnDensitySettings.scaledCap(baseline, multiplier);
            if (config.getMaxEnvironmentalNPCSpawns() != effective) {
                try { CAP_FIELD.setInt(config, effective); }
                catch (IllegalAccessException error) { throw new IllegalStateException("Native environmental cap unavailable", error); }
            }
        }
        var entities = world.getEntityStore().getStore();
        var data = entities.getResource(WorldSpawnData.getResourceType());
        if (data == null) return;
        // Existing saves at 1x must leave native world/chunk population state untouched.
        if (multiplier == 1.0 && !applied.containsKey(world)) return;
        var time = entities.getResource(WorldTimeResource.getResourceType());
        if (time == null) throw new IllegalStateException("Native world time resource unavailable for population projection");
        var prior = applied.computeIfAbsent(world, ignored -> new HashMap<>());
        long now = System.nanoTime();
        // Native chunk admission initializes its own baseline budget. Re-project at most once a second
        // so a replacement chunk is covered even when total chunk/segment counts stay unchanged.
        boolean refreshChunks = multiplier != 1.0 &&
                now - lastChunkRefresh.getOrDefault(world, 0L) >= 1_000_000_000L;
        boolean changed = false;
        var indexes = data.getWorldEnvironmentSpawnDataIndexes();
        for (int index : indexes) {
            var environment = data.getWorldEnvironmentSpawnData(index);
            if (environment == null) continue;
            double density = SpawningPlugin.get().getEnvironmentDensity(index) * multiplier;
            int segments = environment.getSegmentCount();
            int chunkCount = environment.getChunkRefList().size();
            var current = new Applied(density, segments, chunkCount);
            double expected = segments * density / 1024.0;
            var previous = prior.get(index);
            if (refreshChunks || !current.equals(previous) || Math.abs(environment.getExpectedNPCs() - expected) > 0.000001) {
                // Native updates both world targets and each loaded chunk's budget. No NPC is manually created.
                environment.setDensity(density, chunks);
                environment.updateExpectedNPCs(time.getMoonPhase());
                prior.put(index, current);
                changed = true;
            }
        }
        if (refreshChunks) lastChunkRefresh.put(world, now);
        if (changed) data.recalculateWorldCount();
    }

    private Snapshot snapshot(World world) {
        var data = world.getEntityStore().getStore().getResource(WorldSpawnData.getResourceType());
        double baseline = 0;
        if (data != null) for (int index : data.getWorldEnvironmentSpawnDataIndexes()) {
            var environment = data.getWorldEnvironmentSpawnData(index);
            if (environment != null && environment.hasNPCs())
                baseline += environment.getSegmentCount() * SpawningPlugin.get().getEnvironmentDensity(index) / 1024.0;
        }
        var config = world.getGameplayConfig();
        int cap;
        synchronized (baselineCaps) { cap = baselineCaps.computeIfAbsent(config, GameplayConfig::getMaxEnvironmentalNPCSpawns); }
        return new Snapshot(world.getName(), data == null ? 0 : data.getActualNPCs(), baseline,
                data == null ? 0 : data.getExpectedNPCs(), cap, config.getMaxEnvironmentalNPCSpawns(),
                data == null ? 0 : data.getActiveSpawnJobs());
    }

    @Override public void close() {
        closed = true;
        synchronized (baselineCaps) {
            baselineCaps.forEach((config, baseline) -> {
                try { CAP_FIELD.setInt(config, baseline); }
                catch (IllegalAccessException error) { throw new IllegalStateException("Failed to restore native environmental cap", error); }
            });
            baselineCaps.clear();
        }
        for (var world : Universe.get().getWorlds().values()) if (world.getWorldConfig().isSpawningNPC())
            world.execute(() -> restoreWorld(world));
        applied.clear();
        lastChunkRefresh.clear();
    }

    private void restoreWorld(World world) {
        var data = world.getEntityStore().getStore().getResource(WorldSpawnData.getResourceType());
        if (data == null) return;
        var chunks = world.getChunkStore().getStore();
        var time = world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType());
        for (int index : data.getWorldEnvironmentSpawnDataIndexes()) {
            var environment = data.getWorldEnvironmentSpawnData(index);
            if (environment == null) continue;
            environment.setDensity(SpawningPlugin.get().getEnvironmentDensity(index), chunks);
            if (time != null) environment.updateExpectedNPCs(time.getMoonPhase());
        }
        data.recalculateWorldCount();
    }

    public final class Tick extends TickingSystem<ChunkStore> {
        @Override public Set<Dependency<ChunkStore>> getDependencies() {
            return Set.of(new SystemDependency<>(Order.BEFORE, WorldSpawningSystem.class));
        }
        @Override public void tick(float dt, int index, Store<ChunkStore> store) {
            if (!closed) apply(store.getExternalData().getWorld(), store, settings.multiplier());
        }
    }
}
