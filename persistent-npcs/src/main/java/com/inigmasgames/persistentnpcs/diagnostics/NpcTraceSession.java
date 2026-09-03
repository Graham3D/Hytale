package com.inigmasgames.persistentnpcs.diagnostics;

import com.google.gson.JsonObject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;

/** One non-persistent operator-owned black-box recording session for one authored NPC. */
public final class NpcTraceSession implements AutoCloseable {
    private final UUID sessionId;
    private final UUID operatorId;
    private final UUID npcId;
    private final String npcName;
    private final Instant startedAt;
    private final Path outputPath;
    private final BufferedWriter writer;
    private boolean closed;

    NpcTraceSession(UUID sessionId, UUID operatorId, UUID npcId, String npcName,
            Instant startedAt, Path outputPath) throws IOException {
        this.sessionId = sessionId;
        this.operatorId = operatorId;
        this.npcId = npcId;
        this.npcName = npcName;
        this.startedAt = startedAt;
        this.outputPath = outputPath;
        this.writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    public UUID sessionId() { return sessionId; }
    public UUID operatorId() { return operatorId; }
    public UUID npcId() { return npcId; }
    public String npcName() { return npcName; }
    public Instant startedAt() { return startedAt; }
    public Path outputPath() { return outputPath; }

    synchronized void append(JsonObject source) {
        if (closed) return;
        JsonObject event = source == null ? new JsonObject() : source.deepCopy();
        event.addProperty("schema", "ImmersiveNPCs.NpcTrace.v1");
        event.addProperty("traceSessionId", sessionId.toString());
        event.addProperty("operatorId", operatorId.toString());
        event.addProperty("npcId", npcId.toString());
        event.addProperty("npcName", npcName);
        if (!event.has("at")) event.addProperty("at", Instant.now().toString());
        try {
            writer.write(event.toString());
            writer.newLine();
            writer.flush();
        } catch (IOException failure) {
            closeQuietly();
            throw new IllegalStateException("Could not append NPC trace", failure);
        }
    }

    synchronized void stop(String reason) {
        if (closed) return;
        JsonObject event = new JsonObject();
        event.addProperty("event", "TRACE_STOPPED");
        event.addProperty("reason", reason == null ? "stopped" : reason);
        append(event);
        closeQuietly();
    }

    @Override
    public synchronized void close() {
        closeQuietly();
    }

    private void closeQuietly() {
        if (closed) return;
        closed = true;
        try {
            writer.close();
        } catch (IOException ignored) {
            // The owning manager reports append failures; shutdown remains best-effort.
        }
    }
}
