package com.inigmasgames.persistentnpcs.training.teacher;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.training.registry.ArtifactRoot;
import com.inigmasgames.persistentnpcs.training.registry.CanonicalJson;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/** Fail-closed, import-first D3 path for operator-reviewed teacher conclusions. */
public final class ReviewedTeacherImport {
    private static final Set<String> HIDDEN_REASONING_KEYS = Set.of("reasoning",
            "chainOfThought", "chain_of_thought", "hiddenReasoning", "hidden_reasoning");
    private final ArtifactRoot root;
    private final Predicate<String> candidateExists;
    private final Set<String> importedResponseHashes = new HashSet<>();

    public ReviewedTeacherImport(ArtifactRoot root, Predicate<String> candidateExists) {
        this.root = java.util.Objects.requireNonNull(root, "root");
        this.candidateExists = java.util.Objects.requireNonNull(candidateExists,
                "candidateExists");
    }

    public ImportResult importLine(String rawJson) {
        try {
            JsonObject object = JsonParser.parseString(rawJson).getAsJsonObject();
            if (containsHiddenReasoning(object)) throw new IllegalArgumentException(
                    "hidden reasoning fields are prohibited");
            String candidateId = requiredString(object, "candidateId");
            if (!candidateExists.test(candidateId)) throw new IllegalArgumentException(
                    "unknown candidate reference " + candidateId);
            JsonObject responseObject = object.getAsJsonObject("response");
            if (responseObject == null) throw new IllegalArgumentException("response required");
            TeacherContracts.TeacherResponse response = JsonFiles.GSON.fromJson(
                    responseObject, TeacherContracts.TeacherResponse.class);
            String hash = CanonicalJson.sha256(response);
            if (!importedResponseHashes.add(hash)) throw new IllegalArgumentException(
                    "duplicate teacher response");
            return new ImportResult(candidateId, response, hash);
        } catch (RuntimeException malformed) {
            quarantine(rawJson, malformed.getMessage());
            throw malformed;
        }
    }

    private void quarantine(String raw, String reason) {
        String id = CanonicalJson.sha256Text(raw);
        JsonObject entry = new JsonObject();
        entry.addProperty("schemaVersion", 1);
        entry.addProperty("contentHash", id);
        entry.addProperty("reason", reason == null ? "malformed" : reason);
        entry.addProperty("raw", raw == null ? "" : raw);
        Path target = root.resolve("quarantine", "teacher-import-" + id + ".json");
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, CanonicalJson.serialize(entry), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
        } catch (java.nio.file.FileAlreadyExistsException ignored) {
            // The identical malformed artifact is already quarantined.
        } catch (IOException exception) {
            throw new UncheckedIOException("could not quarantine teacher import", exception);
        }
    }

    private static boolean containsHiddenReasoning(JsonElement value) {
        if (value == null || value.isJsonNull()) return false;
        if (value.isJsonArray()) {
            for (JsonElement item : value.getAsJsonArray()) if (containsHiddenReasoning(item)) return true;
        } else if (value.isJsonObject()) {
            for (var entry : value.getAsJsonObject().entrySet()) {
                if (HIDDEN_REASONING_KEYS.contains(entry.getKey())
                        || containsHiddenReasoning(entry.getValue())) return true;
            }
        }
        return false;
    }

    private static String requiredString(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()
                || object.get(key).getAsString().isBlank()) {
            throw new IllegalArgumentException(key + " required");
        }
        return object.get(key).getAsString();
    }

    public record ImportResult(String candidateId,
            TeacherContracts.TeacherResponse response, String responseHash) { }
}
