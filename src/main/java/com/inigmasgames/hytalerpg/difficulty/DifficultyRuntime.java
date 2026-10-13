package com.inigmasgames.hytalerpg.difficulty;

import com.inigmasgames.hytalerpg.progress.*;
import java.nio.file.*;
import java.util.*;

/** Stage 1 composition root. World loading registers identity before encounter tracking starts. */
public final class DifficultyRuntime {
    private record Lineage(UUID historical, UUID release) {}
    /** These two pairs are the verified copied-registry identities of the Hywind 0.7.0 save. */
    private static final Map<String,Lineage> CHAPTER_I_LINEAGE = Map.of(
            "default",new Lineage(UUID.fromString("9a19439a-004d-44e6-934d-e82f0fdf662c"),
                    UUID.fromString("3d366b79-f367-419a-87b7-a8d53cde7ec0")),
            "zone3_taiga1_world",new Lineage(UUID.fromString("5cc8eef5-cf14-4e85-81ba-2dd651909658"),
                    UUID.fromString("6e88444d-898b-4a9d-99a6-f5f19d0257a1")));
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
        if (Files.isDirectory(nativeWorldsPath)) {
            try (var entries = Files.list(nativeWorldsPath)) {
                var imported = new ArrayList<WorldDifficultyRegistry.Binding>();
                var seen = new HashSet<UUID>();
                var replacements = new HashMap<UUID,UUID>();
                for (var path : entries.filter(p -> Files.isRegularFile(p.resolve("config.json"))).toList()) {
                    var nativeConfig=org.bson.BsonDocument.parse(Files.readString(path.resolve("config.json")));
                    // Use the installed SDK's UUID codec, including its native binary byte order.
                    var uuid=com.hypixel.hytale.codec.Codec.UUID_BINARY.decode(nativeConfig.get("UUID"),new com.hypixel.hytale.codec.ExtraInfo());
                    if (uuid == null || !seen.add(uuid)) throw new IllegalStateException("DUPLICATE_NATIVE_WORLD_UUID");
                    var name=path.getFileName().toString();
                    if (firstMigration) { imported.add(normal(uuid,name)); continue; }
                    var known=CHAPTER_I_LINEAGE.get(name);
                    if (known==null || !known.release().equals(uuid)) continue;
                    var old=worlds.find(known.historical());
                    if (old.isPresent()) {
                        if (!old.get().equals(normal(known.historical(),name)))
                            throw new IllegalStateException("CHAPTER_I_HISTORICAL_WORLD_CONFLICT:"+name);
                        replacements.put(known.historical(),uuid);
                    } else if (worlds.find(uuid).isPresent()) {
                        if (!worlds.find(uuid).orElseThrow().equals(normal(uuid,name)))
                            throw new IllegalStateException("CHAPTER_I_RELEASE_WORLD_CONFLICT:"+name);
                    }
                }
                if (firstMigration) worlds.registerAll(imported);
                else worlds.reconcile(replacements);
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
