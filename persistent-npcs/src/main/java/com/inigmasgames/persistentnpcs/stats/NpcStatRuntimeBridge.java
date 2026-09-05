package com.inigmasgames.persistentnpcs.stats;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.inigmasgames.persistentnpcs.hytale.*;
import com.inigmasgames.persistentnpcs.profile.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Stable-profile lifecycle coordinator. Only hydration/checkpoint/removal world callbacks access ECS. */
public final class NpcStatRuntimeBridge implements AutoCloseable {
    public static final long CHECKPOINT_NANOS = TimeUnit.SECONDS.toNanos(1);
    private final NpcStatStateRepository repository;
    private final NpcProfileRegistry profiles;
    private final ImmersiveNpcRoleService roles;
    private final NpcRuntimeRegistry runtimes;
    private final VanillaNpcStatBaselineResolver baselines;
    private final NpcEquipmentStatSynchronizer equipmentStats;
    private final Consumer<String> log;
    private final Map<UUID, Attachment> active = new ConcurrentHashMap<>();
    private final Map<UUID, AddReason> additions = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<?>> pendingRemovals = new ConcurrentHashMap<>();
    private volatile boolean closing;
    private volatile boolean closed;

    private static final class Attachment {
        final NpcProfile profile;
        final UUID entity;
        final World world;
        final Ref<EntityStore> ref;
        final boolean fresh;
        volatile CompletableFuture<NpcStatStateRepository.Loaded> loaded;
        volatile CompletableFuture<NpcStatStateRepository.Lease> lease;
        CompletableFuture<Void> evidence;
        NpcStatHydration.Marker hydrated;
        Map<String, NpcStatSample> lastQueued = Map.of();
        CompletableFuture<?> lastSave;
        long nextCheckpoint;
        boolean failed;
        int readinessAttempts;
        Attachment(NpcProfile p, UUID id, World world, Ref<EntityStore> ref, boolean fresh) {
            profile = p; entity = id; this.world = world; this.ref = ref; this.fresh = fresh;
        }
    }
    public NpcStatRuntimeBridge(NpcStatStateRepository repository, NpcProfileRegistry profiles,
            ImmersiveNpcRoleService roles, NpcRuntimeRegistry runtimes, Consumer<String> log) {
        this.repository = repository; this.profiles = profiles; this.roles = roles; this.runtimes = runtimes;
        this.log = log; this.baselines = new VanillaNpcStatBaselineResolver(log);
        this.equipmentStats = new NpcEquipmentStatSynchronizer(log);
    }
    public NpcStatStateRepository repository() { return repository; }
    public void syncEquipment(UUID stableId, Ref<EntityStore> ref,
            Store<EntityStore> store, String trigger) {
        equipmentStats.synchronize(stableId, ref, store, trigger);
    }
    public static Query<EntityStore> query() {
        return Query.and(NPCEntity.getComponentType(), UUIDComponent.getComponentType(), Query.not(Player.getComponentType()));
    }
    public void added(Ref<EntityStore> ref, Store<EntityStore> store, AddReason reason) {
        var uuid = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuid != null) additions.put(uuid.getUuid(), reason);
        attach(ref, store, null);
    }
    private NpcProfile identity(Ref<EntityStore> ref, Store<EntityStore> store, NPCEntity npc, UUID uuid) {
        if (!HytaleNpcAdapter.isManagedRole(npc.getNPCTypeId())) return null;
        var byRole = roles.profileForRole(npc.getNPCTypeId());
        var display = store.getComponent(ref, PersistentDisplayName.getComponentType());
        var byName = display == null || display.getDisplayName() == null ? Optional.<NpcProfile>empty()
                : profiles.byName(display.getDisplayName().getRawText());
        if (byRole.isPresent() && byName.isPresent() && !byRole.get().stableId().equals(byName.get().stableId())) {
            log.accept("NPC_STATS_IDENTITY_CONFLICT entity=" + uuid + " roleAndNameDisagree=true"); return null;
        }
        // A generic-role NPC MUST have its own name binding. Never use the cognition default-profile fallback.
        return byRole.or(() -> byName).orElse(null);
    }
    private Attachment attach(Ref<EntityStore> ref, Store<EntityStore> store, NpcProfile expected) {
        if (closing || !ref.isValid() || store.getComponent(ref, Player.getComponentType()) != null) return null;
        var npc = store.getComponent(ref, NPCEntity.getComponentType());
        var uuid = store.getComponent(ref, UUIDComponent.getComponentType());
        if (npc == null || uuid == null) return null;
        var profile = identity(ref, store, npc, uuid.getUuid());
        if (profile == null || expected != null && !expected.stableId().equals(profile.stableId())) return null;
        Attachment a = active.computeIfAbsent(profile.stableId(), ignored -> new Attachment(profile, uuid.getUuid(),
                store.getExternalData().getWorld(), ref, additions.get(uuid.getUuid()) == AddReason.SPAWN));
        if (!a.entity.equals(uuid.getUuid()) || a.ref != ref) return null; // A duplicate cannot steal authority.
        var map = store.getComponent(ref, EntityStatMap.getComponentType());
        if (a.loaded == null && map != null && npc.getRole() != null) {
            var policy = new VanillaNpcStatBaselineResolver.RolePolicy(npc.getNPCTypeId(),
                    (double) npc.getRole().getInitialMaxHealth(), npc.getRole().isInvulnerable());
            var baseline = baselines.resolve(profile, policy);
            var live = NpcStatHydration.sample(map);
            var previousRemoval = pendingRemovals.getOrDefault(profile.stableId(), CompletableFuture.completedFuture(null));
            a.loaded = previousRemoval.thenCompose(ignored -> repository.ensure(profile, baseline, live, "MIGRATION_FROM_LIVE"));
            a.lease = a.loaded.thenCompose(ignored -> repository.bind(profile, uuid.getUuid()));
            log.accept("NPC_STATS_ATTACHED npc=" + profile.name() + " stableId=" + profile.stableId()
                    + " entity=" + uuid.getUuid() + " world=" + a.world.getWorldConfig().getUuid()
                    + " freshSpawn=" + a.fresh + " role=" + npc.getNPCTypeId());
        }
        return a;
    }
    /** Profile opening may request disk preparation, never hydrate or synthesize spawned currents. */
    public CompletableFuture<?> prepare(NpcProfile profile, Store<EntityStore> store, Ref<EntityStore> live, boolean create) {
        if (live != null) attach(live, store, profile);
        Attachment a = active.get(profile.stableId());
        if (a != null) return a.loaded == null ? CompletableFuture.failedFuture(
                new IllegalStateException("Native NPC stat map not ready; vitals unavailable")) : a.loaded;
        String role = roles.spawnRole(profile);
        return repository.ensure(profile, baselines.resolve(profile, roles.statPolicy(profile, role)),
                null, create ? "CREATE" : "MIGRATION_FROM_BASELINE");
    }
    public CompletableFuture<?> initializeCreated(NpcProfile profile) {
        return prepare(profile, null, null, true);
    }
    public void hydrate(Ref<EntityStore> ref, Store<EntityStore> store) {
        Attachment a = attach(ref, store, null);
        if (a == null || a.failed || a.hydrated != null) return;
        if (++a.readinessAttempts > 600) {
            a.failed = true;
            log.accept("NPC_STATS_ATTACH_DEFERRED npc=" + a.profile.name() + " reason=NATIVE_OR_DISK_AUTHORITY_NOT_READY boundedAttempts=600 livePreserved=true");
            return;
        }
        if (a.loaded == null || !a.lease.isDone()) return;
        try {
            var loaded = a.loaded.join(); // isDone lease implies completed load; never waits for disk on world.
            var lease = a.lease.join();
            if (!repository.owns(lease)) return;
            var map = store.getComponent(ref, EntityStatMap.getComponentType());
            if (map == null) return;
            // Reconcile native equipment before restoring the persistent current
            // value so Hytale clamps it against the same effective bounds used by
            // combat and the Profile snapshot.
            equipmentStats.synchronize(a.profile.stableId(), ref, store,
                    a.fresh ? "FRESH_SPAWN_HYDRATION" : "WORLD_RESTORE_HYDRATION");
            if (NpcStatHydration.sample(map).isEmpty() && !loaded.state().stats().isEmpty()) return;
            if (loaded.migratedFromLive()) {
                // Preserve any native damage/regen that occurred while the first migration was written.
                a.hydrated = new NpcStatHydration.Marker(a.profile.stableId(), a.entity, loaded.state().revision());
            } else {
                var saved = repository.cached(a.profile.stableId()).orElseThrow();
                var live = NpcStatHydration.sample(map);
                if (!a.fresh && NpcStatHydration.differs(saved, live)) {
                    if (a.evidence == null) a.evidence = repository.preserveRuntime(a.profile, a.entity, saved.revision(), live);
                    if (!a.evidence.isDone()) return;
                    a.evidence.join(); // Preserve first, mutate second. Failure leaves native state untouched.
                }
                a.hydrated = NpcStatHydration.applyOnce(saved, a.entity, map, null, log);
            }
            queue(a, NpcStatHydration.sample(map), "CHECKPOINT");
        } catch (RuntimeException failure) {
            a.failed = true;
            log.accept("NPC_STATS_ATTACH_FAILED npc=" + a.profile.name() + " livePreserved=true reason=" + failure);
        }
    }
    public void checkpoint(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (closing) return;
        var uuid = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuid == null) return;
        Attachment a = active.values().stream().filter(value -> value.entity.equals(uuid.getUuid())).findFirst().orElse(null);
        long now = System.nanoTime();
        if (a == null || a.hydrated == null || now < a.nextCheckpoint) return;
        a.nextCheckpoint = now + CHECKPOINT_NANOS;
        queue(a, NpcStatHydration.sample(store.getComponent(ref, EntityStatMap.getComponentType())), "CHECKPOINT");
    }
    private CompletableFuture<?> queue(Attachment a, Map<String, NpcStatSample> values, String reason) {
        if (a.lastQueued.equals(values) && a.lastSave != null && !a.lastSave.isCompletedExceptionally()) return a.lastSave;
        a.lastQueued = values;
        a.lastSave = repository.capture(a.profile, a.lease.join(), values, reason);
        a.lastSave.whenComplete((ignored, error) -> {
            if (error != null) log.accept("NPC_STATS_CAPTURE_FAILED npc=" + a.profile.name() + " retainedDirty=true reason=" + error);
        });
        return a.lastSave;
    }
    public void removed(Ref<EntityStore> ref, Store<EntityStore> store, RemoveReason reason) {
        if (closed) return; // Plugin shutdown already captured/flushed before unregistering its systems.
        var uuid = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuid == null) return;
        Attachment a = active.values().stream().filter(value -> value.entity.equals(uuid.getUuid())).findFirst().orElse(null);
        if (a != null) {
            var live = NpcStatHydration.sample(store.getComponent(ref, EntityStatMap.getComponentType()));
            String captureReason = reason == RemoveReason.UNLOAD ? "WORLD_UNLOAD" : "PRE_REMOVE";
            CompletableFuture<?> capture = CompletableFuture.completedFuture(null);
            if (a.hydrated != null) capture = queue(a, live, captureReason);
            else if (a.loaded != null) {
                capture = a.lease.thenCompose(lease -> {
                    if (a.loaded.join().migratedFromLive()) return repository.capture(a.profile, lease, live, captureReason).thenApply(x -> null);
                    return repository.preserveRuntime(a.profile, a.entity, a.loaded.join().state().revision(), live);
                });
            }
            CompletableFuture<?> finalCapture = capture;
            pendingRemovals.put(a.profile.stableId(), finalCapture);
            finalCapture.whenComplete((ignored, error) -> {
                if (error == null) pendingRemovals.remove(a.profile.stableId(), finalCapture);
                else log.accept("NPC_STATS_REMOVAL_CAPTURE_FAILED npc=" + a.profile.name() + " reason=" + error);
            });
            active.remove(a.profile.stableId(), a);
        }
        additions.remove(uuid.getUuid());
    }
    /** Called on world thread. Removal must be retried AFTER the latest immutable capture is durable. */
    public boolean durableForRemoval(NpcProfile profile, Ref<EntityStore> ref, Store<EntityStore> store) {
        Attachment a = attach(ref, store, profile);
        if (a == null || a.hydrated == null) throw new IllegalStateException("NPC stats not ready; removal safely deferred");
        var save = queue(a, NpcStatHydration.sample(store.getComponent(ref, EntityStatMap.getComponentType())), "PRE_REMOVE");
        if (!save.isDone()) return false;
        save.join();
        return true;
    }
    public boolean spawned(UUID stableId) { return active.containsKey(stableId); }
    public Optional<Boolean> invulnerable(UUID stableId, Store<EntityStore> store, Ref<EntityStore> live) {
        if (live != null && live.isValid() && store != null) {
            var npc = store.getComponent(live, NPCEntity.getComponentType());
            if (npc != null && npc.getRole() != null) return Optional.of(npc.getRole().isInvulnerable());
            return Optional.empty();
        }
        return profiles.profiles().stream().filter(p -> p.stableId().equals(stableId)).findFirst()
                .map(p -> roles.statPolicy(p, ProfileRepository.sanitizeProfileName(p.name())))
                .map(VanillaNpcStatBaselineResolver.RolePolicy::invulnerable);
    }
    /** Scan all worlds first, then initialize ONLY profiles without a loaded entity. */
    public void initializeUnspawned() {
        List<CompletableFuture<Void>> scans = new ArrayList<>();
        for (World world : Universe.get().getWorlds().values()) {
            var scan = new CompletableFuture<Void>(); scans.add(scan);
            world.execute(() -> {
                try { world.getEntityStore().getStore().forEachChunk(query(), (chunk, commands) -> {
                    for (int i = 0; i < chunk.size(); i++) attach(chunk.getReferenceTo(i), world.getEntityStore().getStore(), null);
                }); scan.complete(null); } catch (Throwable error) { scan.completeExceptionally(error); }
            });
        }
        CompletableFuture.allOf(scans.toArray(CompletableFuture[]::new)).thenRun(() -> {
            for (NpcProfile profile : profiles.profiles()) if (!active.containsKey(profile.stableId())) {
                String role = ProfileRepository.sanitizeProfileName(profile.name());
                repository.ensure(profile, baselines.resolve(profile, roles.statPolicy(profile, role)), null, "MIGRATION_FROM_BASELINE")
                        .exceptionally(error -> { log.accept("NPC_STATS_MIGRATION_FAILED npc=" + profile.name() + " reason=" + error); return null; });
            }
        }).exceptionally(error -> { log.accept("NPC_STATS_WORLD_SCAN_FAILED noOfflineMigration=true reason=" + error); return null; });
    }
    @Override public void close() {
        closing = true;
        List<CompletableFuture<Void>> captures = new ArrayList<>();
        for (Attachment a : List.copyOf(active.values())) {
            var captured = new CompletableFuture<Void>(); captures.add(captured);
            Runnable capture = () -> {
                try {
                    if (a.ref.isValid() && a.hydrated != null) {
                        var map = a.world.getEntityStore().getStore().getComponent(a.ref, EntityStatMap.getComponentType());
                        queue(a, NpcStatHydration.sample(map), "PLUGIN_SHUTDOWN");
                    }
                    captured.complete(null);
                } catch (Throwable failure) { captured.completeExceptionally(failure); }
            };
            if (a.world.getEntityStore().getStore().isInThread()) capture.run();
            else if (a.world.isAlive()) a.world.execute(capture);
            else { captured.complete(null); log.accept("NPC_STATS_WORLD_ALREADY_STOPPED entity=" + a.entity + " usingRemovalCapture=true"); }
        }
        try { CompletableFuture.allOf(captures.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS); }
        catch (Exception failure) { log.accept("NPC_STATS_SHUTDOWN_CAPTURE_INCOMPLETE reason=" + failure); }
        try { CompletableFuture.allOf(pendingRemovals.values().toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS); }
        catch (Exception failure) { log.accept("NPC_STATS_SHUTDOWN_REMOVAL_DRAIN_FAILED reason=" + failure); }
        repository.close();
        closed = true;
    }
}
