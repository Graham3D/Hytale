package com.inigmasgames.persistentnpcs.sentinel;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Immutable S1 contracts. ENFORCE is intentionally observational until S2. */
public final class SentinelContracts {
    private SentinelContracts() { }

    public enum SentinelMode { OFF, OBSERVE, ENFORCE }
    public enum Category {
        CONTRACT_DRIFT, OWNERSHIP_VIOLATION, INPUT_INTEGRITY, PROVIDER_LIFECYCLE,
        RESOURCE_ENVELOPE, SPEECH_DELIVERY_INTEGRITY, EPISTEMIC_AUTHORITY,
        ACTION_TRUTH, DURABLE_STATE_INTEGRITY, PERFORMANCE_DEGRADATION
    }
    public enum Boundary {
        TRANSCRIPT_ACCEPT, TURN_PLAN_COMPILE, CONTEXT_RENDER_COMPLETE,
        PROVIDER_DISPATCH, PROVIDER_STREAM_EVENT, PROVIDER_TERMINAL,
        CLAIM_VALIDATION, SPEECH_LEDGER_APPEND, ACTION_COMMIT,
        TERMINAL_CLEANUP, READINESS_SAMPLE, LATENCY_WINDOW_UPDATE,
        BELIEF_WRITE_PROPOSED
    }
    public enum Scope {
        REQUEST, TURN, BRANCH, CONVERSATION_SCENE, NPC, ROUTE, PROVIDER,
        RESOURCE_PROFILE, PERSISTENCE_STREAM, WORLD, GLOBAL_RUNTIME
    }
    public enum Severity { NOTICE, WARNING, DEGRADED, CRITICAL, FATAL_OPERATOR_REQUIRED }
    public enum Confidence { PROVEN, HIGH_CONFIDENCE, SUSPECT, INSUFFICIENT_DATA }
    public enum VerdictStatus {
        PASS, FAIL, NOT_APPLICABLE, INSUFFICIENT_DATA, EVALUATOR_ERROR
    }
    public enum Health {
        HEALTHY, SUSPECT, DEGRADED, RECOVERING, QUARANTINED,
        FAILED_OPERATOR_REQUIRED
    }
    public enum CircuitState { CLOSED, OPEN, HALF_OPEN }
    public enum RecoveryAction {
        REJECT_SIDE_EFFECT, FAIL_FAST_WITH_SAFE_REASON, RECOMPILE_TURN_PLAN,
        PRUNE_CONTEXT_AND_RECOMPILE, DROP_UNSUPPORTED_CLAUSE,
        REALIZE_SAFE_ANSWERPLAN_FALLBACK, CANCEL_PROVIDER_REQUEST,
        WAIT_FOR_PROVIDER_DRAIN, RESTART_PROVIDER_PROCESS,
        CLOSE_PLAYBACK_PRESERVE_PARTIAL, DOWNGRADE_TO_APPROVED_RESOURCE_PROFILE,
        QUARANTINE_ROUTE, QUARANTINE_PROVIDER, REJECT_DURABLE_WRITE,
        DISABLE_PERSISTENCE_WRITES_READ_ONLY, REQUEST_OPERATOR_ATTENTION
    }
    public enum RecoveryState {
        NOT_REQUIRED, CONTAINED, REQUESTED, RECOVERING, VERIFIED, FAILED,
        SKIPPED_CIRCUIT_OPEN
    }

    public record InvariantDefinition(String id, int version, String description,
            Category category, Boundary boundary, Scope scope, Severity severity,
            Confidence minimumEnforcementConfidence, String evaluatorId,
            String authoritativeOwner, String futureRecoveryPolicyId,
            Duration evaluationDeadline, boolean enabledInObserve,
            boolean enabledInEnforce) {
        public InvariantDefinition {
            if (id == null || id.isBlank() || version < 1 || description == null
                    || category == null || boundary == null || scope == null
                    || severity == null || minimumEnforcementConfidence == null
                    || evaluatorId == null || evaluatorId.isBlank()
                    || authoritativeOwner == null || authoritativeOwner.isBlank()
                    || futureRecoveryPolicyId == null || evaluationDeadline == null
                    || evaluationDeadline.isNegative() || evaluationDeadline.isZero()) {
                throw new IllegalArgumentException("complete versioned invariant required");
            }
        }
    }

    public record InvariantVerdict(String invariantId, VerdictStatus status,
            Confidence confidence, String boundedReasonCode, List<String> evidenceIds,
            Instant evaluatedAt, long evaluationMicros) {
        public InvariantVerdict {
            evidenceIds = List.copyOf(evidenceIds == null ? List.of() : evidenceIds);
        }
    }

    public record DegradationSignal(String signalId, String invariantId,
            Severity severity, Confidence confidence, Scope scope, String scopeKey,
            String boundedReasonCode, List<String> correlationIds, String signatureSeed,
            Instant detectedAt) {
        public DegradationSignal {
            correlationIds = List.copyOf(correlationIds == null ? List.of() : correlationIds);
        }
    }

    public record EnforcementDecision(boolean allowed, String invariantId,
            String reasonCode, String failureSignature, String recoveryPolicyId,
            RecoveryState recoveryState, CircuitState circuitState,
            List<RecoveryAction> requestedActions) {
        public EnforcementDecision {
            requestedActions = List.copyOf(requestedActions == null
                    ? List.of() : requestedActions);
        }
        public static EnforcementDecision allow() {
            return new EnforcementDecision(true, "", "PASS", "", "",
                    RecoveryState.NOT_REQUIRED, CircuitState.CLOSED, List.of());
        }
    }
}
