package com.inigmasgames.hytalerpg.progress;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Atomic, checksum-verified persistence beneath the Hytale save's plugin-owned data directory. */
public final class FileRpgPlayerStateRepository implements RpgPlayerStateRepository {
    private static final int ENVELOPE_SCHEMA = 1;
    private final Path playersDirectory;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private final RpgStateMigrator migrator = new RpgStateMigrator();

    public FileRpgPlayerStateRepository(Path playersDirectory) { this.playersDirectory = playersDirectory; }

    @Override
    public LoadResult load(UUID playerUuid) {
        Path path = path(playerUuid);
        if (!Files.isRegularFile(path)) return new LoadResult(RpgPlayerState.create(playerUuid), false, false,
                RpgPlayerState.CURRENT_SCHEMA, List.of());
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject rawState;
            List<String> warnings = new ArrayList<>();
            if (root.has("state")) {
                rawState = root.getAsJsonObject("state");
                String expected = root.get("checksum").getAsString();
                String actual = checksum(gson.toJson(rawState));
                if (!expected.equalsIgnoreCase(actual)) throw new IllegalStateException("RPG state checksum mismatch: " + path);
            } else {
                rawState = root;
                warnings.add("Legacy unwrapped state migrated to checksum envelope");
            }
            RpgStateMigrator.MigrationResult migration = migrator.migrate(rawState);
            RpgPlayerState state = gson.fromJson(migration.state(), RpgPlayerState.class);
            state.normalizeShape();
            if (!playerUuid.toString().equals(state.playerUuid)) throw new IllegalStateException("RPG state player UUID mismatch: " + path);
            if (migration.migrated()) warnings.add("Migrated RPG schema v" + migration.sourceVersion() + " -> v" + migration.targetVersion());
            return new LoadResult(state, true, migration.migrated(), migration.sourceVersion(), warnings);
        } catch (Exception error) {
            throw new IllegalStateException("Refusing to reset unreadable RPG player state " + path + ": " + error.getMessage(), error);
        }
    }

    @Override
    public void save(RpgPlayerState state) {
        state.normalizeShape();
        if (state.schemaVersion != RpgPlayerState.CURRENT_SCHEMA) throw new IllegalArgumentException("Cannot save non-current RPG schema");
        Path path = path(state.playerUuid());
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        Path backup = path.resolveSibling(path.getFileName() + ".bak");
        try {
            Files.createDirectories(playersDirectory);
            JsonObject stateJson = gson.toJsonTree(state).getAsJsonObject();
            JsonObject envelope = new JsonObject();
            envelope.addProperty("envelopeSchema", ENVELOPE_SCHEMA);
            envelope.addProperty("checksum", checksum(gson.toJson(stateJson)));
            envelope.add("state", stateJson);
            Files.writeString(temp, gson.toJson(envelope), StandardCharsets.UTF_8);
            if (Files.isRegularFile(path)) Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception error) {
            try { Files.deleteIfExists(temp); } catch (Exception ignored) {}
            throw new IllegalStateException("Unable to save RPG player state " + path, error);
        }
    }

    public Path path(UUID playerUuid) { return playersDirectory.resolve(playerUuid + ".json"); }

    private static String checksum(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
