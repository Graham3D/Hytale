package com.inigmasgames.hytalerpg.difficulty;

import com.inigmasgames.hytalerpg.progress.*;
import java.nio.file.*;
import java.util.*;

/** Stage 1 composition root. World loading registers identity before encounter tracking starts. */
public final class DifficultyRuntime {
    private final WorldDifficultyRegistry worlds;
    private final EncounterProfileResolver encounters;
    private final Set<String> legacyWorldNames;
    private final String legacyProfile = EnemyRewardRegistry.load().profileId();
    public DifficultyRuntime(Path registryPath, Path nativeWorldsPath) {
        boolean firstMigration = !Files.exists(registryPath);
        worlds = new WorldDifficultyRegistry(registryPath);
        DifficultyDefinitions.load(); // Reject malformed test definitions at startup, even while content stays gated.
        encounters = AuthoredEncounterCatalog.load().resolver(worlds);
        Set<String> legacy = new HashSet<>();
        if (firstMigration && Files.isDirectory(nativeWorldsPath)) {
            try (var entries = Files.list(nativeWorldsPath)) {
                var imported = new ArrayList<WorldDifficultyRegistry.Binding>();
                for (var path : entries.filter(p -> Files.isRegularFile(p.resolve("config.json"))).toList()) {
                    var nativeConfig=org.bson.BsonDocument.parse(Files.readString(path.resolve("config.json")));
                    // Use the installed SDK's UUID codec, including its native binary byte order.
                    var uuid=com.hypixel.hytale.codec.Codec.UUID_BINARY.decode(nativeConfig.get("UUID"),new com.hypixel.hytale.codec.ExtraInfo());
                    imported.add(normal(uuid,path.getFileName().toString()));
                }
                worlds.registerAll(imported);
            } catch (java.io.IOException error) { throw new IllegalStateException("LEGACY_WORLD_DISCOVERY_FAILED", error); }
        }
        // Fresh saves create this native default after plugin setup. Existing registry identities still win.
        if (firstMigration) legacy.add("default");
        legacyWorldNames = Set.copyOf(legacy);
    }
    /** Called by the native world-load event, before that world's simulation starts; never per-player/tick. */
    public Optional<WorldDifficultyRegistry.Binding> worldLoaded(UUID world, String name) {
        var prior = worlds.find(world);
        if (prior.isPresent()) {
            if (!prior.get().worldName().equals(name)) throw new IllegalStateException("WORLD_NAME_IDENTITY_CHANGED");
            return prior;
        }
        if (!legacyWorldNames.contains(name)) return Optional.empty();
        return Optional.of(worlds.register(normal(world,name)));
    }
    private WorldDifficultyRegistry.Binding normal(UUID world,String name) {
        return new WorldDifficultyRegistry.Binding(world,name,WorldDifficultyRegistry.Kind.CAMPAIGN,DifficultyId.NORMAL,legacyProfile,"server-character",true);
    }
    public WorldDifficultyRegistry worlds() { return worlds; }
    public EncounterProfileResolver encounters() { return encounters; }
    public WorldDifficultyRegistry.Binding currentWorld(UUID nativePlayerWorld) { return worlds.require(nativePlayerWorld); }
}
