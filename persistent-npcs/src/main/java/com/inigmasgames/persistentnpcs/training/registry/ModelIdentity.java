package com.inigmasgames.persistentnpcs.training.registry;

import java.util.Map;

/** Immutable identity for a base, teacher, or adapter-bearing model artifact. */
public record ModelIdentity(int schemaVersion, String repository, String revision,
        String artifactSha256, String architecture, String precision,
        String tokenizerSha256, String chatTemplateSha256,
        Map<String, String> provenance) {
    public static final int SCHEMA_VERSION = 1;
    public ModelIdentity {
        if (schemaVersion != SCHEMA_VERSION || blank(repository) || blank(revision)
                || !sha(artifactSha256) || blank(architecture) || blank(precision)) {
            throw new IllegalArgumentException("complete pinned model identity required");
        }
        tokenizerSha256 = tokenizerSha256 == null ? "" : tokenizerSha256.strip();
        chatTemplateSha256 = chatTemplateSha256 == null ? "" : chatTemplateSha256.strip();
        provenance = Map.copyOf(provenance == null ? Map.of() : provenance);
    }
    public String contentId() { return "model_" + CanonicalJson.sha256(this); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static boolean sha(String value) { return value != null && value.matches("[0-9a-fA-F]{64}"); }
}
