package com.inigmasgames.persistentnpcs.autonomy;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persistent in-progress-operation gate for long-lived model work. */
public final class AgentOperationStore {
    private final Path path;
    private final Map<UUID, AgentOperation> operations = new LinkedHashMap<>();

    public AgentOperationStore(Path dataDirectory) {
        path = dataDirectory.resolve("persistence/agent-operations.json");
    }

    public synchronized void load() {
        operations.clear();
        if (Files.exists(path)) {
            AgentOperation[] loaded = JsonFiles.read(path, AgentOperation[].class);
            if (loaded != null) Arrays.stream(loaded).forEach(operation ->
                    operations.put(operation.operationId(), operation));
        } else save();
        expire(Instant.now());
    }

    public synchronized AgentOperation claim(
            String kind, Set<UUID> npcIds, String input, Instant now, Duration timeout) {
        expire(now);
        boolean busy = operations.values().stream().filter(operation -> operation.active(now))
                .anyMatch(operation -> operation.npcIds().stream().anyMatch(npcIds::contains));
        if (busy) throw new IllegalStateException(
                "One or more NPCs already have an in-progress operation");
        AgentOperation operation = new AgentOperation(UUID.randomUUID(), kind,
                Set.copyOf(npcIds), now, now.plus(timeout),
                input == null ? "" : input.strip(), "IN_PROGRESS", "");
        operations.put(operation.operationId(), operation);
        save();
        return operation;
    }

    public synchronized void complete(UUID operationId, boolean success, String result) {
        AgentOperation operation = operations.get(operationId);
        if (operation == null) return;
        operations.put(operationId, new AgentOperation(operation.operationId(), operation.kind(),
                operation.npcIds(), operation.startedAt(), operation.deadline(),
                operation.authoritativeInput(), success ? "COMPLETED" : "FAILED",
                result == null ? "" : result.strip()));
        save();
    }

    public synchronized boolean busy(UUID npcId, Instant now) {
        expire(now);
        return operations.values().stream().filter(operation -> operation.active(now))
                .anyMatch(operation -> operation.npcIds().contains(npcId));
    }

    public synchronized boolean ownsActive(
            UUID operationId, UUID npcId, Instant now) {
        expire(now);
        AgentOperation operation = operations.get(operationId);
        return operation != null && operation.active(now)
                && operation.npcIds().contains(npcId);
    }

    public synchronized java.util.List<AgentOperation> all() {
        return java.util.List.copyOf(operations.values());
    }

    public synchronized java.util.Optional<AgentOperation> activeFor(UUID npcId, Instant now) {
        expire(now);
        return operations.values().stream().filter(operation -> operation.active(now))
                .filter(operation -> operation.npcIds().contains(npcId)).findFirst();
    }

    public synchronized java.util.Optional<AgentOperation> latestFor(
            UUID npcId, String kind) {
        return operations.values().stream()
                .filter(operation -> operation.npcIds().contains(npcId))
                .filter(operation -> kind == null || operation.kind().equalsIgnoreCase(kind))
                .max(java.util.Comparator.comparing(AgentOperation::startedAt));
    }

    private void expire(Instant now) {
        boolean changed = false;
        for (Map.Entry<UUID, AgentOperation> entry : operations.entrySet()) {
            AgentOperation operation = entry.getValue();
            if ("IN_PROGRESS".equals(operation.status()) && !now.isBefore(operation.deadline())) {
                entry.setValue(new AgentOperation(operation.operationId(), operation.kind(),
                        operation.npcIds(), operation.startedAt(), operation.deadline(),
                        operation.authoritativeInput(), "FAILED", "operation timeout"));
                changed = true;
            }
        }
        if (changed) save();
    }

    private void save() { JsonFiles.writeAtomic(path, operations.values()); }
}
