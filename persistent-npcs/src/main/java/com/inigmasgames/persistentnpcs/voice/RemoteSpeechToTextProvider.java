package com.inigmasgames.persistentnpcs.voice;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.ai.AiProviderCapabilities;
import com.inigmasgames.persistentnpcs.ai.AiProviderHealth;
import com.inigmasgames.persistentnpcs.ai.AiServiceKind;
import com.inigmasgames.persistentnpcs.ai.ProviderExecutionMode;
import com.inigmasgames.persistentnpcs.ai.RemoteInferenceTransport;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Network-separated STT provider using the Immersive worker JSON contract. */
public final class RemoteSpeechToTextProvider implements SpeechToTextProvider {
    private final RemoteInferenceTransport transport;
    private final String model;
    private final int concurrency;

    public RemoteSpeechToTextProvider(
            String endpoint, String model, int timeoutMillis, int concurrency) {
        transport = new RemoteInferenceTransport(endpoint, timeoutMillis);
        this.model = model == null ? "" : model;
        this.concurrency = Math.max(1, concurrency);
    }

    @Override public CompletableFuture<SpeechTranscript> transcribe(
            UUID requestId, List<byte[]> opusFrames) {
        JsonObject body = request(requestId, requestId, opusFrames);
        return transport.post(requestId, "/v1/stt/transcribe", body)
                .thenApply(RemoteSpeechToTextProvider::transcript);
    }

    @Override public boolean streamingTranscriptionEnabled() { return true; }
    @Override public CompletableFuture<Void> startStream(UUID sessionId) {
        JsonObject body = request(sessionId, sessionId, List.of());
        return transport.post(sessionId, "/v1/stt/stream/start", body).thenAccept(v -> { });
    }
    @Override public CompletableFuture<String> appendStream(
            UUID sessionId, List<byte[]> opusFrames) {
        UUID requestId = UUID.randomUUID();
        JsonObject body = request(requestId, sessionId, opusFrames);
        return transport.post(requestId, "/v1/stt/stream/audio", body)
                .thenApply(value -> text(value, "partial"));
    }
    @Override public CompletableFuture<SpeechTranscript> finishStream(UUID sessionId) {
        JsonObject body = request(sessionId, sessionId, List.of());
        return transport.post(sessionId, "/v1/stt/stream/finish", body)
                .thenApply(RemoteSpeechToTextProvider::transcript);
    }
    @Override public void cancel(UUID requestOrSessionId) { transport.cancel(requestOrSessionId); }
    @Override public String providerId() { return "immersive-http-stt"; }
    @Override public AiServiceKind serviceKind() { return AiServiceKind.SPEECH_TO_TEXT; }
    @Override public ProviderExecutionMode executionMode() { return ProviderExecutionMode.REMOTE; }
    @Override public AiProviderCapabilities capabilities() {
        return new AiProviderCapabilities(true, true, true, Set.of("opus", "json", "text"));
    }
    @Override public CompletableFuture<AiProviderHealth> health() { return transport.health(); }
    @Override public int concurrencyLimit() { return concurrency; }
    @Override public String backendDescription() {
        return "remote STT model=" + model + " endpoint=" + transport.endpoint();
    }
    @Override public void close() { transport.close(); }

    private JsonObject request(UUID requestId, UUID sessionId, List<byte[]> frames) {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", requestId.toString());
        body.addProperty("sessionId", sessionId.toString());
        body.addProperty("model", model);
        JsonArray encoded = new JsonArray();
        if (frames != null) frames.forEach(frame ->
                encoded.add(Base64.getEncoder().encodeToString(frame.clone())));
        body.add("opusFrames", encoded);
        return body;
    }
    private static SpeechTranscript transcript(JsonObject value) {
        return new SpeechTranscript(text(value, "text"), number(value, "decodeMs"),
                value.has("inferenceMs") ? number(value, "inferenceMs")
                        : number(value, "whisperMs"), text(value, "language"));
    }
    private static String text(JsonObject value, String key) {
        return value.has(key) && !value.get(key).isJsonNull()
                ? value.get(key).getAsString() : "";
    }
    private static long number(JsonObject value, String key) {
        return value.has(key) ? value.get(key).getAsLong() : 0L;
    }
}
