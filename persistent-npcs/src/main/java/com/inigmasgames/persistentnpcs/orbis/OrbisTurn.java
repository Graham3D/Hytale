package com.inigmasgames.persistentnpcs.orbis;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Single authoritative state object for one player voice turn. */
public final class OrbisTurn {
    public enum State {
        CAPTURING, FINALIZING_INPUT, TRANSCRIBING, RESOLVING_AUDIENCE,
        DISPATCHED, ACTIVE, COMPLETED, CANCELLED, FAILED
    }

    private final TurnId turnId;
    private final UtteranceId utteranceId;
    private final UUID playerId;
    private final UUID worldId;
    private final TurnIngressSource ingressSource;
    private final UUID originalPhysicalUtteranceId;
    private final long epoch;
    private final Instant createdAt;
    private final CancellationScope cancellation;
    private final ProviderRequestId sttRequestId = ProviderRequestId.create();
    private final Map<UUID, NpcTurnBranch> branches = new LinkedHashMap<>();
    private State state = State.CAPTURING;
    private String transcript = "";
    private String rawTranscript = "";
    private AudienceSnapshot audienceSnapshot;

    public OrbisTurn(TurnId turnId, UtteranceId utteranceId, UUID playerId, long epoch) {
        this(turnId, utteranceId, playerId, null, epoch, new CancellationScope());
    }

    public OrbisTurn(TurnId turnId, UtteranceId utteranceId, UUID playerId, long epoch,
            CancellationScope cancellation) {
        this(turnId, utteranceId, playerId, null, epoch, cancellation);
    }

    public OrbisTurn(TurnId turnId, UtteranceId utteranceId, UUID playerId,
            UUID worldId, long epoch, CancellationScope cancellation) {
        this(turnId, utteranceId, playerId, worldId, epoch,
                TurnIngressSource.VOICE_CAPTURE,
                utteranceId == null ? null : utteranceId.value(), cancellation);
    }

    public OrbisTurn(TurnId turnId, UtteranceId utteranceId, UUID playerId,
            UUID worldId, long epoch, TurnIngressSource ingressSource,
            UUID originalPhysicalUtteranceId, CancellationScope cancellation) {
        this.turnId = turnId;
        this.utteranceId = utteranceId;
        this.playerId = playerId;
        this.worldId = worldId;
        this.ingressSource = ingressSource == null
                ? TurnIngressSource.UNKNOWN_TEXT : ingressSource;
        this.originalPhysicalUtteranceId = originalPhysicalUtteranceId;
        this.epoch = epoch;
        this.createdAt = Instant.now();
        this.cancellation = cancellation == null ? new CancellationScope() : cancellation;
    }

    void state(State value) {
        if (state == State.COMPLETED || state == State.CANCELLED || state == State.FAILED) return;
        state = value;
    }
    void transcript(String value) { transcript = value == null ? "" : value; }
    void rawTranscript(String value) { rawTranscript = value == null ? "" : value; }
    void audienceSnapshot(AudienceSnapshot value) { audienceSnapshot = value; }
    void addBranch(NpcTurnBranch branch) { branches.put(branch.npcId(), branch); }

    public TurnId turnId() { return turnId; }
    public UtteranceId utteranceId() { return utteranceId; }
    public UUID playerId() { return playerId; }
    public UUID worldId() { return worldId; }
    public TurnIngressSource ingressSource() { return ingressSource; }
    public String ingressProvenance() { return ingressSource.chain(); }
    public UUID originalPhysicalUtteranceId() { return originalPhysicalUtteranceId; }
    public long epoch() { return epoch; }
    public Instant createdAt() { return createdAt; }
    public CancellationScope cancellation() { return cancellation; }
    public ProviderRequestId sttRequestId() { return sttRequestId; }
    public State state() { return state; }
    public String transcript() { return transcript; }
    public String rawTranscript() { return rawTranscript; }
    public AudienceSnapshot audienceSnapshot() { return audienceSnapshot; }
    public Map<UUID, NpcTurnBranch> branches() { return Map.copyOf(branches); }
}
