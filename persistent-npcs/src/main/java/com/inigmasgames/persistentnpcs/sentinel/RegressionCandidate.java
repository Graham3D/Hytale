package com.inigmasgames.persistentnpcs.sentinel;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Sanitized data fixture. Runtime code never turns a candidate into source code. */
public record RegressionCandidate(String schemaVersion, String candidateId,
        String sourceIncidentId, String sourceBuildRevision, UUID sourceNpcId,
        String failureSignature, String invariantId, FixtureKind fixtureKind,
        long deterministicSeed, Map<String, String> semanticInputs,
        Map<String, String> syntheticBehavior, String expectedInvariantOutcome,
        List<String> requiredHarnessCapabilities, CandidateStatus status,
        String sanitizerVersion, String payloadSha256, Instant createdAt,
        Instant updatedAt) {
    public static final String SCHEMA_VERSION = "S3.1";

    public RegressionCandidate {
        semanticInputs = Map.copyOf(semanticInputs == null ? Map.of() : semanticInputs);
        syntheticBehavior = Map.copyOf(syntheticBehavior == null
                ? Map.of() : syntheticBehavior);
        requiredHarnessCapabilities = List.copyOf(requiredHarnessCapabilities == null
                ? List.of() : requiredHarnessCapabilities);
    }

    public RegressionCandidate withStatus(CandidateStatus value, String checksum,
            Instant at) {
        return new RegressionCandidate(schemaVersion, candidateId, sourceIncidentId,
                sourceBuildRevision, sourceNpcId, failureSignature, invariantId,
                fixtureKind, deterministicSeed, semanticInputs, syntheticBehavior,
                expectedInvariantOutcome, requiredHarnessCapabilities, value,
                sanitizerVersion, checksum, createdAt, at);
    }

    public enum FixtureKind {
        TURN_PLAN, PROMPT_BUDGET, ROUTE_AUTHORITY, STT_PARTIAL_FINAL,
        PROVIDER_STREAM, PROVIDER_CANCEL_DRAIN, RESOURCE_SEQUENCE, ATOMIC_CLAIM,
        CANONICAL_SPEECH_LEDGER, ACTION_AUTHORITY, PERSISTENCE_EVENT,
        SNAPSHOT_REPLAY, QUEUE_CLEANUP, LATENCY_WINDOW
    }

    public enum CandidateStatus {
        NEW, REPLAYABLE, NON_DETERMINISTIC, REPLAY_PASSED_CURRENT_BUILD,
        REPLAY_FAILED_CURRENT_BUILD, PROMOTED_TO_SOURCE_FIXTURE,
        REJECTED_AS_FALSE_POSITIVE, SUPERSEDED
    }
}
