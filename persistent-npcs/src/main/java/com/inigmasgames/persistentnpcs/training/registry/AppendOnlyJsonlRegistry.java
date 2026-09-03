package com.inigmasgames.persistentnpcs.training.registry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/** Small append-only JSONL registry with immutable identity enforcement. */
public final class AppendOnlyJsonlRegistry {
    private final Path file;
    public AppendOnlyJsonlRegistry(Path file) { this.file = file.toAbsolutePath().normalize(); }

    public synchronized boolean append(String id, Object payload) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("registry id required");
        String hash = CanonicalJson.sha256(payload);
        Existing existing = find(id);
        if (existing != null) {
            if (!existing.hash().equals(hash)) throw new IllegalStateException(
                    "immutable registry identity collision: " + id);
            return false;
        }
        JsonObject envelope = new JsonObject();
        envelope.addProperty("schemaVersion", 1);
        envelope.addProperty("id", id);
        envelope.addProperty("contentHash", hash);
        envelope.addProperty("recordedAt", Instant.now().toString());
        envelope.add("payload", com.inigmasgames.persistentnpcs.json.JsonFiles.GSON.toJsonTree(payload));
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, CanonicalJson.serialize(envelope) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return true;
        } catch (IOException exception) {
            throw new UncheckedIOException("could not append " + file, exception);
        }
    }

    public synchronized Path initialize() {
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) Files.createFile(file);
            if (!Files.isRegularFile(file)) throw new IllegalStateException(
                    "registry path is not a regular file: " + file);
            return file;
        } catch (IOException exception) {
            throw new UncheckedIOException("could not initialize " + file, exception);
        }
    }

    public synchronized boolean contains(String id) { return find(id) != null; }
    public Path path() { return file; }

    private Existing find(String id) {
        if (!Files.isRegularFile(file)) return null;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                JsonObject object = JsonParser.parseString(line).getAsJsonObject();
                if (id.equals(object.get("id").getAsString())) {
                    return new Existing(object.get("contentHash").getAsString());
                }
            }
            return null;
        } catch (IOException exception) {
            throw new UncheckedIOException("could not read " + file, exception);
        }
    }
    private record Existing(String hash) { }
}
