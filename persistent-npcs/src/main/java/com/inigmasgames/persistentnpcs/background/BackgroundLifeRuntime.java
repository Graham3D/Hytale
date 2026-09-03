package com.inigmasgames.persistentnpcs.background;

import com.inigmasgames.persistentnpcs.hytale.NpcRuntimeRegistry;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Low-frequency unloaded simulation. It never touches the Hytale ECS from this thread. */
public final class BackgroundLifeRuntime implements AutoCloseable {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
            runnable -> Thread.ofPlatform().name("immersive-ai-background-life").daemon(true)
                    .unstarted(runnable));

    public BackgroundLifeRuntime(
            Supplier<NpcProfile> profile,
            NpcRuntimeRegistry runtimes,
            BackgroundLifeStore store,
            BackgroundLifeSimulator simulator,
            Consumer<String> diagnostics) {
        Consumer<String> log = diagnostics == null ? ignored -> { } : diagnostics;
        executor.scheduleWithFixedDelay(() -> {
            try {
                NpcProfile current = profile.get();
                BackgroundLifeState state = store.get(current.id());
                if (state != null && runtimes.forProfile(current.id()).isEmpty()) {
                    World world = state.worldId() == null ? null
                            : Universe.get().getWorld(state.worldId());
                    if (world != null && world.isAlive()) {
                        world.execute(() -> {
                            WorldTimeResource clock = world.getEntityStore().getStore()
                                    .getResource(WorldTimeResource.getResourceType());
                            if (clock != null && clock.getGameDateTime() != null) {
                                simulator.advanceUnloaded(current, state.worldId(),
                                        clock.getGameDateTime().toInstant(
                                                java.time.ZoneOffset.UTC));
                            }
                        });
                    }
                }
            } catch (Throwable failure) {
                log.accept("BACKGROUND_LIFE_FAILED type=" + failure.getClass().getSimpleName()
                        + " reason=" + failure.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
