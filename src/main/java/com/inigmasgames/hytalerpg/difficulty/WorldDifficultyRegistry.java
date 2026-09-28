package com.inigmasgames.hytalerpg.difficulty;

import com.google.gson.Gson;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Persistent server-owned world identities. Reads are memory-only; writes run outside the world tick. */
public final class WorldDifficultyRegistry {
    public enum Kind { CAMPAIGN, SHARED_HUB }
    public record Binding(UUID worldId, String worldName, Kind kind, DifficultyId difficulty,
                          String profileId, String progressionScope, boolean enabled) {
        public Binding {
            Objects.requireNonNull(worldId); Objects.requireNonNull(kind); Objects.requireNonNull(difficulty);
            for (String value : List.of(worldName, profileId, progressionScope))
                if (value.isBlank() || value.length() > 256 || value.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("INVALID_WORLD_BINDING");
            // Hub has a separate future event context, never a private difficulty per visitor.
            if (kind == Kind.SHARED_HUB && difficulty != DifficultyId.NORMAL) throw new IllegalArgumentException("HUB_REQUIRES_EXPLICIT_EVENT_CONTEXT");
        }
    }
    private record Data(int schemaVersion, List<Binding> worlds) {}
    private record Envelope(String checksum, Data data) {}
    private final Path path;
    private final Gson gson = new Gson();
    private volatile Map<UUID, Binding> worlds;
    public WorldDifficultyRegistry(Path path) {
        this.path = path;
        try {
            if (!Files.exists(path)) { worlds = Map.of(); return; }
            var envelope = gson.fromJson(Files.readString(path), Envelope.class);
            if (!hash(gson.toJson(envelope.data())).equals(envelope.checksum()) || envelope.data().schemaVersion() != 1)
                throw new IllegalStateException("WORLD_REGISTRY_CHECKSUM_OR_SCHEMA");
            worlds = validated(envelope.data().worlds());
        } catch (Exception error) { throw new IllegalStateException("Refusing to reset world difficulty registry " + path, error); }
    }
    public Optional<Binding> find(UUID world) { return Optional.ofNullable(worlds.get(Objects.requireNonNull(world))); }
    public Binding require(UUID world) { return find(world).orElseThrow(() -> new IllegalStateException("WORLD_DIFFICULTY_UNREGISTERED:" + world)); }
    public Collection<Binding> bindings() { return worlds.values(); }
    /** Identity is immutable after registration; stage 2 registers actual persistent native worlds before admission. */
    public synchronized Binding register(Binding binding) {
        registerAll(List.of(binding));
        return require(binding.worldId());
    }
    /** One startup transaction captures even unloaded legacy worlds; partial migration cannot reclassify a later world. */
    public synchronized void registerAll(Collection<Binding> bindings) {
        var list = new ArrayList<>(worlds.values());
        for (var binding : bindings) {
            var prior = worlds.get(binding.worldId());
            if (prior != null) {
                if (!prior.equals(binding)) throw new IllegalStateException("WORLD_DIFFICULTY_REBIND_FORBIDDEN");
            } else list.add(binding);
        }
        if (list.size() == worlds.size()) return;
        var candidate = validated(list);
        var data = new Data(1, candidate.values().stream().sorted(Comparator.comparing(b -> b.worldId().toString())).toList());
        var temp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            var bytes = gson.toJson(new Envelope(hash(gson.toJson(data)), data)).getBytes(StandardCharsets.UTF_8);
            try (var channel = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                var buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true);
            }
            if (Files.exists(path)) Files.copy(path, path.resolveSibling(path.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            worlds = candidate;
        } catch (Exception error) { throw new IllegalStateException("WORLD_DIFFICULTY_PERSISTENCE_FAILED", error); }
    }
    private static Map<UUID, Binding> validated(List<Binding> values) {
        if (values.size() > 1024) throw new IllegalArgumentException("WORLD_REGISTRY_BUDGET");
        Map<UUID, Binding> result = new HashMap<>(); Set<String> names = new HashSet<>(); Set<String> scopes = new HashSet<>();
        for (var value : values) {
            if (result.put(value.worldId(), value) != null || !names.add(value.worldName())) throw new IllegalArgumentException("DUPLICATE_WORLD_BINDING");
            scopes.add(value.progressionScope());
        }
        if (scopes.size() > 1) throw new IllegalArgumentException("CHARACTER_PROGRESSION_MUST_BE_SHARED");
        return Map.copyOf(result);
    }
    private static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
