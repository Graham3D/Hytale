package com.inigmasgames.persistentnpcs.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.io.IOException;

/** Cancellable JSON transport for an explicitly configured Immersive inference worker. */
public final class RemoteInferenceTransport implements AutoCloseable {
    private final String baseEndpoint;
    private final int timeoutMillis;
    private final HttpClient client;
    private final ConcurrentHashMap<UUID, CompletableFuture<?>> inFlight =
            new ConcurrentHashMap<>();

    public RemoteInferenceTransport(String baseEndpoint, int timeoutMillis) {
        if (baseEndpoint == null || baseEndpoint.isBlank()) {
            throw new IllegalArgumentException("Remote provider endpoint is required");
        }
        this.baseEndpoint = baseEndpoint.strip().replaceAll("/+$", "");
        this.timeoutMillis = Math.max(100, timeoutMillis);
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.min(this.timeoutMillis, 10_000)))
                .build();
    }

    public String endpoint() { return baseEndpoint; }

    public CompletableFuture<JsonObject> post(UUID requestId, String path, JsonObject payload) {
        CompletableFuture<JsonObject> result = attempt(path, payload, 0);
        inFlight.put(requestId, result);
        return result.whenComplete((value, failure) -> inFlight.remove(requestId, result));
    }

    private CompletableFuture<JsonObject> attempt(
            String path, JsonObject payload, int attempt) {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofMillis(timeoutMillis))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(),
                        StandardCharsets.UTF_8)).build();
        CompletableFuture<JsonObject> result = client.sendAsync(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .thenApply(response -> parse(response.statusCode(), response.body()));
        return result.handle((value, failure) -> {
            if (failure == null) return CompletableFuture.completedFuture(value);
            Throwable cause = unwrap(failure);
            if (attempt < 1 && retryable(cause)) {
                return attempt(path, payload, attempt + 1);
            }
            return CompletableFuture.<JsonObject>failedFuture(cause);
        }).thenCompose(value -> value);
    }

    public CompletableFuture<AiProviderHealth> health() {
        long started = System.nanoTime();
        HttpRequest request = HttpRequest.newBuilder(uri("/health"))
                .timeout(Duration.ofMillis(Math.min(timeoutMillis, 5_000))).GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .orTimeout(Math.min(timeoutMillis, 5_000), TimeUnit.MILLISECONDS)
                .handle((response, failure) -> {
                    if (failure != null) return AiProviderHealth.unavailable(root(failure));
                    long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                    return response.statusCode() >= 200 && response.statusCode() < 300
                            ? AiProviderHealth.healthy("HTTP " + response.statusCode()
                                    + "; network=" + elapsed + "ms")
                            : AiProviderHealth.unavailable("HTTP " + response.statusCode());
                });
    }

    public void cancel(UUID requestId) {
        if (requestId == null) return;
        CompletableFuture<?> future = inFlight.remove(requestId);
        if (future != null) future.cancel(true);
        JsonObject body = new JsonObject();
        body.addProperty("requestId", requestId.toString());
        HttpRequest request = HttpRequest.newBuilder(uri("/v1/cancel"))
                .timeout(Duration.ofMillis(Math.min(timeoutMillis, 2_000)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .orTimeout(Math.min(timeoutMillis, 2_000), TimeUnit.MILLISECONDS)
                .exceptionally(ignored -> null);
    }

    private URI uri(String path) {
        return URI.create(baseEndpoint + (path.startsWith("/") ? path : "/" + path));
    }

    private static JsonObject parse(int status, String body) {
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("Remote inference returned HTTP " + status
                    + ": " + compact(body));
        }
        return JsonParser.parseString(body).getAsJsonObject();
    }

    private static String compact(String value) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").strip();
        return text.length() <= 500 ? text : text.substring(0, 500);
    }

    private static String root(Throwable failure) {
        Throwable current = unwrap(failure);
        return current.getClass().getSimpleName() + ": " + current.getMessage();
    }

    private static boolean retryable(Throwable failure) {
        return failure instanceof ConnectException || failure instanceof HttpTimeoutException
                || failure instanceof IOException || failure instanceof java.util.concurrent.TimeoutException;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    @Override public void close() {
        inFlight.forEach((id, future) -> future.cancel(true));
        inFlight.clear();
    }
}
