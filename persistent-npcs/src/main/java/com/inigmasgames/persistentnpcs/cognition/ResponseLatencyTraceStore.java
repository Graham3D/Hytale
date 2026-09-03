package com.inigmasgames.persistentnpcs.cognition;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Response-ID scoped end-to-end latency trace shared by cognition, streaming, and voice. */
public final class ResponseLatencyTraceStore {
    private static final int MAX_RETAINED_TRACES = 512;
    private static final int RETAIN_AFTER_PRUNE = 256;

    public record StageTiming(ResponseLatencyStage stage, Instant at,
            long elapsedFromStartMillis, long durationMillis, long budgetMillis,
            boolean overBudget) { }

    public record Trace(UUID responseId, UUID npcId, UUID playerId, Instant startedAt,
            List<StageTiming> stages, boolean complete) {
        public boolean anyOverBudget() {
            return stages.stream().anyMatch(StageTiming::overBudget);
        }

        public long totalMillis() {
            return stages.stream()
                    .filter(value -> value.stage() == ResponseLatencyStage.TOTAL_RESPONSE_COMPLETION)
                    .mapToLong(StageTiming::elapsedFromStartMillis).max().orElseGet(() ->
                            stages.stream().mapToLong(StageTiming::elapsedFromStartMillis)
                                    .max().orElse(0));
        }
    }

    private final LatencyBudgetConfig budgets;
    private final Map<UUID, MutableTrace> byResponse = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> latestByNpc = new ConcurrentHashMap<>();

    public ResponseLatencyTraceStore() {
        this(LatencyBudgetConfig.defaults());
    }

    public ResponseLatencyTraceStore(LatencyBudgetConfig budgets) {
        this.budgets = budgets == null ? LatencyBudgetConfig.defaults() : budgets;
    }

    public void begin(UUID responseId, UUID npcId, UUID playerId) {
        if (responseId == null) return;
        pruneCompletedTraces();
        byResponse.put(responseId, new MutableTrace(responseId, npcId, playerId));
        if (npcId != null) latestByNpc.put(npcId, responseId);
    }

    private void pruneCompletedTraces() {
        int removeCount = byResponse.size() - RETAIN_AFTER_PRUNE;
        if (removeCount <= 0 || byResponse.size() < MAX_RETAINED_TRACES) return;
        byResponse.values().stream()
                .filter(trace -> trace.complete)
                .sorted(Comparator.comparing(trace -> trace.startedAt))
                .limit(removeCount)
                .map(trace -> trace.responseId)
                .toList()
                .forEach(responseId -> {
                    if (byResponse.remove(responseId) != null) {
                        latestByNpc.entrySet().removeIf(
                                entry -> responseId.equals(entry.getValue()));
                    }
                });
    }

    public long startedNanos(UUID responseId) {
        MutableTrace trace = byResponse.get(responseId);
        return trace == null ? System.nanoTime() : trace.startedNanos;
    }

    public void recordDuration(UUID responseId, ResponseLatencyStage stage, long durationMillis) {
        MutableTrace trace = byResponse.get(responseId);
        if (trace != null) trace.record(stage, Math.max(0, durationMillis), budgets.budget(stage));
    }

    public void mark(UUID responseId, ResponseLatencyStage stage) {
        MutableTrace trace = byResponse.get(responseId);
        if (trace != null) trace.record(stage, trace.elapsedMillis(), budgets.budget(stage));
    }

    public void complete(UUID responseId) {
        MutableTrace trace = byResponse.get(responseId);
        if (trace != null) {
            trace.record(ResponseLatencyStage.TOTAL_RESPONSE_COMPLETION,
                    trace.elapsedMillis(), budgets.budget(ResponseLatencyStage.TOTAL_RESPONSE_COMPLETION));
            trace.complete = true;
        }
    }

    public Optional<Trace> trace(UUID responseId) {
        return Optional.ofNullable(byResponse.get(responseId)).map(MutableTrace::snapshot);
    }

    public Optional<Trace> latest(UUID npcId) {
        return Optional.ofNullable(latestByNpc.get(npcId)).flatMap(this::trace);
    }

    public String compact(UUID responseId) {
        return trace(responseId).map(value -> value.stages().stream()
                .map(stage -> stage.stage() + "=" + stage.durationMillis() + "ms"
                        + (stage.overBudget() ? "[OVER]" : ""))
                .collect(java.util.stream.Collectors.joining(" "))).orElse("no trace");
    }

    private static final class MutableTrace {
        private final UUID responseId;
        private final UUID npcId;
        private final UUID playerId;
        private final Instant startedAt = Instant.now();
        private final long startedNanos = System.nanoTime();
        private final EnumMap<ResponseLatencyStage, StageTiming> stages =
                new EnumMap<>(ResponseLatencyStage.class);
        private volatile boolean complete;

        private MutableTrace(UUID responseId, UUID npcId, UUID playerId) {
            this.responseId = responseId;
            this.npcId = npcId;
            this.playerId = playerId;
        }

        private long elapsedMillis() {
            return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                    Math.max(0, System.nanoTime() - startedNanos));
        }

        private synchronized void record(
                ResponseLatencyStage stage, long durationMillis, long budgetMillis) {
            StageTiming value = new StageTiming(stage, Instant.now(), elapsedMillis(),
                    durationMillis, budgetMillis, durationMillis > budgetMillis);
            if (stage == ResponseLatencyStage.TOTAL_RESPONSE_COMPLETION) {
                stages.put(stage, value);
            } else {
                stages.putIfAbsent(stage, value);
            }
        }

        private synchronized Trace snapshot() {
            return new Trace(responseId, npcId, playerId, startedAt,
                    List.copyOf(stages.values()), complete);
        }
    }
}
