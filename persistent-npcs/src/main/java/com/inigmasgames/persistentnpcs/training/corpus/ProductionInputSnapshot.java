package com.inigmasgames.persistentnpcs.training.corpus;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.training.registry.CanonicalJson;
import com.inigmasgames.persistentnpcs.training.registry.ModelIdentity;
import com.inigmasgames.persistentnpcs.training.registry.PromptTemplateIdentity;
import java.util.List;
import java.util.Map;

/** Immutable capture of the exact input crossing the production provider boundary. */
public record ProductionInputSnapshot(int schemaVersion,
        List<ChatMessage> messages,
        JsonElement tools,
        JsonElement responseFormat,
        Map<String, String> providerSettings,
        String conversationRoute,
        PromptTemplateIdentity promptTemplate,
        ModelIdentity baseModel,
        JsonElement epistemicTargetSnapshot,
        JsonElement evidenceSnapshot,
        JsonElement answerabilitySnapshot,
        JsonElement answerPlanSnapshot,
        JsonElement profileConstraints,
        String providerInputSha256) {
    public static final int SCHEMA_VERSION = 1;

    public ProductionInputSnapshot {
        if (schemaVersion != SCHEMA_VERSION || messages == null || messages.isEmpty()
                || promptTemplate == null || baseModel == null) {
            throw new IllegalArgumentException("complete production input snapshot required");
        }
        messages = List.copyOf(messages);
        tools = copy(tools); responseFormat = copy(responseFormat);
        providerSettings = Map.copyOf(providerSettings == null ? Map.of() : providerSettings);
        conversationRoute = conversationRoute == null ? "" : conversationRoute.strip();
        epistemicTargetSnapshot = copy(epistemicTargetSnapshot);
        evidenceSnapshot = copy(evidenceSnapshot);
        answerabilitySnapshot = copy(answerabilitySnapshot);
        answerPlanSnapshot = copy(answerPlanSnapshot);
        profileConstraints = copy(profileConstraints);
        String actual = inputHash(messages, tools, responseFormat, providerSettings,
                conversationRoute, promptTemplate);
        if (providerInputSha256 == null || !providerInputSha256.equals(actual)) {
            throw new IllegalArgumentException("provider input hash mismatch");
        }
    }

    public static ProductionInputSnapshot capture(LlmRequest request,
            Map<String, String> providerSettings, String route,
            PromptTemplateIdentity promptTemplate, ModelIdentity baseModel,
            JsonElement epistemicTarget, JsonElement evidence, JsonElement answerability,
            JsonElement answerPlan, JsonElement profileConstraints) {
        List<ChatMessage> exactMessages = request.canonicalMessages();
        JsonElement tools = com.inigmasgames.persistentnpcs.json.JsonFiles.GSON
                .toJsonTree(request.tools());
        JsonElement responseFormat = request.responseFormat() == null ? JsonNull.INSTANCE
                : request.responseFormat().deepCopy();
        Map<String, String> settings = Map.copyOf(
                providerSettings == null ? Map.of() : providerSettings);
        String hash = inputHash(exactMessages, tools, responseFormat, settings, route,
                promptTemplate);
        return new ProductionInputSnapshot(SCHEMA_VERSION, exactMessages, tools,
                responseFormat, settings, route, promptTemplate, baseModel,
                epistemicTarget, evidence, answerability, answerPlan, profileConstraints, hash);
    }

    /** Recomputes the provider-boundary hash without trusting serialized hash fields. */
    public String recomputedProviderInputSha256() {
        return inputHash(messages, tools, responseFormat, providerSettings,
                conversationRoute, promptTemplate);
    }

    public boolean hasValidProviderInputHash() {
        return providerInputSha256.equals(recomputedProviderInputSha256());
    }

    private static String inputHash(List<ChatMessage> messages, JsonElement tools,
            JsonElement responseFormat, Map<String, String> settings, String route,
            PromptTemplateIdentity template) {
        JsonObject input = new JsonObject();
        input.add("messages", com.inigmasgames.persistentnpcs.json.JsonFiles.GSON.toJsonTree(messages));
        input.add("tools", copy(tools)); input.add("responseFormat", copy(responseFormat));
        input.add("providerSettings",
                com.inigmasgames.persistentnpcs.json.JsonFiles.GSON.toJsonTree(settings));
        input.addProperty("route", route == null ? "" : route.strip());
        input.addProperty("promptTemplateId", template.contentId());
        return CanonicalJson.sha256(input);
    }

    private static JsonElement copy(JsonElement value) {
        return value == null ? JsonNull.INSTANCE : value.deepCopy();
    }
}
