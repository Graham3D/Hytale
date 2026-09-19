package com.inigmasgames.persistentnpcs.evaluation;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.llm.LlmLatency;
import com.inigmasgames.persistentnpcs.llm.LlmProvider;
import com.inigmasgames.persistentnpcs.llm.LlmProviderStatus;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.LlmResult;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Deterministic external-boundary adapter; the production Orbis graph remains unchanged. */
final class DeterministicEvaluationProvider implements LlmProvider {
    @Override public CompletableFuture<LlmResult> generateResponse(LlmRequest request) {
        return CompletableFuture.completedFuture(new LlmResult(response(request),
                new LlmLatency(Instant.now(), 1, 2, true)));
    }

    @Override public CompletableFuture<LlmResult> stream(LlmRequest request,
            Consumer<String> tokens) {
        String response = response(request);
        tokens.accept(response);
        return CompletableFuture.completedFuture(new LlmResult(response,
                new LlmLatency(Instant.now(), 1, 2, true)));
    }

    @Override public boolean streamingEnabled() { return true; }
    @Override public CompletableFuture<LlmProviderStatus> checkStatus() {
        return CompletableFuture.completedFuture(new LlmProviderStatus("in-process",
                "deterministic-evaluation", true, true, true, "fixture"));
    }
    @Override public String description() { return "deterministic evaluation boundary"; }

    private static String response(LlmRequest request) {
        String user = request.messages().stream().filter(value -> "user".equalsIgnoreCase(
                value.role())).reduce((first, second) -> second).map(value -> value.content())
                .orElse("").toLowerCase(Locale.ROOT);
        String fullContext = request.messages().stream().map(value -> value.content() == null
                ? "" : value.content()).collect(java.util.stream.Collectors.joining("\n"))
                .toLowerCase(Locale.ROOT);
        String spoken = user.contains("who are you") ? "I am Mara."
                : user.contains("name is graham") ? "Understood, Graham."
                : user.contains("what's my name") ? "Your name is Graham."
                : user.contains("golden crown") ? "I don't know where you put a golden crown."
                : user.contains("what did i hide") || user.contains("where did i put") ?
                        "You told me you hid a silver key under a large rock."
                : user.contains("dragon behind the moon") ? "I don't know that."
                : user.contains("what am i holding") || user.contains("see what i'm holding")
                        ? fullContext.contains("lantern") ? "You're holding a lantern."
                                : "You're holding nothing."
                : user.contains("what are you doing") || user.contains("working on")
                        ? "I'm checking the forge."
                : user.contains("follow me") || user.contains("come with me")
                        ? "I can follow you."
                : user.contains("put it there") ? "Where do you want me to put it?"
                : user.contains("what do you want") ? "I want to help with useful work."
                : user.contains("how do you feel") ? "I feel calm and attentive."
                : "Hello. I'm listening.";
        if (request.responseFormat() == null) return spoken;
        JsonObject schema = request.responseFormat().getAsJsonObject("json_schema")
                .getAsJsonObject("schema").getAsJsonObject("properties");
        String intent = firstEnum(schema, "intent", "AMBIENT_RESPONSE");
        String emotion = firstEnum(schema, "emotion", "CALM");
        return "{\"intent\":\"" + intent + "\",\"spokenText\":\""
                + spoken.replace("\\", "\\\\").replace("\"", "\\\"")
                + "\",\"emotion\":\"" + emotion
                + "\",\"paralinguisticEvent\":\"NONE\",\"actions\":[],"
                + "\"groundingEvidenceRefs\":[]}";
    }

    private static String firstEnum(JsonObject properties, String name, String fallback) {
        if (properties == null || !properties.has(name)) return fallback;
        var values = properties.getAsJsonObject(name).getAsJsonArray("enum");
        return values == null || values.isEmpty() ? fallback : values.get(0).getAsString();
    }
}
