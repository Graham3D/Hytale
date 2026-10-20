package com.inigmasgames.hytalerpg.spawning;

import com.google.gson.Gson;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

/** One server/save-wide setting; a missing file is the untouched native 1x default. */
public final class WorldSpawnDensitySettings {
    private record Data(int schemaVersion, double worldSpawnDensityMultiplier) {}
    private record Envelope(String checksum, Data data) {}
    private final Gson gson = new Gson();
    private final Path path;
    private volatile double multiplier;

    public WorldSpawnDensitySettings(Path path) {
        this.path = path;
        try {
            if (!Files.exists(path)) { multiplier = 1.0; return; }
            var envelope = gson.fromJson(Files.readString(path), Envelope.class);
            if (envelope == null || envelope.data() == null || envelope.data().schemaVersion() != 1 ||
                    !hash(gson.toJson(envelope.data())).equals(envelope.checksum()))
                throw new IllegalStateException("WORLD_SPAWN_DENSITY_CHECKSUM_OR_SCHEMA");
            validate(envelope.data().worldSpawnDensityMultiplier());
            multiplier = envelope.data().worldSpawnDensityMultiplier();
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
            var bytes = gson.toJson(new Envelope(hash(gson.toJson(data)), data)).getBytes(StandardCharsets.UTF_8);
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

    private static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
