package com.inigmasgames.persistentnpcs.voice;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.ai.AiProviderCapabilities;
import com.inigmasgames.persistentnpcs.ai.AiProviderHealth;
import com.inigmasgames.persistentnpcs.ai.AiServiceKind;
import com.inigmasgames.persistentnpcs.ai.ProviderExecutionMode;
import com.inigmasgames.persistentnpcs.ai.RemoteInferenceTransport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Network-separated Chatterbox-compatible TTS provider. */
public final class RemoteTextToSpeechProvider implements TextToSpeechProvider {
    private final RemoteInferenceTransport transport;
    private final String model;
    private final int concurrency;

    public RemoteTextToSpeechProvider(
            String endpoint, String model, int timeoutMillis, int concurrency) {
        transport = new RemoteInferenceTransport(endpoint, timeoutMillis);
        this.model = model == null ? "" : model;
        this.concurrency = Math.max(1, concurrency);
    }

    @Override public CompletableFuture<OpusClip> synthesize(UUID requestId, UUID responseId,
            VoiceRenderPlan plan, String text) {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", requestId.toString());
        body.addProperty("queuedAtEpochMillis", System.currentTimeMillis());
        body.addProperty("responseId", responseId.toString());
        body.addProperty("npcStableId", plan.npcId().toString());
        body.addProperty("model", model);
        body.addProperty("text", text);
        body.addProperty("voicePresetId", plan.voicePresetId());
        body.addProperty("emotion", plan.vocalState().emotion().name());
        body.addProperty("paralinguisticEvent", plan.vocalState().paralinguisticEvent()
                .map(ParalinguisticEvent::tag).orElse(""));
        body.addProperty("gainDb", plan.outputGainDb());
        body.addProperty("projection", plan.projection().name());
        plan.referenceAudio().ifPresent(reference -> {
            body.addProperty("referenceFileName", reference.getFileName().toString());
            try {
                body.addProperty("referenceWavBase64", Base64.getEncoder().encodeToString(
                        Files.readAllBytes(reference)));
            } catch (IOException failure) {
                throw new IllegalStateException("Could not read TTS reference audio", failure);
            }
        });
        return transport.post(requestId, "/v1/tts/synthesize", body)
                .thenApply(value -> clip(value, plan.referenceAudio().orElse(Path.of("remote"))));
    }

    @Override public void cancel(UUID requestOrSessionId) { transport.cancel(requestOrSessionId); }
    @Override public String providerId() { return "immersive-http-tts"; }
    @Override public AiServiceKind serviceKind() { return AiServiceKind.TEXT_TO_SPEECH; }
    @Override public ProviderExecutionMode executionMode() { return ProviderExecutionMode.REMOTE; }
    @Override public AiProviderCapabilities capabilities() {
        return new AiProviderCapabilities(true, true, true, Set.of("text", "wav", "opus"));
    }
    @Override public CompletableFuture<AiProviderHealth> health() { return transport.health(); }
    @Override public int concurrencyLimit() { return concurrency; }
    @Override public String backendDescription() {
        return "remote TTS model=" + model + " endpoint=" + transport.endpoint();
    }
    @Override public void close() { transport.close(); }

    private static OpusClip clip(JsonObject value, Path reference) {
        JsonArray encoded = value.getAsJsonArray("frames");
        ArrayList<byte[]> frames = new ArrayList<>();
        encoded.forEach(frame -> frames.add(Base64.getDecoder().decode(frame.getAsString())));
        return new OpusClip(frames, integer(value, "sourceRate", 48_000),
                value.has("inferenceMs") ? number(value, "inferenceMs")
                        : number(value, "ttsMs"), number(value, "encodeMs"),
                number(value, "conditioningMs"), bool(value, "conditionalsCached"),
                number(value, "workerQueueWaitMs"), 0, 0,
                integer(value, "modelLoadCount", 1), text(value, "device", "remote"),
                reference);
    }
    private static long number(JsonObject value, String key) {
        return value.has(key) ? value.get(key).getAsLong() : 0L;
    }
    private static int integer(JsonObject value, String key, int fallback) {
        return value.has(key) ? value.get(key).getAsInt() : fallback;
    }
    private static boolean bool(JsonObject value, String key) {
        return value.has(key) && value.get(key).getAsBoolean();
    }
    private static String text(JsonObject value, String key, String fallback) {
        return value.has(key) ? value.get(key).getAsString() : fallback;
    }
}
