package com.inigmasgames.persistentnpcs.stats;

import com.google.gson.JsonObject;
import com.hypixel.hytale.protocol.EntityStatResetBehavior;
import com.hypixel.hytale.server.core.modules.entitystats.*;
import com.hypixel.hytale.server.core.modules.entitystats.asset.*;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.profile.*;
import com.inigmasgames.persistentnpcs.hytale.ImmersiveNpcRoleService;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipFile;

/** Real repository + installed SDK stat values and mutation path. No player/world files touched. */
public final class R149PersistentVanillaStatsTest {
    private static final List<String> LOG = new CopyOnWriteArrayList<>();
    private static Map<String, EntityStatType> definitions;
    private static final Map<String, Integer> INDEX = Map.of("Health", 2, "Stamina", 5, "Mana", 7);
    public static void main(String[] args) throws Exception {
        try {
            definitions = installedDefinitions();
            // Tests emulate the installed registry assigning NON-contiguous runtime indexes.
            for (var e : INDEX.entrySet()) {
                var field = DefaultEntityStatTypes.class.getDeclaredField(e.getKey().toUpperCase(Locale.ROOT));
                field.setAccessible(true); field.setInt(null, e.getValue());
            }
            var assetStore = new FixtureStore.FixtureBuilder().setCodec(EntityStatType.CODEC).setKeyFunction(EntityStatType::getId).setPath("Entity/Stats").build();
            var storeField = EntityStatType.class.getDeclaredField("ASSET_STORE");
            storeField.setAccessible(true); storeField.set(null, assetStore);
            Path root = Files.createTempDirectory("r149-stat-foundation-");
            try {
                baselineAndRoles(root.resolve("roles"));
                createCloseUpdate(root.resolve("create"));
                repositoryLifecycle(root.resolve("lifecycle"));
                corruptionAndForwardCompatibility(root.resolve("corruption"));
                nativeHydrationAndIsolation(root.resolve("native"));
                writeFailureAndRetirement(root.resolve("failure"));
                coalescedBacklog(root.resolve("coalescing"));
                wiringContracts();
            } finally {
                try (var paths = Files.walk(root)) {
                    for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
                }
            }
            System.out.println("R149 S1 PASS: installed baselines/roles, live migration, create/restart, ordered captures, conflict preservation, native hydration/clamps/idempotence, saved/live display, player/NPC isolation, failed-write recovery, deletion barrier, lifecycle wiring.");
        } catch (Throwable failure) { failure.printStackTrace(System.out); throw failure; }
    }
    private static Map<String, EntityStatType> installedDefinitions() throws Exception {
        var result = new TreeMap<String, EntityStatType>();
        try (var zip = new ZipFile(Path.of(System.getenv("APPDATA"),
                "Hytale/install/release/package/game/latest/Assets.zip").toFile())) {
            for (String id : VanillaNpcStats.IDS) {
                var entry = zip.getEntry("Server/Entity/Stats/" + id + ".json");
                try (var reader = new java.io.InputStreamReader(zip.getInputStream(entry), java.nio.charset.StandardCharsets.UTF_8)) {
                    JsonObject json = JsonFiles.GSON.fromJson(reader, JsonObject.class);
                    result.put(id, new EntityStatType(id, json.get("InitialValue").getAsInt(),
                            json.get("Min").getAsInt(), json.get("Max").getAsInt(), true,
                            null, null, null, EntityStatResetBehavior.MaxValue));
                }
            }
        }
        return Map.copyOf(result);
    }
    private static Map<String, NpcStatRecord> baseline(NpcProfile p) {
        return new VanillaNpcStatBaselineResolver(definitions::get, LOG::add).resolve(p,
                new VanillaNpcStatBaselineResolver.RolePolicy(p.name(), 100.0, true));
    }
    private static void baselineAndRoles(Path root) throws Exception {
        var profiles = new ProfileRepository(root);
        var p = profiles.createTemplate("Hoit");
        var base = baseline(p);
        assert base.size() == 3;
        assert base.get("Health").current() == definitions.get("Health").getInitialValue();
        assert base.get("Stamina").baseMin() == -4 && base.get("Mana").baseMax() == 0;
        var lowerRole = new VanillaNpcStatBaselineResolver(definitions::get, LOG::add).resolve(p,
                new VanillaNpcStatBaselineResolver.RolePolicy("LowHealthRole", 65.0, false));
        assert lowerRole.get("Health").current() == 65 && lowerRole.get("Health").baseMax() == 65;
        var missing = new VanillaNpcStatBaselineResolver(id -> id.equals("Mana") ? null : definitions.get(id), LOG::add)
                .resolve(p, new VanillaNpcStatBaselineResolver.RolePolicy("Hoit", 100.0, true));
        assert !missing.containsKey("Mana") && missing.size() == 2;
        assert LOG.stream().anyMatch(s -> s.contains("NPC_STATS_MISSING_ASSET"));
        var registry = new NpcProfileRegistry(profiles); registry.register(p);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var roles = new ImmersiveNpcRoleService(root, registry, (name, file) -> calls.incrementAndGet(), LOG::add);
        assert roles.spawnRole(p).equals("Hoit") && calls.get() == 1;
        byte[] original = Files.readAllBytes(roles.roleFile("Hoit"));
        roles.registerOrUpdate(p); assert calls.get() == 1;
        assert Arrays.equals(original, Files.readAllBytes(roles.roleFile("Hoit")));
        var custom = JsonFiles.read(roles.roleFile("Hoit"), JsonObject.class);
        custom.addProperty("MaxHealth", 75); JsonFiles.writeAtomic(roles.roleFile("Hoit"), custom);
        roles.registerOrUpdate(p);
        assert calls.get() == 2 && roles.statPolicy(p, roles.spawnRole(p)).maxHealth() == 75;
        assert JsonFiles.read(roles.roleFile("Hoit"), JsonObject.class).get("MaxHealth").getAsInt() == 75;
        assert new NpcProfileRegistry(profiles).byName("Other").isEmpty();
    }
    private static void repositoryLifecycle(Path root) throws Exception {
        var profiles = new ProfileRepository(root);
        var p = profiles.createTemplate("Hoit"); var other = profiles.createTemplate("Mara");
        long revision;
        try (var repo = new NpcStatStateRepository(profiles, LOG::add)) {
            var created = repo.ensure(p, baseline(p), null, "CREATE").get();
            assert !created.migratedFromLive() && created.state().revision() == 1;
            assert created.state().captureReason().equals("CREATE") && Files.isRegularFile(repo.path(p));
            var repeated = repo.ensure(p, baseline(p), null, "CREATE").get();
            assert repeated.state().equals(created.state());
            var lease = repo.bind(p, UUID.randomUUID()).get();
            var injured = samples(37, -2, 0);
            var observed = repo.capture(p, lease, injured, "CHECKPOINT").get();
            assert observed.stats().get("Health").current() == 37;
            assert observed.stats().get("Stamina").current() == -2;
            byte[] bytes = Files.readAllBytes(repo.path(p));
            var unchanged = repo.capture(p, lease, injured, "CHECKPOINT").get();
            assert observed.equals(unchanged) && Arrays.equals(bytes, Files.readAllBytes(repo.path(p)));
            var older = repo.capture(p, lease, samples(28, 1, 0), "CHECKPOINT");
            var finalCapture = repo.capture(p, lease, samples(0, -4, 0), "PRE_REMOVE");
            older.get(); var removed = finalCapture.get(); repo.flush().get();
            assert removed.stats().get("Health").current() == 0 && removed.captureReason().equals("PRE_REMOVE");
            revision = removed.revision();
            var otherLive = repo.ensure(other, baseline(other), samples(12, 3, 0), "MIGRATION_FROM_LIVE").get();
            assert otherLive.migratedFromLive() && otherLive.state().stats().get("Health").current() == 12;
            assert otherLive.state().stats().get("Health").baseMax() == 100;
            expectFailure(() -> repo.capture(other, lease, samples(99, 10, 0), "CHECKPOINT").get());
            assert repo.cached(other.stableId()).orElseThrow().stats().get("Health").current() == 12;
            // Reopening Profile while spawned never switches the record to baseline max/current.
            assert repo.ensure(other, baseline(other), null, "CREATE").get().state().equals(otherLive.state());
            var newLease = repo.bind(p, UUID.randomUUID()).get();
            expectFailure(() -> repo.capture(p, lease, samples(100, 10, 0), "CHECKPOINT").get());
            assert repo.owns(newLease) && !repo.owns(lease);
            assert repo.cached(p.stableId()).orElseThrow().stats().get("Health").current() == 0;
        }
        try (var restart = new NpcStatStateRepository(profiles, LOG::add)) {
            var saved = restart.ensure(p, baseline(p), null, "MIGRATION_FROM_BASELINE").get().state();
            assert saved.revision() == revision && saved.stats().get("Health").current() == 0;
            assert saved.stats().get("Stamina").current() == -4;
            var otherSaved = restart.ensure(other, baseline(other), null, "MIGRATION_FROM_BASELINE").get().state();
            assert otherSaved.stats().get("Health").current() == 12;
            var display = new NpcStatsSnapshotService().captureSaved(p.stableId(), new SimpleItemContainer((short) 4),
                    saved, UUID.randomUUID(), 2, 4);
            assert display.npcEntityUuid() == null && display.health().orElseThrow().current() == 0;
            assert display.health().orElseThrow().maximum() == 100 && display.mana().orElseThrow().maximum() == 0;
            var wrong = new NpcStatsSnapshotService().captureSaved(other.stableId(), new SimpleItemContainer((short) 4),
                    saved, UUID.randomUUID(), 2, 4);
            assert wrong.health().isEmpty();
        }
    }
    private static void createCloseUpdate(Path root) throws Exception {
        var profiles = new ProfileRepository(root); var registry = new NpcProfileRegistry(profiles);
        var roles = new ImmersiveNpcRoleService(root, registry, (name, file) -> { }, LOG::add);
        var inventories = new NpcInventoryRepository(profiles);
        var editor = new NpcProfileEditorService(profiles, registry, new AppearanceRepository(root, LOG::add), inventories);
        UUID stable;
        try (var bridge = new NpcStatRuntimeBridge(new NpcStatStateRepository(profiles, LOG::add), registry,
                roles, new com.inigmasgames.persistentnpcs.hytale.NpcRuntimeRegistry(), LOG::add)) {
            editor.configurePersistentStats(bridge);
            editor.beginCreate("Hoit");
            bridge.repository().flush().get();
            var profile = editor.currentProfile("Hoit").orElseThrow(); stable = profile.stableId();
            var created = bridge.repository().cached(stable).orElseThrow();
            assert created.stats().size() == 3 && created.captureReason().equals("CREATE");
            assert created.stableNpcId().equals(registry.requireName("Hoit").stableId());
            byte[] original = Files.readAllBytes(bridge.repository().path(profile));
            editor.requireExisting("Hoit");
            bridge.prepare(profile, null, null, false).get();
            assert Arrays.equals(original, Files.readAllBytes(bridge.repository().path(profile)));
            assert bridge.invulnerable(stable, null, null).orElseThrow();
        }
        inventories.close();
        var restartedProfiles = new ProfileRepository(root);
        var restartedProfile = restartedProfiles.load("Hoit");
        assert restartedProfile.stableId().equals(stable);
        try (var restarted = new NpcStatStateRepository(restartedProfiles, LOG::add)) {
            var state = restarted.ensure(restartedProfile, baseline(restartedProfile), null, "CREATE").get().state();
            assert state.stableNpcId().equals(stable) && state.revision() == 1 && state.stats().size() == 3;
        }
    }
    private static void coalescedBacklog(Path root) throws Exception {
        var profiles = new ProfileRepository(root); var p = profiles.createTemplate("Backlog");
        try (var repo = new NpcStatStateRepository(profiles, LOG::add)) {
            repo.ensure(p, baseline(p), null, "CREATE").get();
            UUID entity = UUID.randomUUID(); var lease = repo.bind(p, entity).get();
            var field = NpcStatStateRepository.class.getDeclaredField("writer"); field.setAccessible(true);
            var writer = (ScheduledExecutorService) field.get(repo);
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            writer.execute(() -> { entered.countDown(); try { release.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
            assert entered.await(5, TimeUnit.SECONDS);
            CompletableFuture<NpcStatState> first = null, last = null;
            try {
                for (int i = 0; i < 1000; i++) {
                    last = repo.capture(p, lease, samples(i % 50, 2, 0), i == 999 ? "PRE_REMOVE" : "CHECKPOINT");
                    if (first == null) first = last;
                    assert first == last : "Backlog must use one bounded coalesced completion per NPC";
                }
            } finally { release.countDown(); }
            var saved = last.get(5, TimeUnit.SECONDS);
            assert saved.stats().get("Health").current() == 49 && saved.revision() == 2;
            assert saved.captureReason().equals("PRE_REMOVE");
            var reattached = repo.bind(p, entity).get(); // Same UUID, different attachment lifecycle.
            assert !reattached.token().equals(lease.token());
            expectFailure(() -> repo.capture(p, lease, samples(100, 10, 0), "CHECKPOINT").get());
        }
    }
    private static void corruptionAndForwardCompatibility(Path root) throws Exception {
        var profiles = new ProfileRepository(root);
        var p = profiles.createTemplate("Conflicted");
        Path file = profiles.profileDirectory(p.name()).resolve("npc-stats.json");
        for (String corrupt : new String[] { "", "{bad json", "{\"schemaVersion\":1}" }) {
            Files.writeString(file, corrupt);
            try (var repo = new NpcStatStateRepository(profiles, LOG::add)) {
                var recovered = repo.ensure(p, baseline(p), samples(22, -1, 0), "MIGRATION_FROM_LIVE").get();
                assert recovered.state().stats().get("Health").current() == 22;
                try (var paths = Files.list(file.getParent())) {
                    assert paths.filter(f -> f.getFileName().toString().startsWith("npc-stats.conflict-"))
                            .anyMatch(f -> { try { return Files.readString(f).equals(corrupt); } catch (Exception e) { throw new RuntimeException(e); } });
                }
            }
        }
        var doc = JsonFiles.read(file, JsonObject.class);
        doc.addProperty("stableNpcId", UUID.randomUUID().toString());
        JsonFiles.writeAtomic(file, doc);
        try (var repo = new NpcStatStateRepository(profiles, LOG::add)) {
            var repaired = repo.ensure(p, baseline(p), samples(19, 2, 0), "MIGRATION_FROM_LIVE").get().state();
            assert repaired.stableNpcId().equals(p.stableId()) && repaired.stats().get("Health").current() == 19;
        }
        doc = JsonFiles.read(file, JsonObject.class);
        JsonObject future = new JsonObject(); future.addProperty("futureMeaning", "preserve this, not a vanilla vital");
        doc.getAsJsonObject("stats").add("FutureStat", future);
        doc.addProperty("futureTopLevel", "also preserved");
        JsonFiles.writeAtomic(file, doc);
        try (var repo = new NpcStatStateRepository(profiles, LOG::add)) {
            var loaded = repo.ensure(p, baseline(p), null, "MIGRATION_FROM_BASELINE").get().state();
            assert !loaded.stats().containsKey("FutureStat") && loaded.stats().size() == 3;
            var lease = repo.bind(p, UUID.randomUUID()).get();
            repo.capture(p, lease, samples(17, 4, 0), "WORLD_UNLOAD").get();
            repo.preserveRuntime(p, lease.entityId(), loaded.revision(), samples(81, 6, 0)).get();
            var persisted = JsonFiles.read(file, JsonObject.class);
            assert persisted.getAsJsonObject("stats").getAsJsonObject("FutureStat").equals(future);
            assert persisted.get("futureTopLevel").getAsString().equals("also preserved");
            try (var paths = Files.list(file.getParent())) {
                var evidence = paths.filter(f -> f.getFileName().toString().startsWith("npc-stats.runtime-conflict-")).findFirst().orElseThrow();
                assert JsonFiles.read(evidence, JsonObject.class).getAsJsonObject("stats").getAsJsonObject("Health").get("current").getAsInt() == 81;
            }
        }
        // Future schemas fail closed rather than being downgraded.
        doc = JsonFiles.read(file, JsonObject.class); doc.addProperty("schemaVersion", 99); JsonFiles.writeAtomic(file, doc);
        byte[] original = Files.readAllBytes(file);
        try (var repo = new NpcStatStateRepository(profiles, LOG::add)) {
            expectFailure(() -> repo.ensure(p, baseline(p), null, "CREATE").get());
            assert Arrays.equals(original, Files.readAllBytes(file));
        }
        for (double value : new double[] { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY })
            expectFailure(() -> new NpcStatRecord(value, 1, 0, 100, 0, 100, "TEST"));
    }
    private static void nativeHydrationAndIsolation(Path root) throws Exception {
        var profiles = new ProfileRepository(root); var p = profiles.createTemplate("Native");
        EntityStatMap npc = nativeMap(); EntityStatMap player = nativeMap(); EntityStatMap secondNpc = nativeMap();
        var playerBefore = NpcStatHydration.sample(player); var secondBefore = NpcStatHydration.sample(secondNpc);
        npc.setStatValue(EntityStatMap.Predictable.NONE, VanillaNpcStats.index("Health"), 31);
        assert NpcStatHydration.sample(npc).get("Health").current() == 31;
        try (var repo = new NpcStatStateRepository(profiles, LOG::add)) {
            var loaded = repo.ensure(p, baseline(p), NpcStatHydration.sample(npc), "MIGRATION_FROM_LIVE").get();
            assert loaded.migratedFromLive() && loaded.state().stats().get("Health").current() == 31;
            UUID entity = UUID.randomUUID();
            npc.setStatValue(EntityStatMap.Predictable.NONE, VanillaNpcStats.index("Health"), 100);
            var marker = NpcStatHydration.applyOnce(loaded.state(), entity, npc, null, LOG::add);
            assert npc.get(VanillaNpcStats.index("Health")).get() == 31;
            // Native damage after hydration, then Profile reopen/refresh must NOT heal it.
            npc.subtractStatValue(EntityStatMap.Predictable.NONE, VanillaNpcStats.index("Health"), 8);
            NpcStatHydration.applyOnce(loaded.state(), entity, npc, marker, LOG::add);
            assert npc.get(VanillaNpcStats.index("Health")).get() == 23;
            var extreme = new TreeMap<>(loaded.state().stats());
            extreme.put("Health", new NpcStatRecord(500, 100, 0, 100, 0, 500, "TEST"));
            extreme.put("Stamina", new NpcStatRecord(-20, 10, -4, 10, -20, 10, "TEST"));
            var bounds = new NpcStatState(1, p.stableId(), 3, java.time.Instant.now().toString(), "CHECKPOINT", extreme);
            NpcStatHydration.applyOnce(bounds, UUID.randomUUID(), npc, null, LOG::add);
            assert npc.get(VanillaNpcStats.index("Health")).get() == 100;
            assert npc.get(VanillaNpcStats.index("Stamina")).get() == -4;
            assert !npc.getSelfUpdates().isEmpty() || !npc.otherUpdates.isEmpty() : "Native set must produce stat update semantics";
            assert playerBefore.equals(NpcStatHydration.sample(player)) && secondBefore.equals(NpcStatHydration.sample(secondNpc));
            expectFailure(() -> NpcStatHydration.applyOnce(loaded.state(), UUID.randomUUID(), npc, marker, LOG::add));
        }
    }
    private static EntityStatMap nativeMap() throws Exception {
        var map = new EntityStatMap(); var values = new EntityStatValue[8];
        for (var e : INDEX.entrySet()) values[e.getValue()] = new EntityStatValue(e.getValue(), definitions.get(e.getKey()));
        // Fixture initialization only. All production reads/mutations below use the actual SDK methods.
        var field = EntityStatMap.class.getDeclaredField("values"); field.setAccessible(true); field.set(map, values);
        return map;
    }
    private static final class FixtureMap extends com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap<String, EntityStatType> {
        FixtureMap() { super(EntityStatType[]::new); }
        @Override public EntityStatType getAsset(int index) {
            return INDEX.entrySet().stream().filter(e -> e.getValue() == index).map(e -> definitions.get(e.getKey())).findFirst().orElse(null);
        }
    }
    private static final class FixtureStore extends com.hypixel.hytale.assetstore.AssetStore<String, EntityStatType,
            com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap<String, EntityStatType>> {
        private static final class FixtureBuilder extends Builder<String, EntityStatType,
                com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap<String, EntityStatType>, FixtureBuilder> {
            FixtureBuilder() {
                super(String.class, EntityStatType.class, new FixtureMap());
                setReplaceOnRemove(id -> EntityStatType.UNKNOWN); setIsUnknown(EntityStatType::isUnknown);
            }
            @Override public FixtureStore build() { return new FixtureStore(this); }
        }
        FixtureStore(FixtureBuilder builder) { super(builder); }
        @Override protected com.hypixel.hytale.event.IEventBus getEventBus() { return null; }
        @Override public void addFileMonitor(String pack, Path path) { }
        @Override public void removeFileMonitor(Path path) { }
        @Override protected void handleRemoveOrUpdate(Set<String> keys, Map<String, EntityStatType> assets,
                com.hypixel.hytale.assetstore.AssetUpdateQuery query) { }
    }
    private static void writeFailureAndRetirement(Path root) throws Exception {
        var profiles = new ProfileRepository(root); var p = profiles.createTemplate("Durable");
        try (var repo = new NpcStatStateRepository(profiles, LOG::add)) {
            var original = repo.ensure(p, baseline(p), null, "CREATE").get().state();
            var lease = repo.bind(p, UUID.randomUUID()).get();
            Path file = repo.path(p); Path preserved = file.resolveSibling("test-original.json");
            Files.move(file, preserved); Files.createDirectory(file); Files.writeString(file.resolve("block"), "prevent atomic replace");
            expectFailure(() -> repo.capture(p, lease, samples(14, 5, 0), "PRE_REMOVE").get());
            assert repo.cached(p.stableId()).orElseThrow().revision() == original.revision();
            Files.delete(file.resolve("block")); Files.delete(file); Files.move(preserved, file);
            repo.flush().get();
            assert repo.cached(p.stableId()).orElseThrow().stats().get("Health").current() == 14;
            repo.retire(p).get(); byte[] before = Files.readAllBytes(file);
            expectFailure(() -> repo.capture(p, lease, samples(100, 10, 0), "CHECKPOINT").get());
            assert Arrays.equals(before, Files.readAllBytes(file));
        }
    }
    private static void wiringContracts() throws Exception {
        assert EntityStatsSystems.StatModifyingSystem.class.isAssignableFrom(NpcStatHydrationSystem.class);
        assert !EntityStatsSystems.StatModifyingSystem.class.isAssignableFrom(NpcStatCheckpointSystem.class);
        String root = "src/main/java/com/inigmasgames/persistentnpcs/";
        String bridge = Files.readString(Path.of(root + "stats/NpcStatRuntimeBridge.java"));
        assert bridge.contains("Query.not(Player.getComponentType())") && !bridge.contains("defaultProfile()");
        assert bridge.contains("a.loaded.join().migratedFromLive()") && bridge.contains("WORLD_UNLOAD");
        assert bridge.contains("PLUGIN_SHUTDOWN") && bridge.contains("repository.close()");
        assert bridge.contains("if (loaded.migratedFromLive())") && bridge.contains("a.hydrated != null");
        assert bridge.contains("a.evidence.join()") && bridge.contains("durableForRemoval");
        String command = Files.readString(Path.of(root + "command/AbstractImmersiveNpcProfileCommand.java"));
        assert command.contains("editor.persistentStats().prepare(") && command.contains("removeNpcAsync(");
        assert command.contains("repository().retire(profile)");
        String page = Files.readString(Path.of(root + "ui/NpcProfilePage.java"));
        assert page.contains("statsService.captureSaved(") && page.contains("SAVED:") && page.contains("LIVE:");
        assert !page.contains("NpcConfiguredVitals.read") && !page.contains("setStatValue(");
        String plugin = Files.readString(Path.of(root + "PersistentNpcsPlugin.java"));
        assert plugin.contains("NpcStatHydrationSystem(npcStats)") && plugin.contains("NpcStatRemovalCaptureSystem(npcStats)");
        assert plugin.contains("npcStats.initializeUnspawned()") && plugin.contains("npcStats.close()");
        for (String file : List.of("stats/NpcStatHydration.java", "profile/NpcStatsSnapshotService.java")) {
            String source = Files.readString(Path.of(root + file));
            assert !source.contains("map.get(id)") && !source.contains("new EntityStatMap(");
        }
        assert NpcStatRuntimeBridge.CHECKPOINT_NANOS >= TimeUnit.SECONDS.toNanos(1);
        String state = JsonFiles.GSON.toJson(new NpcStatState(1, UUID.randomUUID(), 1,
                java.time.Instant.now().toString(), "CREATE", Map.of()));
        for (String forbidden : List.of("Strength", "Dexterity", "Wisdom", "Defense", "level", "modifiers")) assert !state.contains(forbidden);
    }
    private static Map<String, NpcStatSample> samples(double h, double s, double m) {
        return Map.of("Health", new NpcStatSample(h, 0, 100), "Stamina", new NpcStatSample(s, -4, 10),
                "Mana", new NpcStatSample(m, 0, 0));
    }
    private interface Work { void run() throws Exception; }
    private static void expectFailure(Work work) throws Exception {
        try { work.run(); } catch (Exception expected) { return; }
        throw new AssertionError("Expected fail-closed operation");
    }
}
