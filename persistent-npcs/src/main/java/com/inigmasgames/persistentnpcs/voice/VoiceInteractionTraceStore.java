package com.inigmasgames.persistentnpcs.voice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.inigmasgames.persistentnpcs.orbis.OrbisEvent;
import com.inigmasgames.persistentnpcs.orbis.OrbisEventType;

/** Ephemeral native-inspector state for shared STT, audience, response, and playback. */
public final class VoiceInteractionTraceStore {
    private static final int MAX_TRACES = 256;
    private final Map<UUID, MutableTrace> byUtterance = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> utteranceByResponse = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> npcByResponse = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> latestByNpc = new ConcurrentHashMap<>();

    /** Production capture/audience observer. It never owns or mutates an Orbis turn. */
    public void observeOrbis(OrbisEvent event) {
        if (event == null || event.type() == OrbisEventType.CAPTURE_FRAME_ACCEPTED) return;
        UUID utteranceId = uuid(event.facts().get("utteranceId"));
        UUID playerId = uuid(event.facts().get("playerId"));
        if (utteranceId == null || playerId == null) return;
        MutableTrace trace = byUtterance.computeIfAbsent(utteranceId,
                ignored -> new MutableTrace(utteranceId, playerId, System.nanoTime()));
        UUID npcId = uuid(event.facts().get("npcId"));
        String npc = event.facts().getOrDefault("npc", "unknown NPC");
        synchronized (trace) {
            switch (event.type()) {
                case STT_COMPLETED -> {
                    trace.transcript = event.facts().getOrDefault("transcript", "");
                    trace.sttMillis = number(event.facts().get("wallMs"));
                }
                case CAPTURE_FINALIZED -> trace.endpointMillis = number(
                        event.facts().get("releaseDelayMs"));
                case AUDIENCE_RESOLVED -> trace.audienceMillis = number(
                        event.facts().get("audienceMs"));
                case LISTENER_HEARD -> {
                    trace.listenerCandidates = add(trace.listenerCandidates, npc);
                    trace.receivedBy = add(trace.receivedBy, npc);
                    if ("true".equals(event.facts().get("directAddress"))) {
                        trace.directTargets = add(trace.directTargets, npc);
                    }
                    trace.rangeClassification = event.facts().getOrDefault(
                            "rangeClass", trace.rangeClassification);
                }
                case RESPONSE_CANDIDATE -> trace.responseCandidates = add(
                        trace.responseCandidates, npc);
                case RESPONSE_OWNER_SELECTED -> trace.responseOwners = add(
                        trace.responseOwners, npc);
                case RESPONSE_SUPPRESSED -> {
                    if (npcId != null) {
                        Map<UUID, String> reasons = new java.util.LinkedHashMap<>(
                                trace.suppressionReasons);
                        reasons.put(npcId, event.facts().getOrDefault(
                                "reason", "LISTENER_NOT_SELECTED_TO_SPEAK"));
                        trace.suppressionReasons = Map.copyOf(reasons);
                    }
                }
                case BRANCH_CREATED -> {
                    if (npcId != null && event.responseId() != null) {
                        trace.responseIds.put(npcId, event.responseId().value());
                        trace.playbackStates.put(npcId,
                                "true".equals(event.facts().get("responseOwner"))
                                        ? "RESPONSE_STARTED" : "LISTENER_ONLY");
                    }
                }
                case BRANCH_CANCELLED -> {
                    if (npcId != null) {
                        trace.playbackStates.put(npcId, "CANCELLED");
                        trace.cancellationReasons.put(npcId,
                                event.facts().getOrDefault("reason", "cancelled"));
                    }
                }
                case SPEECH_QUEUED, TTS_SYNTHESIZING, AUDIO_READY,
                        PLAYBACK_QUEUED, SPEAKING, CHUNK_PLAYBACK_COMPLETE,
                        SPEECH_COMPLETE, TTS_FAILED, PLAYBACK_FAILED,
                        SPEECH_TIMED_OUT -> {
                    if (npcId != null) {
                        trace.playbackStates.put(npcId, event.type().name());
                        if (event.type() == OrbisEventType.SPEAKING
                                && !trace.firstAudioMillis.containsKey(npcId)) {
                            trace.firstAudioMillis.put(npcId,
                                    elapsed(trace.firstFrameNanos));
                        }
                    }
                }
                case SPEECH_CANCELLED -> {
                    if (npcId != null) {
                        trace.playbackStates.put(npcId, "SPEECH_CANCELLED");
                        trace.cancellationReasons.put(npcId,
                                event.facts().getOrDefault("reason", "cancelled"));
                    }
                }
                default -> { }
            }
        }
        if (npcId != null) {
            latestByNpc.put(npcId, utteranceId);
            if (event.responseId() != null) {
                utteranceByResponse.put(event.responseId().value(), utteranceId);
                npcByResponse.put(event.responseId().value(), npcId);
            }
        }
    }

    public void begin(UUID utteranceId, UUID playerId, long firstFrameNanos) {
        if (utteranceId == null || playerId == null) return;
        prune();
        byUtterance.putIfAbsent(utteranceId,
                new MutableTrace(utteranceId, playerId, firstFrameNanos));
    }

    public void transcribed(TranscribedPlayerUtterance utterance) {
        if (utterance == null) return;
        MutableTrace trace = byUtterance.computeIfAbsent(utterance.utteranceId(), ignored ->
                new MutableTrace(utterance.utteranceId(), utterance.playerId(),
                        utterance.firstFrameNanos()));
        synchronized (trace) {
            trace.transcript = utterance.transcript();
            trace.endpointMillis = utterance.endpointMillis();
            trace.sttMillis = utterance.sttMillis();
        }
    }

    public void audience(PlayerUtteranceEvent event, List<EligibleNpcListener> owners) {
        if (event == null) return;
        MutableTrace trace = byUtterance.computeIfAbsent(event.utteranceId(), ignored ->
                new MutableTrace(event.utteranceId(), event.playerId(), System.nanoTime()));
        synchronized (trace) {
            trace.transcript = event.transcript();
            trace.listenerCandidates = event.eligibleNpcListeners().stream()
                    .map(EligibleNpcListener::npcName).toList();
            trace.receivedBy = List.copyOf(trace.listenerCandidates);
            trace.directTargets = event.eligibleNpcListeners().stream()
                    .filter(EligibleNpcListener::directAddress)
                    .map(EligibleNpcListener::npcName).toList();
            trace.rangeClassification = event.eligibleNpcListeners().stream()
                    .anyMatch(value -> value.rangeClass() == UtteranceRangeClass.REMOTE_HAIL)
                            ? UtteranceRangeClass.REMOTE_HAIL.name()
                            : UtteranceRangeClass.ORDINARY.name();
            trace.responseOwners = (owners == null ? List.<EligibleNpcListener>of() : owners)
                    .stream().map(EligibleNpcListener::npcName).toList();
            trace.audienceMillis = event.audienceResolutionMillis();
        }
        event.eligibleNpcListeners().forEach(listener ->
                latestByNpc.put(listener.npcId(), event.utteranceId()));
    }

    public void audience(PlayerUtteranceAudienceService.Resolution resolution) {
        if (resolution == null) return;
        audience(resolution.event(), resolution.responseOwners());
        MutableTrace trace = byUtterance.get(resolution.event().utteranceId());
        if (trace == null) return;
        synchronized (trace) {
            trace.responseCandidates = resolution.responseOwners().stream()
                    .map(EligibleNpcListener::npcName).toList();
            trace.suppressionReasons = Map.copyOf(resolution.suppressionReasons());
            resolution.listenerObservations().forEach((npcId, observation) -> {
                trace.classifications.put(npcId,
                        observation.analysis().classification().name());
                trace.propositions.put(npcId, observation.analysis().propositions().stream()
                        .map(value -> value.proposition()).toList());
                trace.beliefWrites.put(npcId, observation.beliefWrites().stream()
                        .map(value -> value.beliefId()).toList());
                trace.memoryWrites.put(npcId, observation.memoryWrites());
            });
        }
    }

    public void bindResponse(UUID utteranceId, UUID npcId, UUID responseId,
            SpeechProjection projection) {
        MutableTrace trace = byUtterance.get(utteranceId);
        if (trace == null || responseId == null || npcId == null) return;
        synchronized (trace) {
            trace.responseIds.put(npcId, responseId);
            trace.projections.put(npcId,
                    projection == null ? SpeechProjection.NORMAL : projection);
            trace.playbackStates.put(npcId, "RESPONSE_STARTED");
        }
        utteranceByResponse.put(responseId, utteranceId);
        if (npcId != null) {
            npcByResponse.put(responseId, npcId);
            latestByNpc.put(npcId, utteranceId);
        }
    }

    public void playback(UUID responseId, String state) {
        MutableTrace trace = traceForResponse(responseId);
        if (trace == null) return;
        UUID npcId = npcByResponse.get(responseId);
        synchronized (trace) {
            // ClipPlayback completion can race an explicit/superseded cancellation.
            // Cancellation is authoritative and must remain visible in the inspector.
            if ("CANCELLED".equals(trace.playbackStates.get(npcId))) return;
            trace.playbackStates.put(npcId, clean(state, 80, "UNKNOWN"));
            if ("FIRST_AUDIBLE_FRAME".equals(state)
                    && !trace.firstAudioMillis.containsKey(npcId)) {
                trace.firstAudioMillis.put(npcId, elapsed(trace.firstFrameNanos));
            }
        }
    }

    public void cancelled(UUID responseId, String reason) {
        MutableTrace trace = traceForResponse(responseId);
        if (trace == null) return;
        UUID npcId = npcByResponse.get(responseId);
        synchronized (trace) {
            trace.playbackStates.put(npcId, "CANCELLED");
            trace.cancellationReasons.put(npcId,
                    clean(reason, 160, "explicit cancellation"));
        }
    }

    public Optional<Snapshot> latest(UUID npcId) {
        UUID utteranceId = latestByNpc.get(npcId);
        MutableTrace trace = utteranceId == null ? null : byUtterance.get(utteranceId);
        return Optional.ofNullable(trace).map(value -> value.snapshot(npcId));
    }

    private MutableTrace traceForResponse(UUID responseId) {
        UUID utteranceId = responseId == null ? null : utteranceByResponse.get(responseId);
        return utteranceId == null ? null : byUtterance.get(utteranceId);
    }

    private void prune() {
        if (byUtterance.size() < MAX_TRACES) return;
        List<MutableTrace> values = new ArrayList<>(byUtterance.values());
        values.sort(java.util.Comparator.comparing(value -> value.startedAt));
        values.stream().limit(MAX_TRACES / 2).forEach(trace -> {
            byUtterance.remove(trace.utteranceId, trace);
            trace.responseIds.values().forEach(responseId -> {
                utteranceByResponse.remove(responseId, trace.utteranceId);
                npcByResponse.remove(responseId);
            });
            latestByNpc.entrySet().removeIf(entry -> trace.utteranceId.equals(entry.getValue()));
        });
    }

    private static long elapsed(long startNanos) {
        return startNanos <= 0 ? -1 : java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                Math.max(0, System.nanoTime() - startNanos));
    }

    private static UUID uuid(String value) {
        try { return value == null ? null : UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static long number(String value) {
        try { return value == null ? -1 : Long.parseLong(value); }
        catch (NumberFormatException ignored) { return -1; }
    }

    private static List<String> add(List<String> values, String value) {
        if (values.contains(value)) return values;
        List<String> result = new ArrayList<>(values);
        result.add(value);
        return List.copyOf(result);
    }

    private static String clean(String value, int maximum, String fallback) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").strip();
        if (text.isBlank()) text = fallback;
        return text.length() <= maximum ? text : text.substring(0, maximum);
    }

    public record Snapshot(
            UUID utteranceId,
            UUID playerId,
            String transcript,
            List<String> listenerCandidates,
            List<String> receivedBy,
            List<String> directTargets,
            String rangeClassification,
            List<String> responseCandidates,
            List<String> responseOwners,
            String suppressionReason,
            String inputClassification,
            List<String> extractedPropositions,
            List<UUID> beliefWrites,
            List<UUID> memoryWrites,
            UUID responseId,
            UUID responseNpcId,
            SpeechProjection projection,
            String playbackState,
            String cancellationReason,
            long endpointMillis,
            long sttMillis,
            long audienceMillis,
            long firstAudioMillis) { }

    private static final class MutableTrace {
        private final UUID utteranceId;
        private final UUID playerId;
        private final Instant startedAt = Instant.now();
        private final long firstFrameNanos;
        private String transcript = "";
        private List<String> listenerCandidates = List.of();
        private List<String> receivedBy = List.of();
        private List<String> directTargets = List.of();
        private String rangeClassification = "UNRESOLVED";
        private List<String> responseCandidates = List.of();
        private List<String> responseOwners = List.of();
        private Map<UUID, String> suppressionReasons = Map.of();
        private final Map<UUID, String> classifications = new ConcurrentHashMap<>();
        private final Map<UUID, List<String>> propositions = new ConcurrentHashMap<>();
        private final Map<UUID, List<UUID>> beliefWrites = new ConcurrentHashMap<>();
        private final Map<UUID, List<UUID>> memoryWrites = new ConcurrentHashMap<>();
        private final Map<UUID, UUID> responseIds = new ConcurrentHashMap<>();
        private final Map<UUID, SpeechProjection> projections = new ConcurrentHashMap<>();
        private final Map<UUID, String> playbackStates = new ConcurrentHashMap<>();
        private final Map<UUID, String> cancellationReasons = new ConcurrentHashMap<>();
        private long endpointMillis = -1;
        private long sttMillis = -1;
        private long audienceMillis = -1;
        private final Map<UUID, Long> firstAudioMillis = new ConcurrentHashMap<>();

        private MutableTrace(UUID utteranceId, UUID playerId, long firstFrameNanos) {
            this.utteranceId = utteranceId;
            this.playerId = playerId;
            this.firstFrameNanos = firstFrameNanos;
        }

        private synchronized Snapshot snapshot(UUID npcId) {
            return new Snapshot(utteranceId, playerId, transcript,
                    List.copyOf(listenerCandidates), List.copyOf(receivedBy),
                    List.copyOf(directTargets), rangeClassification,
                    List.copyOf(responseCandidates), List.copyOf(responseOwners),
                    suppressionReasons.getOrDefault(npcId, ""),
                    classifications.getOrDefault(npcId, "UNKNOWN"),
                    propositions.getOrDefault(npcId, List.of()),
                    beliefWrites.getOrDefault(npcId, List.of()),
                    memoryWrites.getOrDefault(npcId, List.of()),
                    responseIds.get(npcId), npcId,
                    projections.getOrDefault(npcId, SpeechProjection.NORMAL),
                    playbackStates.getOrDefault(npcId, "LISTENER_ONLY"),
                    cancellationReasons.getOrDefault(npcId, ""), endpointMillis,
                    sttMillis, audienceMillis, firstAudioMillis.getOrDefault(npcId, -1L));
        }
    }
}
