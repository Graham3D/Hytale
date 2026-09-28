package com.inigmasgames.hytalerpg.difficulty;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.inigmasgames.hytalerpg.progress.*;
import com.inigmasgames.hytalerpg.combat.cooldown.SavedCooldown;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.inigmasgames.hytalerpg.difficulty.DifficultyId.*;
import static com.inigmasgames.hytalerpg.difficulty.MonsterResistanceProfile.Channel.*;

class DifficultyFoundationTest {
    @TempDir Path directory;
    private WorldDifficultyRegistry worlds() { return new WorldDifficultyRegistry(directory.resolve("worlds.json")); }
    private WorldDifficultyRegistry.Binding binding(DifficultyId mode) {
        return new WorldDifficultyRegistry.Binding(UUID.randomUUID(), mode.name().toLowerCase(Locale.ROOT),
                WorldDifficultyRegistry.Kind.CAMPAIGN, mode, "fixture.v1", "server-character", false);
    }
    private EncounterProfileResolver.Profile profile(DifficultyId mode, MonsterResistanceProfile resistance) {
        return new EncounterProfileResolver.Profile("fixture." + mode, mode, "fixture.v1", "fixture_role", "fixture_biome",
                switch(mode) { case NORMAL -> 5; case NIGHTMARE -> 42; case HELL -> 65; },
                100, 20, 1, 1, resistance, EncounterProfileResolver.Evidence.FIXTURE_ONLY);
    }
    @Test void playerWorldToDifficultyToProfileToResolvedStatsIsSharedAndSurvivesRestart() throws Exception {
        var registry = worlds(); var authored = new ArrayList<EncounterProfileResolver.Profile>();
        for (var mode : DifficultyId.values()) { registry.register(binding(mode)); authored.add(profile(mode, MonsterResistanceProfile.NONE)); }
        var player = UUID.randomUUID(); var repo = new FileRpgPlayerStateRepository(directory.resolve("players"));
        var character = RpgPlayerState.create(player); character.level = 37; character.currentXp = 123456;
        character.equippedSkills[0] = "quick_slash"; character.learnedSkills.add("quick_slash");
        character.cooldowns.put("quick_slash", new SavedCooldown(12, .25)); repo.save(character);
        var resolver = new EncounterProfileResolver(registry, ProgressionProfiles.load(), authored);
        var proof = new ArrayList<Object>();
        for (var binding : registry.bindings()) {
            // Native PlayerRef supplies this world UUID. Character level/unlocks never enter monster resolution.
            UUID nativePlayerWorld = binding.worldId(); var enemy = UUID.randomUUID();
            var result = resolver.preview(nativePlayerWorld, enemy, "fixture_role", "fixture_biome");
            var factor = ProgressionProfiles.load().difficulty(binding.difficulty().name());
            assertEquals(100 * factor.healthMultiplier(), result.maxHealth(), 1e-9);
            assertEquals(20 * factor.damageMultiplier(), result.attackBasis(), 1e-9);
            assertEquals(binding.difficulty(), result.difficulty());
            assertEquals(result, resolver.preview(nativePlayerWorld, enemy, "fixture_role", "fixture_biome"));
            assertThrows(IllegalStateException.class, () -> resolver.resolveProduction(nativePlayerWorld, enemy, "fixture_role", "fixture_biome"));
            var restarted = new EncounterProfileResolver(worlds(), ProgressionProfiles.load(), authored);
            assertEquals(result, restarted.preview(nativePlayerWorld, enemy, "fixture_role", "fixture_biome"));
            var loaded = new FileRpgPlayerStateRepository(directory.resolve("players")).load(player).state();
            assertEquals(new Gson().toJson(character), new Gson().toJson(loaded));
            proof.add(Map.of("player", player, "nativePlayerWorld", nativePlayerWorld, "binding", binding, "monster", result));
        }
        // Retained build evidence labels these fixed-stat fixtures, not connected combat proof.
        Path evidence = Path.of("build/difficulty-stage-1/resolution-proof.json"); Files.createDirectories(evidence.getParent());
        Files.writeString(evidence, new Gson().toJson(proof));
    }
    @Test void sameWorldCannotHaveTwoDifficultiesAndCharactersCannotGetSeparateProgressionScopes() {
        var registry = worlds(); var normal = binding(NORMAL); registry.register(normal);
        assertSame(normal, registry.register(normal));
        assertThrows(IllegalStateException.class, () -> registry.register(new WorldDifficultyRegistry.Binding(normal.worldId(), normal.worldName(), normal.kind(), HELL, "fixture.v1", "server-character", false)));
        assertThrows(IllegalArgumentException.class, () -> registry.register(new WorldDifficultyRegistry.Binding(UUID.randomUUID(), "other", normal.kind(), NIGHTMARE, "fixture.v1", "per-world-player", false)));
        assertEquals(1, worlds().bindings().size());
    }
    @Test void unknownWorldAndUnwrittenProfileFailClosed() {
        var registry = worlds(); var binding = registry.register(binding(HELL));
        var resolver = new EncounterProfileResolver(registry, ProgressionProfiles.load(), List.of());
        assertThrows(IllegalStateException.class, () -> resolver.preview(UUID.randomUUID(), UUID.randomUUID(), "role", "biome"));
        assertThrows(IllegalStateException.class, () -> resolver.preview(binding.worldId(), UUID.randomUUID(), "role", "biome"));
    }
    @Test void hubNeverInheritsCampaignProfileFromVisitor() {
        var registry = worlds(); var hub = registry.register(new WorldDifficultyRegistry.Binding(UUID.randomUUID(), "hub", WorldDifficultyRegistry.Kind.SHARED_HUB, NORMAL, "fixture.v1", "server-character", true));
        var resolver = new EncounterProfileResolver(registry, ProgressionProfiles.load(), List.of(profile(NORMAL, MonsterResistanceProfile.NONE)));
        assertThrows(IllegalStateException.class, () -> resolver.preview(hub.worldId(), UUID.randomUUID(), "fixture_role", "fixture_biome"));
    }
    @Test void normalPilotRetainsExactRewardLevelsAndHarderWorldsDoNotFallBack() {
        var registry = worlds(); var enemies = EnemyRewardRegistry.load();
        var normal = registry.register(new WorldDifficultyRegistry.Binding(UUID.randomUUID(), "normal", WorldDifficultyRegistry.Kind.CAMPAIGN, NORMAL, enemies.profileId(), "server-character", true));
        var hell = registry.register(binding(HELL)); var resolver = new EncounterProfileResolver(registry, ProgressionProfiles.load(), List.of());
        for (var biome : enemies.biomes()) for (var role : enemies.roles()) {
            var enemy = UUID.randomUUID(); var origin = EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN;
            assertEquals(enemies.classify(normal.worldId(), enemy, role.roleId(), biome.key(), origin, 123),
                    resolver.classifyLegacyNormal(enemies, normal.worldId(), enemy, role.roleId(), biome.key(), origin, 123));
            assertTrue(resolver.classifyLegacyNormal(enemies, hell.worldId(), enemy, role.roleId(), biome.key(), origin, 123).isEmpty());
            assertTrue(resolver.classifyLegacyNormal(enemies, normal.worldId(), enemy, role.roleId(), biome.key(), EnemyRewardRegistry.Origin.SUMMONED, 123).isEmpty());
        }
    }
    @Test void resistanceCapAndImmunityAreIndependentPerChannel() {
        var resistance = new MonsterResistanceProfile(Map.of(FIRE, 1.0, COLD, .5), Set.of(LIGHTNING));
        assertEquals(.75, resistance.effective(FIRE)); assertEquals(25, resistance.resolve(FIRE, 100).amount());
        assertEquals(50, resistance.resolve(COLD, 100).amount()); assertEquals(100, resistance.resolve(WIND, 100).amount());
        assertEquals(0, resistance.effective(LIGHTNING)); assertEquals(0, resistance.resolve(LIGHTNING, 100).amount());
        assertEquals("AUTHORED_ELEMENTAL_IMMUNITY", resistance.resolve(LIGHTNING, 100).reason());
        assertFalse(resistance.immunities().contains(FIRE));
    }
    @Test void invalidOrUnknownChannelsAndNonfiniteNumbersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MonsterResistanceProfile(Map.of(FIRE, Double.NaN), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new MonsterResistanceProfile(Map.of(FIRE, -.1), Set.of()));
        assertThrows(RuntimeException.class, () -> new Gson().fromJson("{\"resistance\":{\"MADE_UP\":0.5},\"immunities\":[]}", MonsterResistanceProfile.class));
        assertThrows(IllegalArgumentException.class, () -> MonsterResistanceProfile.NONE.resolve(FIRE, Double.POSITIVE_INFINITY));
    }
    @Test void milestoneWritesAreIdempotentAndModeLedgersIndependent() {
        var p = DifficultyProgress.INITIAL.complete(NORMAL, "fixture.earth");
        assertSame(p, p.complete(NORMAL, "fixture.earth"));
        assertFalse(p.unlocked(NIGHTMARE));
        assertThrows(IllegalStateException.class, () -> p.unlockNext(NORMAL, Set.of()));
        assertThrows(IllegalStateException.class, () -> p.unlockNext(NORMAL, Set.of("fixture.flame")));
        var nightmare = p.unlockNext(NORMAL, Set.of("fixture.earth"));
        assertTrue(nightmare.unlocked(NIGHTMARE)); assertFalse(nightmare.unlocked(HELL));
        assertTrue(nightmare.milestones().get(NIGHTMARE).isEmpty());
        assertThrows(IllegalStateException.class, () -> nightmare.unlockNext(NIGHTMARE, Set.of("fixture.earth")));
        var hell = nightmare.complete(NIGHTMARE, "fixture.earth").unlockNext(NIGHTMARE, Set.of("fixture.earth"));
        assertTrue(hell.unlocked(HELL)); assertTrue(DifficultyProgress.INITIAL.milestones().get(NORMAL).isEmpty());
    }
    @Test void migrationPreservesAllExistingFieldsAndOnlyAddsEmptyDifficultyLedger() {
        var state = RpgPlayerState.create(UUID.randomUUID()); state.level = 37; state.currentXp = 123456;
        state.cooldowns.put("quick_slash", new SavedCooldown(17, .5)); state.learnedSkills.add("quick_slash");
        var old = new Gson().toJsonTree(state).getAsJsonObject(); old.remove("difficulty"); old.remove("gearEconomy"); old.addProperty("schemaVersion", 9);
        var migrated = new RpgStateMigrator().migrate(old);
        for (var entry : old.entrySet()) if (!entry.getKey().equals("schemaVersion")) assertEquals(entry.getValue(), migrated.state().get(entry.getKey()), entry.getKey());
        assertEquals(DifficultyProgress.INITIAL, new Gson().fromJson(migrated.state().get("difficulty"), DifficultyProgress.class));
        assertFalse(new RpgStateMigrator().migrate(migrated.state()).migrated());
    }
    @Test void playerLedgersPersistUnlocksWithoutResettingCooldownsOnRestart() {
        var player = UUID.randomUUID(); var repo = new FileRpgPlayerStateRepository(directory.resolve("players")); var state = RpgPlayerState.create(player);
        state.difficulty = state.difficulty.complete(NORMAL, "fixture.earth").unlockNext(NORMAL, Set.of("fixture.earth"));
        state.cooldowns.put("quick_slash", new SavedCooldown(7, .3)); repo.save(state);
        var loaded = new FileRpgPlayerStateRepository(directory.resolve("players")).load(player).state();
        assertEquals(state.difficulty, loaded.difficulty); assertEquals(state.cooldowns, loaded.cooldowns);
        assertSame(loaded.difficulty, loaded.difficulty.complete(NORMAL, "fixture.earth"));
    }
    @Test void corruptCurrentPlayerLedgerIsNeverResetByGsonDefaults() throws Exception {
        var player = UUID.randomUUID(); var repo = new FileRpgPlayerStateRepository(directory.resolve("players"));
        var json = new Gson().toJsonTree(RpgPlayerState.create(player)).getAsJsonObject(); json.remove("difficulty");
        Files.createDirectories(repo.path(player).getParent()); Files.writeString(repo.path(player), json.toString());
        assertThrows(IllegalStateException.class, () -> repo.load(player));
    }
    @Test void corruptWorldRegistryIsNeverResetOrSilentlyReclassified() throws Exception {
        var registry = worlds(); registry.register(binding(HELL));
        Path path = directory.resolve("worlds.json"); Files.writeString(path, Files.readString(path).replace("HELL", "NORMAL"));
        assertThrows(IllegalStateException.class, this::worlds);
    }
    @Test void failedWorldWriteNeverPublishesAnInMemoryBinding() throws Exception {
        Path blocked = directory.resolve("blocked"); Files.writeString(blocked, "file");
        var registry = new WorldDifficultyRegistry(blocked.resolve("worlds.json"));
        assertThrows(IllegalStateException.class, () -> registry.register(binding(NIGHTMARE)));
        assertTrue(registry.bindings().isEmpty());
    }
    @Test void regionDefinitionsAndScalarsDoNotPretendToEnableHigherContent() {
        var definitions = DifficultyDefinitions.load();
        assertTrue(definitions.band("emerald_wilds", NIGHTMARE).contains(42));
        assertTrue(definitions.band("devastated_lands", HELL).contains(99));
        assertFalse(definitions.scalar(NIGHTMARE).enabled()); assertFalse(definitions.scalar(HELL).enabled());
        assertEquals(1, definitions.scalar(NORMAL).healthMultiplier());
        assertEquals(40, NIGHTMARE.recommendedLevel()); assertEquals(60, HELL.recommendedLevel());
    }
    @Test void unallocatedRoleBaselinesCannotProduceInventedStats() {
        assertThrows(IllegalArgumentException.class, () -> new EncounterProfileResolver.Profile("bad", HELL, "fixture.v1", "role", "biome", 60, 0, 20, 1, 1, MonsterResistanceProfile.NONE, EncounterProfileResolver.Evidence.FIXTURE_ONLY));
        var registry = worlds(); var binding = registry.register(binding(NORMAL)); var p = profile(NORMAL, MonsterResistanceProfile.NONE);
        assertThrows(IllegalArgumentException.class, () -> new EncounterProfileResolver(registry, ProgressionProfiles.load(), List.of(p,p)));
    }
    @Test void startupImportsUnloadedNativeWorldsAtomicallyAndPreservesIdentityAcrossRestart() throws Exception {
        Path nativeWorlds = directory.resolve("universe/worlds"); var ids = new ArrayList<UUID>();
        for (String name : List.of("default", "flat_world", "zone3_taiga1_world")) {
            UUID id = UUID.randomUUID(); ids.add(id); Path folder = nativeWorlds.resolve(name); Files.createDirectories(folder);
            var config = new org.bson.BsonDocument("UUID", com.hypixel.hytale.codec.Codec.UUID_BINARY.encode(id, new com.hypixel.hytale.codec.ExtraInfo()));
            Files.writeString(folder.resolve("config.json"), config.toJson());
        }
        Path registry = directory.resolve("native-worlds.json"); var runtime = new DifficultyRuntime(registry, nativeWorlds);
        assertEquals(3, runtime.worlds().bindings().size());
        for (UUID id : ids) assertEquals(NORMAL, runtime.currentWorld(id).difficulty());
        var restarted = new DifficultyRuntime(registry, nativeWorlds);
        for (UUID id : ids) assertEquals(runtime.currentWorld(id), restarted.currentWorld(id));
        assertTrue(restarted.worldLoaded(UUID.randomUUID(), "unregistered_dungeon").isEmpty());
        assertThrows(IllegalStateException.class, () -> restarted.worldLoaded(ids.getFirst(), "renamed_world"));
    }
    @Test void freshDefaultWorldRegistersOnceAndRetainsPersistentDifficulty() {
        Path registry = directory.resolve("fresh.json"); var runtime = new DifficultyRuntime(registry, directory.resolve("empty"));
        UUID world = UUID.randomUUID(); assertEquals(NORMAL, runtime.worldLoaded(world, "default").orElseThrow().difficulty());
        var restarted = new DifficultyRuntime(registry, directory.resolve("empty"));
        assertEquals(runtime.currentWorld(world), restarted.currentWorld(world));
        assertTrue(restarted.worldLoaded(UUID.randomUUID(), "default").isEmpty());
    }
}
