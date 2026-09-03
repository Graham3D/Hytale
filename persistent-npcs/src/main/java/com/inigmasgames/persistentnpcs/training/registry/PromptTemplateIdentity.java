package com.inigmasgames.persistentnpcs.training.registry;

import java.util.Map;

/** Pins the renderer/template that produced the exact provider-visible messages. */
public record PromptTemplateIdentity(int schemaVersion, String renderer,
        String rendererRevision, String templateSha256, Map<String, String> parameters) {
    public static final int SCHEMA_VERSION = 1;
    public PromptTemplateIdentity {
        if (schemaVersion != SCHEMA_VERSION || renderer == null || renderer.isBlank()
                || rendererRevision == null || rendererRevision.isBlank()
                || templateSha256 == null || !templateSha256.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("pinned prompt-template identity required");
        }
        parameters = Map.copyOf(parameters == null ? Map.of() : parameters);
    }
    public String contentId() { return "prompt_" + CanonicalJson.sha256(this); }
}
