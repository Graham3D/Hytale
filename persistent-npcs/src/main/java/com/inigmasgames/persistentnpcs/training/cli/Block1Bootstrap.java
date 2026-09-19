package com.inigmasgames.persistentnpcs.training.cli;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.inigmasgames.persistentnpcs.training.registry.ArtifactRoot;
import com.inigmasgames.persistentnpcs.training.registry.TrainingArtifactRegistries;
import com.inigmasgames.persistentnpcs.training.registry.ModelIdentity;
import com.inigmasgames.persistentnpcs.training.registry.PromptTemplateIdentity;
import com.inigmasgames.persistentnpcs.training.teacher.TeacherSourcePolicy;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Explicit offline bootstrap; this class is never called by the Hytale plugin. */
public final class Block1Bootstrap {
    private Block1Bootstrap() { }
    public static void main(String[] args) {
        if (args.length != 5) throw new IllegalArgumentException(
                "usage: Block1Bootstrap <offline-root> <active-save-root> <policy-json> <model-identity-json> <prompt-identity-json>");
        ArtifactRoot root = new ArtifactRoot(Path.of(args[0]), Path.of(args[1]));
        root.initialize();
        TrainingArtifactRegistries registries = new TrainingArtifactRegistries(root);
        registries.initialize();
        JsonObject document = readObject(Path.of(args[2]));
        if (document.get("schemaVersion").getAsInt() != 1) throw new IllegalArgumentException(
                "unsupported teacher policy document schema");
        int appended = 0;
        for (var element : document.getAsJsonArray("policies")) {
            TeacherSourcePolicy policy = policy(element.getAsJsonObject());
            if (registries.teacherSources().append(policy.policyId(), policy)) appended++;
        }
        ModelIdentity model = model(readObject(Path.of(args[3])));
        PromptTemplateIdentity prompt = prompt(readObject(Path.of(args[4])));
        registries.models().append(model.contentId(), model);
        registries.promptTemplates().append(prompt.contentId(), prompt);
        System.out.println("Orbis Block 1 root initialized: " + root.path()
                + " teacherPoliciesAppended=" + appended);
    }
    private static TeacherSourcePolicy policy(JsonObject value) {
        return new TeacherSourcePolicy(TeacherSourcePolicy.SCHEMA_VERSION,
                text(value, "policyId"), text(value, "sourceId"),
                TeacherSourcePolicy.TeacherSourceStatus.valueOf(text(value, "status")),
                text(value, "termsVersion"), text(value, "licenseId"),
                strings(value.getAsJsonArray("allowedUses")),
                strings(value.getAsJsonArray("prohibitedUses")),
                text(value, "decisionBasis"), Instant.parse(text(value, "reviewedAt")));
    }
    private static ModelIdentity model(JsonObject value) {
        return new ModelIdentity(1, text(value, "repository"), text(value, "revision"),
                text(value, "artifactSha256"), text(value, "architecture"),
                text(value, "precision"), text(value, "tokenizerSha256"),
                text(value, "chatTemplateSha256"), stringMap(value.getAsJsonObject("provenance")));
    }
    private static PromptTemplateIdentity prompt(JsonObject value) {
        return new PromptTemplateIdentity(1, text(value, "renderer"),
                text(value, "rendererRevision"), text(value, "templateSha256"),
                stringMap(value.getAsJsonObject("parameters")));
    }
    private static java.util.Map<String, String> stringMap(JsonObject value) {
        java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
        if (value != null) for (var entry : value.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getAsString());
        }
        return java.util.Map.copyOf(result);
    }
    private static String text(JsonObject value, String key) {
        return value.has(key) && !value.get(key).isJsonNull()
                ? value.get(key).getAsString() : "";
    }
    private static Set<String> strings(JsonArray values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) for (var value : values) result.add(value.getAsString());
        return Set.copyOf(result);
    }
    private static JsonObject readObject(Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException("could not read " + path, exception);
        }
    }
}
