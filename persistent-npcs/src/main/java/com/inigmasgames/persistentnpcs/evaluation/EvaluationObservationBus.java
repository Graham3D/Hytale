package com.inigmasgames.persistentnpcs.evaluation;

import com.inigmasgames.persistentnpcs.orbis.OrbisEvent;
import com.inigmasgames.persistentnpcs.orbis.OrbisEventType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Typed, bounded evaluation projection of the existing Orbis event stream. */
public final class EvaluationObservationBus implements Consumer<OrbisEvent> {
    private final int capacity;
    private final CopyOnWriteArrayList<EvaluationContracts.StageObservation> values =
            new CopyOnWriteArrayList<>();

    public EvaluationObservationBus() { this(4_096); }
    public EvaluationObservationBus(int capacity) { this.capacity = Math.max(128, capacity); }

    @Override public void accept(OrbisEvent event) {
        if (event == null) return;
        values.add(new EvaluationContracts.StageObservation(boundary(event.type()),
                event.sequence(), event.at(), uuid(event.turnId() == null ? null
                        : event.turnId().value()), uuid(event.responseId() == null ? null
                        : event.responseId().value()), event.type(), event.facts()));
        while (values.size() > capacity) values.removeFirst();
    }

    public List<EvaluationContracts.StageObservation> snapshot() {
        return List.copyOf(values);
    }

    public List<EvaluationContracts.StageObservation> forTurn(UUID turnId) {
        return values.stream().filter(value -> java.util.Objects.equals(
                turnId, value.turnId())).toList();
    }

    public List<EvaluationContracts.StageObservation> since(long sequenceExclusive) {
        return values.stream().filter(value -> value.sequence() > sequenceExclusive).toList();
    }

    public long latestSequence() {
        return values.isEmpty() ? 0L : values.getLast().sequence();
    }

    public void clear() { values.clear(); }

    private static EvaluationContracts.BoundaryId boundary(OrbisEventType type) {
        return switch (type) {
            case TURN_CREATED, CAPTURE_STARTED, CAPTURE_FRAME_ACCEPTED, CAPTURE_OVERFLOW,
                    CAPTURE_FINALIZED, STT_SELECTED, STT_STARTED, STT_COMPLETED,
                    AUTHORITATIVE_TRANSCRIPT_ACCEPTED, DUPLICATE_UTTERANCE_SUPPRESSED,
                    STT_FAILED, AUDIENCE_STARTED, AUDIENCE_RESOLVED, LISTENER_HEARD,
                    LISTENER_DELIVERED, RESPONSE_CANDIDATE, RESPONSE_OWNER_SELECTED,
                    RESPONSE_SUPPRESSED, BRANCH_CREATED, BRANCH_DISPATCHED ->
                        EvaluationContracts.BoundaryId.INGRESS;
            case TURN_PLAN_COMPILED -> EvaluationContracts.BoundaryId.TURN_PLAN;
            case COGNITION_PENDING, CONTEXT_BUILDING ->
                        EvaluationContracts.BoundaryId.DIALOGUE_STATE;
            case CONTRACT_BUDGET_PLANNED -> EvaluationContracts.BoundaryId.ANSWER_PLAN;
            case LLM_QUEUED, LLM_DISPATCHED -> EvaluationContracts.BoundaryId.CONTEXT_RENDER;
            case LLM_STREAMING, DECISION_VALIDATING -> EvaluationContracts.BoundaryId.PROVIDER;
            case CONTRACT_VALID, CONTRACT_INVALID, TRUNCATED_OUTPUT,
                    RECOVERY_ATTEMPTED, RECOVERY_SUCCEEDED, RECOVERY_EXHAUSTED,
                    DECISION_REJECTED -> EvaluationContracts.BoundaryId.CLAIM_FIREWALL;
            case PHRASE_VALIDATED, CANONICAL_SPEECH_SEGMENT_APPENDED,
                    CANONICAL_SPEECH_SEGMENT_COMMITTED,
                    CANONICAL_SPEECH_SEGMENT_DELIVERED, DECISION_COMMITTED,
                    SPEECH_QUEUED, TTS_SYNTHESIZING, AUDIO_READY, PLAYBACK_QUEUED,
                    SPEAKING, CHUNK_PLAYBACK_COMPLETE, SPEECH_COMPLETE,
                    SPEECH_CANCELLED, SPEECH_INTERRUPTED, TTS_CANCELLED,
                    TTS_RESULT_DISCARDED_STALE, PLAYBACK_INTERRUPTED, TTS_FAILED,
                    PLAYBACK_FAILED, SPEECH_TIMED_OUT ->
                        EvaluationContracts.BoundaryId.CANONICAL_RESPONSE;
            case RESOURCE_SNAPSHOT, RESOURCE_REQUESTED, RESOURCE_ADMITTED,
                    RESOURCE_DEFERRED, RESOURCE_RECHECK, RESOURCE_RECLAIM_ATTEMPT,
                    RESOURCE_ADMISSION_FAILED, RESOURCE_RELEASED, RESOURCE_PRESSURE,
                    BACKEND_SELECTED, PROVIDER_BUSY, RESOURCE_TIMEOUT ->
                        EvaluationContracts.BoundaryId.CLEANUP;
            case BARGE_IN_CANDIDATE, BARGE_IN_CONFIRMED, FLOOR_GRANTED, FLOOR_RELEASED,
                    DEFERRED_TOPIC_CREATED, DEFERRED_TOPIC_CONSUMED,
                    DEFERRED_TOPIC_EXPIRED, BRANCH_COMPLETED, CALLBACK_REJECTED_STALE,
                    BRANCH_CANCELLED, TURN_COMPLETED, TURN_CANCELLED, TURN_FAILED,
                    DIAGNOSTIC -> EvaluationContracts.BoundaryId.CLEANUP;
        };
    }

    private static UUID uuid(UUID value) { return value; }
}
