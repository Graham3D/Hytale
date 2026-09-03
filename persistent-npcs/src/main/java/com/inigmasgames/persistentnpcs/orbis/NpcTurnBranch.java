package com.inigmasgames.persistentnpcs.orbis;

import java.time.Instant;
import com.inigmasgames.persistentnpcs.cognition.NpcDecision;
import com.inigmasgames.persistentnpcs.conversation.ConversationOutcome;
import java.util.UUID;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Per-listener branch identity and immutable provider attribution. */
public final class NpcTurnBranch {
    public enum State {
        SUPPRESSED,
        COGNITION_PENDING,
        CONTEXT_BUILDING,
        LLM_QUEUED,
        LLM_STREAMING,
        DECISION_VALIDATING,
        DECISION_COMMITTED,
        SPEECH_QUEUED,
        TTS_SYNTHESIZING,
        AUDIO_READY,
        PLAYBACK_QUEUED,
        SPEAKING,
        SPEECH_COMPLETE,
        INTERRUPTED,
        SPEECH_CANCELLED,
        TTS_FAILED,
        PLAYBACK_FAILED,
        TIMED_OUT,
        COMPLETED,
        CANCELLED,
        FAILED
    }
    private final BranchId branchId;
    private final ResponseId responseId;
    private final ProviderRequestId providerRequestId;
    private final UUID npcId;
    private final String npcName;
    private final long epoch;
    private final String provider;
    private final String model;
    private final Instant createdAt;
    private final CancellationScope cancellation;
    private final boolean responseOwner;
    private State state;
    private String terminalResult = "";
    private BranchCognitionSnapshot cognitionSnapshot;
    private NpcDecision decision;
    private ConversationOutcome outcome;
    private String canonicalSpokenText = "";
    private String validationResult = "NOT_RUN";
    private String failureReason = "";
    private final CanonicalSpeechLedger speechLedger;
    private SpeechChunkId activeSpeechChunkId;
    private TtsRequestId activeTtsRequestId;
    private PlaybackId activePlaybackId;
    private String ttsProvider = "unknown";
    private String voicePreset = "";
    private String voiceReference = "";
    private String speechMetrics = "";
    private final Map<SpeechChunkId, SpeechDeliveryState> deliveryStates =
            new LinkedHashMap<>();
    private boolean speechInterruptionPending;
    private long bargeInConfirmedNanos;

    public NpcTurnBranch(BranchId branchId, ResponseId responseId, UUID npcId,
            String npcName, long epoch, String provider, String model,
            CancellationScope cancellation, boolean responseOwner) {
        this(branchId, responseId, ProviderRequestId.create(), npcId, npcName, epoch,
                provider, model, cancellation, responseOwner);
    }

    public NpcTurnBranch(BranchId branchId, ResponseId responseId,
            ProviderRequestId providerRequestId, UUID npcId,
            String npcName, long epoch, String provider, String model,
            CancellationScope cancellation, boolean responseOwner) {
        this.branchId = branchId;
        this.responseId = responseId;
        this.speechLedger = new CanonicalSpeechLedger(responseId);
        this.providerRequestId = providerRequestId == null
                ? ProviderRequestId.create() : providerRequestId;
        this.npcId = npcId;
        this.npcName = npcName == null ? "" : npcName;
        this.epoch = epoch;
        this.provider = provider == null ? "unknown" : provider;
        this.model = model == null ? "unknown" : model;
        this.createdAt = Instant.now();
        this.cancellation = cancellation;
        this.responseOwner = responseOwner;
        this.state = responseOwner ? State.COGNITION_PENDING : State.SUPPRESSED;
    }

    void state(State value) {
        if (terminal(state)) return;
        state = value;
    }
    void terminalResult(String value) { terminalResult = value == null ? "" : value; }
    void cognitionSnapshot(BranchCognitionSnapshot value) { cognitionSnapshot = value; }
    void decision(NpcDecision value, String validation) {
        decision = value;
        canonicalSpokenText = value == null ? "" : value.spokenText();
        validationResult = validation == null ? "" : validation;
    }
    void outcome(ConversationOutcome value) { outcome = value; }
    void failureReason(String value) { failureReason = value == null ? "" : value; }
    void speechChunks(List<CanonicalSpeechChunk> value) {
        if (!speechLedger.chunks().isEmpty()) {
            throw new IllegalStateException("canonical speech ledger already contains segments");
        }
        List<CanonicalSpeechChunk> speechChunks = List.copyOf(value == null ? List.of() : value);
        for (CanonicalSpeechChunk chunk : speechChunks) {
            speechLedger.append(chunk.id(), chunk.index(), chunk.text(), chunk.vocalState());
        }
        speechLedger.seal(speechChunks);
        deliveryStates.clear();
        speechChunks.forEach(chunk -> deliveryStates.put(
                chunk.id(), SpeechDeliveryState.NOT_DELIVERED));
    }
    void appendSpeechChunk(CanonicalSpeechChunk chunk) {
        if (chunk == null) throw new IllegalArgumentException("canonical chunk required");
        speechLedger.append(chunk.id(), chunk.index(), chunk.text(), chunk.vocalState());
        deliveryStates.put(chunk.id(), SpeechDeliveryState.NOT_DELIVERED);
    }
    void finalizeSpeechChunks(List<CanonicalSpeechChunk> value) {
        List<CanonicalSpeechChunk> finalized = speechLedger.seal(value);
        finalized.forEach(chunk -> deliveryStates.putIfAbsent(
                chunk.id(), SpeechDeliveryState.NOT_DELIVERED));
    }
    void delivered(SpeechChunkId id) {
        if (id != null && deliveryStates.containsKey(id)) {
            deliveryStates.put(id, SpeechDeliveryState.DELIVERED);
            speechLedger.delivered(id);
        }
    }
    void partial(SpeechChunkId id) {
        if (id != null && deliveryStates.get(id) != SpeechDeliveryState.DELIVERED) {
            deliveryStates.put(id, SpeechDeliveryState.PARTIAL);
            speechLedger.partial(id);
        }
    }
    void speechInterruptionPending(long confirmedNanos) {
        speechInterruptionPending = true;
        bargeInConfirmedNanos = confirmedNanos;
    }
    void speechProgress(SpeechChunkId chunkId, TtsRequestId ttsId, PlaybackId playbackId,
            String provider, String preset, String reference, String metrics) {
        if (chunkId != null) activeSpeechChunkId = chunkId;
        if (ttsId != null) activeTtsRequestId = ttsId;
        if (playbackId != null) activePlaybackId = playbackId;
        if (provider != null && !provider.isBlank()) ttsProvider = provider;
        if (preset != null) voicePreset = preset;
        if (reference != null) voiceReference = reference;
        if (metrics != null) speechMetrics = metrics;
    }

    public static boolean terminal(State value) {
        return value == State.COMPLETED || value == State.CANCELLED
                || value == State.FAILED || value == State.SPEECH_COMPLETE
                || value == State.INTERRUPTED
                || value == State.SPEECH_CANCELLED || value == State.TTS_FAILED
                || value == State.PLAYBACK_FAILED || value == State.TIMED_OUT;
    }

    public BranchId branchId() { return branchId; }
    public ResponseId responseId() { return responseId; }
    public ProviderRequestId providerRequestId() { return providerRequestId; }
    public UUID npcId() { return npcId; }
    public String npcName() { return npcName; }
    public long epoch() { return epoch; }
    public String provider() { return provider; }
    public String model() { return model; }
    public Instant createdAt() { return createdAt; }
    public CancellationScope cancellation() { return cancellation; }
    public boolean responseOwner() { return responseOwner; }
    public State state() { return state; }
    public boolean heard() { return true; }
    public boolean delivered() { return true; }
    public boolean responseCandidate() { return true; }
    public String terminalResult() { return terminalResult; }
    public BranchCognitionSnapshot cognitionSnapshot() { return cognitionSnapshot; }
    public NpcDecision decision() { return decision; }
    public ConversationOutcome outcome() { return outcome; }
    public String canonicalSpokenText() { return canonicalSpokenText; }
    public String validationResult() { return validationResult; }
    public String failureReason() { return failureReason; }
    public List<CanonicalSpeechChunk> speechChunks() { return speechLedger.chunks(); }
    public CanonicalSpeechLedger speechLedger() { return speechLedger; }
    public SpeechChunkId activeSpeechChunkId() { return activeSpeechChunkId; }
    public TtsRequestId activeTtsRequestId() { return activeTtsRequestId; }
    public PlaybackId activePlaybackId() { return activePlaybackId; }
    public String ttsProvider() { return ttsProvider; }
    public String voicePreset() { return voicePreset; }
    public String voiceReference() { return voiceReference; }
    public String speechMetrics() { return speechMetrics; }
    public Map<SpeechChunkId, SpeechDeliveryState> deliveryStates() {
        return Map.copyOf(deliveryStates);
    }
    public boolean speechInterruptionPending() { return speechInterruptionPending; }
    public long bargeInConfirmedNanos() { return bargeInConfirmedNanos; }
    public SpeechDeliveryReport deliveryReport(String reason) {
        List<CanonicalSpeechChunk> speechChunks = speechLedger.chunks();
        List<CanonicalSpeechChunk> delivered = speechChunks.stream().filter(chunk ->
                deliveryStates.get(chunk.id()) == SpeechDeliveryState.DELIVERED).toList();
        CanonicalSpeechChunk partial = speechChunks.stream().filter(chunk ->
                deliveryStates.get(chunk.id()) == SpeechDeliveryState.PARTIAL)
                .findFirst().orElse(null);
        List<CanonicalSpeechChunk> notDelivered = speechChunks.stream().filter(chunk ->
                deliveryStates.get(chunk.id()) == SpeechDeliveryState.NOT_DELIVERED).toList();
        return new SpeechDeliveryReport(delivered, partial, notDelivered, reason);
    }
}
