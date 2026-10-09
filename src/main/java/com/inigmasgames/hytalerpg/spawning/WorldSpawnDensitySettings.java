package com.inigmasgames.hytalerpg.spawning;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** One server/save-wide setting; a missing file is the untouched native 1x default. */
public final class WorldSpawnDensitySettings implements SpawnDensitySetting {
    private record Data(int schemaVersion, double worldSpawnDensityMultiplier) {}
    private final Gson gson = new Gson();
    private final Path path;
    private volatile double multiplier;

    public WorldSpawnDensitySettings(Path path) {
        this.path = path;
        try {
            if (!Files.exists(path)) { multiplier = 1.0; return; }
            var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            // Earlier builds wrote a checksummed envelope. The multiplier is an
            // owner-editable setting, so a valid manual edit must not require
            // recomputing that legacy checksum. New writes use plain JSON.
            var data = gson.fromJson(json.has("data") ? json.getAsJsonObject("data") : json, Data.class);
            if (data == null || data.schemaVersion() != 1)
                throw new IllegalStateException("WORLD_SPAWN_DENSITY_SCHEMA");
            validate(data.worldSpawnDensityMultiplier());
            multiplier = data.worldSpawnDensityMultiplier();
        } catch (Exception error) {
            throw new IllegalStateException("Refusing to reset world spawn density setting " + path, error);
        }
    }

    public double multiplier() { return multiplier; }

    public synchronized void set(double value) {
        validate(value);
        if (value == multiplier) return;
        var data = new Data(1, value);
        var temp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            var bytes = gson.toJson(data).getBytes(StandardCharsets.UTF_8);
            try (var channel = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                var buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            if (Files.exists(path)) Files.copy(path, path.resolveSibling(path.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            multiplier = value;
        } catch (Exception error) {
            throw new IllegalStateException("WORLD_SPAWN_DENSITY_PERSISTENCE_FAILED", error);
        }
    }

    public static void validate(double value) {
        if (!Double.isFinite(value) || value < 0.25 || value > 8.0)
            throw new IllegalArgumentException("Spawn density must be between 0.25 and 8.0.");
    }

    public static int scaledCap(int baseline, double multiplier) {
        validate(multiplier);
        if (baseline <= 0) return baseline; // Native nonpositive cap means unbounded.
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(baseline * multiplier));
    }

}
