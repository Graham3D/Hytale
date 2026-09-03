package com.inigmasgames.persistentnpcs.sentinel;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Bounded asynchronous S2 incident store under the authoritative mod data root. */
public final class OrbisIncidentRecorder implements AutoCloseable {
    public static final String SANITIZER_VERSION = "S2.1";
    private static final int MAX_QUEUE = 64;
    private static final int MAX_SIGNATURES = 512;
    private final Path root;
    private final Consumer<String> diagnostics;
    private final Clock clock;
    private final ArrayBlockingQueue<IncidentWrite> queue = new ArrayBlockingQueue<>(MAX_QUEUE);
    private final LinkedHashMap<String, Anchor> anchors = new LinkedHashMap<>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Anchor> eldest) {
            return size() > MAX_SIGNATURES;
        }
    };
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong activeWrites = new AtomicLong();
    private final AtomicLong enqueuedWrites = new AtomicLong();
    private final AtomicLong processedWrites = new AtomicLong();
    private final Thread writer;
    private volatile String lastIncidentId = "none";
    private volatile RegressionCandidateExtractor candidateExtractor;

    public OrbisIncidentRecorder(Path modDataRoot, Consumer<String> diagnostics) {
        this(modDataRoot, Clock.systemUTC(), diagnostics);
    }
    public OrbisIncidentRecorder(Path modDataRoot, Clock clock, Consumer<String> diagnostics) {
        this.root = modDataRoot.resolve("diagnostics").resolve("incidents");
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
        writer = new Thread(this::run, "orbis-incident-writer");
        writer.setDaemon(true);
        writer.start();
    }

    public synchronized String capture(SentinelEvent event, SentinelObservation observation,
            SentinelContracts.EnforcementDecision enforcement) {
        if (event == null || event.failureSignature() == null || closed.get()) return "none";
        Anchor anchor = anchors.get(event.failureSignature());
        boolean first = anchor == null;
        if (anchor == null) {
            anchor = new Anchor("INC-" + UUID.randomUUID(), 0);
        }
        anchor = new Anchor(anchor.id(), anchor.occurrences() + 1);
        anchors.put(event.failureSignature(), anchor);
        lastIncidentId = anchor.id();
        var write = new IncidentWrite(anchor.id(), anchor.occurrences(), first, event,
                observation, enforcement);
        if (queue.offer(write)) enqueuedWrites.incrementAndGet();
        else dropped.incrementAndGet();
        RegressionCandidateExtractor extractor = candidateExtractor;
        if (extractor != null) extractor.capture(anchor.id(), event, observation, enforcement);
        return anchor.id();
    }

    public void setCandidateExtractor(RegressionCandidateExtractor value) {
        candidateExtractor = value;
    }

    /** Operator-requested sanitized incident export; never removes the source incident. */
    public Path exportIncident(String incidentId) {
        if (incidentId == null || !incidentId.matches("INC-[A-Za-z0-9-]+")) {
            throw new IllegalArgumentException("Invalid incident id");
        }
        try (var days = Files.isDirectory(root) ? Files.list(root) : java.util.stream.Stream
                .<Path>empty()) {
            Path source = days.filter(Files::isDirectory)
                    .map(day -> day.resolve(incidentId + ".json"))
                    .filter(Files::isRegularFile).findFirst().orElseThrow(() ->
                            new IllegalArgumentException("Unknown incident: " + incidentId));
            Path exports = root.getParent().resolve("exports");
            Files.createDirectories(exports);
            Path target = exports.resolve(incidentId + ".json");
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException("Could not export incident", failure);
        }
    }

    private void run() {
        while (!closed.get() || !queue.isEmpty()) {
            try {
                IncidentWrite value = queue.poll(250, TimeUnit.MILLISECONDS);
                if (value != null) {
                    activeWrites.incrementAndGet();
                    try { write(value); }
                    catch (RuntimeException | IOException failure) {
                        failures.incrementAndGet();
                        diagnostics.accept("ORBIS_INCIDENT_WRITER_FAILED type="
                                + failure.getClass().getSimpleName() + " reason="
                                + (failure.getMessage() == null ? "unknown"
                                        : failure.getMessage()));
                    } finally {
                        activeWrites.decrementAndGet();
                        processedWrites.incrementAndGet();
                    }
                }
            } catch (InterruptedException interrupted) {
                if (closed.get()) break;
            } catch (RuntimeException failure) {
                failures.incrementAndGet();
                diagnostics.accept("ORBIS_INCIDENT_WRITER_FAILED type="
                        + failure.getClass().getSimpleName() + " reason="
                        + (failure.getMessage() == null ? "unknown" : failure.getMessage()));
            }
        }
    }

    private void write(IncidentWrite value) throws IOException {
        Path day = root.resolve(LocalDate.ofInstant(value.event().at(), ZoneOffset.UTC).toString());
        Files.createDirectories(day);
        JsonObject json = compact(value);
        if (value.first()) {
            Path target = day.resolve(value.incidentId() + ".json");
            Path temporary = day.resolve(value.incidentId() + ".json.tmp");
            byte[] payload = JsonFiles.GSON.toJson(json).getBytes(StandardCharsets.UTF_8);
            Files.write(temporary, payload);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } else {
            JsonObject occurrence = new JsonObject();
            occurrence.addProperty("incidentId", value.incidentId());
            occurrence.addProperty("signature", value.event().failureSignature());
            occurrence.addProperty("occurrence", value.occurrence());
            occurrence.addProperty("at", value.event().at().toString());
            occurrence.addProperty("scopeKey", value.event().scopeKey());
            occurrence.addProperty("circuitState", value.enforcement().circuitState().name());
            Files.writeString(day.resolve("occurrences.jsonl"),
                    JsonFiles.GSON.toJson(occurrence) + System.lineSeparator(),
                    StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        }
    }

    private static JsonObject compact(IncidentWrite value) {
        JsonObject json = new JsonObject();
        SentinelEvent event = value.event();
        json.addProperty("schemaVersion", "S2.1");
        json.addProperty("incidentId", value.incidentId());
        json.addProperty("state", value.enforcement().allowed()
                ? "UNRESOLVED" : "CONTAINED");
        json.addProperty("invariantId", event.invariantId());
        json.addProperty("signature", event.failureSignature());
        json.addProperty("severity", event.severity().name());
        json.addProperty("confidence", event.confidence().name());
        json.addProperty("scopeKey", event.scopeKey());
        json.addProperty("reasonCode", event.reasonCode());
        json.addProperty("detectedAt", event.at().toString());
        json.addProperty("sentinelPolicyVersion", RecoveryPolicyRegistry.VERSION);
        json.addProperty("sanitizerVersion", SANITIZER_VERSION);
        json.add("correlationIds", JsonFiles.GSON.toJsonTree(event.correlationIds()));
        json.add("proof", JsonFiles.GSON.toJsonTree(value.observation().facts()));
        json.addProperty("recoveryPolicyId", value.enforcement().recoveryPolicyId());
        json.addProperty("recoveryState", value.enforcement().recoveryState().name());
        json.addProperty("circuitState", value.enforcement().circuitState().name());
        json.add("requestedActions", JsonFiles.GSON.toJsonTree(
                value.enforcement().requestedActions()));
        String checksum = SentinelPromptIdentity.hash(java.util.List.of(
                new com.inigmasgames.persistentnpcs.llm.ChatMessage("incident",
                        json.toString())));
        json.addProperty("payloadSha256", checksum);
        return json;
    }

    public void awaitIdle() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        long expected = enqueuedWrites.get();
        while ((!queue.isEmpty() || activeWrites.get() != 0
                || processedWrites.get() < expected)
                && System.nanoTime() < deadline) Thread.onSpinWait();
    }
    public Snapshot snapshot() {
        synchronized (this) {
            return new Snapshot(lastIncidentId, anchors.size(), queue.size(),
                    dropped.get(), failures.get());
        }
    }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        writer.interrupt();
        try { writer.join(2_000); } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
    public record Snapshot(String lastIncidentId, int uniqueSignatures, int queueDepth,
            long droppedWrites, long writerFailures) { }
    private record Anchor(String id, int occurrences) { }
    private record IncidentWrite(String incidentId, int occurrence, boolean first,
            SentinelEvent event, SentinelObservation observation,
            SentinelContracts.EnforcementDecision enforcement) { }
}
