package com.inigmasgames.persistentnpcs.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSerializationContext;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

public final class JsonFiles {
    private static final Object RESOURCE_INSTALL_LOCK = new Object();
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class,
                    (JsonSerializer<Instant>) (value, type, context) ->
                            new JsonPrimitive(value.toString()))
            .registerTypeAdapter(Instant.class,
                    (JsonDeserializer<Instant>) (value, type, context) ->
                            Instant.parse(value.getAsString()))
            .registerTypeAdapter(LocalDateTime.class,
                    (JsonSerializer<LocalDateTime>) (value, type, context) ->
                            new JsonPrimitive(value.toString()))
            .registerTypeAdapter(LocalDateTime.class,
                    (JsonDeserializer<LocalDateTime>) (value, type, context) ->
                            LocalDateTime.parse(value.getAsString()))
            // Hytale's August 31 SDK update made Gson's bundled UUID adapter reject
            // legacy empty strings. Preserve the established persisted-data contract:
            // blank/null means absent, while malformed non-empty UUIDs still fail.
            .registerTypeAdapter(UUID.class, new UuidJsonAdapter())
            .setPrettyPrinting()
            .create();

    private JsonFiles() {
    }

    private static final class UuidJsonAdapter
            implements JsonSerializer<UUID>, JsonDeserializer<UUID> {
        @Override
        public JsonElement serialize(
                UUID value, java.lang.reflect.Type type, JsonSerializationContext context) {
            return value == null ? JsonNull.INSTANCE : new JsonPrimitive(value.toString());
        }

        @Override
        public UUID deserialize(
                JsonElement value, java.lang.reflect.Type type,
                JsonDeserializationContext context) throws JsonParseException {
            if (value == null || value.isJsonNull()) return null;
            String text = value.getAsString().strip();
            if (text.isEmpty()) return null;
            try {
                return UUID.fromString(text);
            } catch (IllegalArgumentException malformed) {
                throw new JsonParseException("Invalid non-empty UUID '" + text + "'", malformed);
            }
        }
    }

    public static <T> T read(Path path, Class<T> type) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + path, exception);
        }
    }

    public static void writeAtomic(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(value), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write " + path, exception);
        }
    }

    public static void copyResourceIfMissing(Class<?> owner, String resource, Path target) {
        if (Files.exists(target)) {
            return;
        }
        try (var input = owner.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing packaged resource: " + resource);
            }
            Files.createDirectories(target.getParent());
            Files.copy(input, target);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not initialize " + target, exception);
        }
    }

    /**
     * Installs a packaged runtime helper that is owned and versioned by the mod.
     * Identical targets are deliberately left untouched: a previous local-server
     * instance may still be releasing a Windows handle during a menu reconnect.
     */
    public static void copyResourceReplacing(Class<?> owner, String resource, Path target) {
        try (var input = owner.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing packaged resource: " + resource);
            }
            byte[] packaged = input.readAllBytes();
            synchronized (RESOURCE_INSTALL_LOCK) {
                Files.createDirectories(target.getParent());
                if (Files.isRegularFile(target)
                        && Files.size(target) == packaged.length
                        && Arrays.equals(Files.readAllBytes(target), packaged)) {
                    return;
                }
                Path temporary = target.resolveSibling(target.getFileName()
                        + ".install-" + UUID.randomUUID() + ".tmp");
                try {
                    Files.write(temporary, packaged);
                    try {
                        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING);
                    } catch (AtomicMoveNotSupportedException ignored) {
                        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not install " + target, exception);
        }
    }
}
