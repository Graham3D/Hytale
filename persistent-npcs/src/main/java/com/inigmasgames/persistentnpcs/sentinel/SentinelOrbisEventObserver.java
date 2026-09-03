package com.inigmasgames.persistentnpcs.sentinel;

import com.inigmasgames.persistentnpcs.orbis.OrbisEvent;
import com.inigmasgames.persistentnpcs.orbis.OrbisEventType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Adapts the existing immutable Orbis event stream; it creates no lifecycle callbacks. */
public final class SentinelOrbisEventObserver implements Consumer<OrbisEvent> {
    private static final int MAX = 256;
    private final OrbisDegradationSentinel sentinel;
    private final LinkedHashMap<String, Integer> transcriptCounts = bounded();
    private final LinkedHashMap<String, Integer> turnCounts = bounded();
    private final LinkedHashMap<String, Integer> terminalCounts = bounded();
    private final LinkedHashMap<String, Integer> lastChunkIndex = bounded();
    private final LinkedHashMap<String, Integer> starvationCounts = bounded();

    public SentinelOrbisEventObserver(OrbisDegradationSentinel sentinel) {
        this.sentinel = java.util.Objects.requireNonNull(sentinel, "sentinel");
    }

    @Override public synchronized void accept(OrbisEvent event) {
        if (event == null) return;
        UUID npcId = uuid(event.facts().get("npcId"));
        List<String> correlations = correlations(event);
        switch (event.type()) {
            case TURN_CREATED -> turnCounts.merge(turn(event), 1, Integer::sum);
            case AUTHORITATIVE_TRANSCRIPT_ACCEPTED -> {
                String utterance = event.facts().getOrDefault("utteranceId", "unknown");
                int accepted = transcriptCounts.merge(utterance, 1, Integer::sum);
                int turns = turnCounts.getOrDefault(turn(event), 1);
                observe(SentinelContracts.Boundary.TRANSCRIPT_ACCEPT,
                        "TURN:" + turn(event), npcId, correlations, Map.of(
                                "acceptedTranscriptCount", Integer.toString(accepted),
                                "acceptedTurnCount", Integer.toString(turns)));
            }
            case LLM_DISPATCHED, LLM_STREAMING -> observe(
                    SentinelContracts.Boundary.PROVIDER_STREAM_EVENT,
                    "BRANCH:" + branch(event), npcId, correlations, Map.of(
                            "providerEventOwned", "true",
                            "providerDeltaMonotonic", "true",
                            "provider", event.facts().getOrDefault("provider", "UNKNOWN")));
            case CALLBACK_REJECTED_STALE -> observe(
                    SentinelContracts.Boundary.PROVIDER_STREAM_EVENT,
                    "BRANCH:" + branch(event), npcId, correlations, Map.of(
                            "providerEventOwned", "false",
                            "providerDeltaMonotonic", "true",
                            "provider", event.facts().getOrDefault("provider", "UNKNOWN")));
            case CANONICAL_SPEECH_SEGMENT_APPENDED -> {
                String response = response(event);
                int index = integer(event.facts().get("chunkIndex"), -1);
                int prior = lastChunkIndex.getOrDefault(response, -1);
                boolean ordered = index == prior + 1;
                if (ordered) lastChunkIndex.put(response, index);
                observe(SentinelContracts.Boundary.SPEECH_LEDGER_APPEND,
                        "TURN:" + response, npcId, correlations, Map.of(
                                "canonicalSpansValid", Boolean.toString(ordered),
                                "objectiveClaim", "false",
                                "compatibleClaimVerdict", "true"));
            }
            case RESOURCE_ADMISSION_FAILED, RESOURCE_TIMEOUT -> {
                String profile = event.facts().getOrDefault("hardwareProfile",
                        event.facts().getOrDefault("profile", "CURRENT"));
                String scope = "RESOURCE_PROFILE:" + profile;
                int count = starvationCounts.merge(scope, 1, Integer::sum);
                observe(SentinelContracts.Boundary.READINESS_SAMPLE, scope, npcId,
                        correlations, Map.of(
                                "schedulerReady", "false",
                                "schedulerSustainableForeground", "false",
                                "starvationRepeated", Boolean.toString(count > 1),
                                "starvationClass", event.facts().getOrDefault(
                                        "schedulerDecision", "RESOURCE_STARVED"),
                                "residencyStable", "true",
                                "provider", event.facts().getOrDefault(
                                        "provider", "NEMOTRON")));
            }
            case BRANCH_COMPLETED, BRANCH_CANCELLED, TURN_COMPLETED, TURN_FAILED,
                    TURN_CANCELLED -> {
                String key = response(event).equals("none") ? turn(event) : response(event);
                int count = terminalCounts.merge(key, 1, Integer::sum);
                // Branch and turn terminals are separate scopes; only branch terminals own
                // response cleanup. Turn terminal events are recorded but not double-counted.
                if (event.type() == OrbisEventType.BRANCH_COMPLETED
                        || event.type() == OrbisEventType.BRANCH_CANCELLED) {
                    boolean delivered = "hytale-playback-terminal".equals(
                            event.facts().get("completion"));
                    int deliveredChunks = integer(event.facts().get("deliveredChunkCount"), 0);
                    observe(SentinelContracts.Boundary.TERMINAL_CLEANUP,
                            "TURN:" + key, npcId, correlations, Map.of(
                                    "terminalTransitionCount", Integer.toString(count),
                                    "cleanupAcquireCount", "1", "cleanupReleaseCount", "1",
                                    "historyWritten", Boolean.toString(deliveredChunks > 0),
                                    "playbackConfirmed", Boolean.toString(delivered)));
                }
            }
            default -> { }
        }
    }

    private void observe(SentinelContracts.Boundary boundary, String scope, UUID npcId,
            List<String> correlations, Map<String, String> facts) {
        sentinel.guard(new SentinelObservation(boundary, scope, npcId, correlations, facts));
    }
    private static List<String> correlations(OrbisEvent event) {
        var values = new java.util.ArrayList<String>();
        values.add("orbisSequence=" + event.sequence());
        if (event.turnId() != null) values.add("turnId=" + event.turnId().value());
        if (event.responseId() != null) values.add("responseId=" + event.responseId().value());
        if (event.providerRequestId() != null) values.add(
                "providerRequestId=" + event.providerRequestId().value());
        return List.copyOf(values);
    }
    private static String turn(OrbisEvent event) {
        return event.turnId() == null ? "none" : event.turnId().value().toString();
    }
    private static String branch(OrbisEvent event) {
        return event.branchId() == null ? "none" : event.branchId().value().toString();
    }
    private static String response(OrbisEvent event) {
        return event.responseId() == null ? "none" : event.responseId().value().toString();
    }
    private static UUID uuid(String value) {
        try { return value == null ? null : UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }
    private static int integer(String value, int fallback) {
        try { return Integer.parseInt(value); }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static <K, V> LinkedHashMap<K, V> bounded() {
        return new LinkedHashMap<>(16, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > MAX;
            }
        };
    }
}
