package com.inigmasgames.persistentnpcs.cognition;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded runtime diagnostics. It deliberately does not persist hidden model reasoning. */
public final class CognitionTraceStore {
    public record Trace(Instant at, CognitionContext context, GroundedNpcDecision decision,
            int promptCharacters, int estimatedPromptTokens, String rawModelOutput,
            String groundingSafetyDecision, String canonicalResponse,
            NpcDecisionDiagnostics structuredDecision) {
        public Trace {
            rawModelOutput = rawModelOutput == null ? "" : rawModelOutput;
            groundingSafetyDecision = groundingSafetyDecision == null
                    ? "" : groundingSafetyDecision;
            canonicalResponse = canonicalResponse == null ? "" : canonicalResponse;
        }
    }

    private final Map<UUID, Trace> latest = new ConcurrentHashMap<>();

    public void record(UUID npcId, CognitionContext context, GroundedNpcDecision decision) {
        if (npcId != null && context != null && decision != null) {
            Trace prior = latest.get(npcId);
            boolean same = prior != null && prior.context() != null
                    && context.responseId().equals(prior.context().responseId());
            latest.put(npcId, new Trace(Instant.now(), context, decision,
                    same ? prior.promptCharacters() : 0,
                    same ? prior.estimatedPromptTokens() : 0,
                    same ? prior.rawModelOutput() : "",
                    same ? prior.groundingSafetyDecision() : "",
                    same ? prior.canonicalResponse() : "",
                    same ? prior.structuredDecision() : null));
        }
    }

    public void recordPrompt(UUID npcId, UUID responseId, int characters) {
        update(npcId, responseId, prior -> new Trace(Instant.now(), prior.context(),
                prior.decision(), Math.max(0, characters), Math.max(0, (characters + 3) / 4),
                prior.rawModelOutput(), prior.groundingSafetyDecision(),
                prior.canonicalResponse(), prior.structuredDecision()));
    }

    public void recordModelOutput(UUID npcId, UUID responseId, String rawModelOutput) {
        update(npcId, responseId, prior -> new Trace(Instant.now(), prior.context(),
                prior.decision(), prior.promptCharacters(), prior.estimatedPromptTokens(),
                rawModelOutput, prior.groundingSafetyDecision(), prior.canonicalResponse(),
                prior.structuredDecision()));
    }

    public void recordCanonical(UUID npcId, UUID responseId, String decision,
            String canonicalResponse) {
        update(npcId, responseId, prior -> new Trace(Instant.now(), prior.context(),
                prior.decision(), prior.promptCharacters(), prior.estimatedPromptTokens(),
                prior.rawModelOutput(), decision, canonicalResponse,
                prior.structuredDecision()));
    }

    public void recordStructuredDecision(UUID npcId, UUID responseId,
            NpcDecisionDiagnostics diagnostics) {
        update(npcId, responseId, prior -> new Trace(Instant.now(), prior.context(),
                prior.decision(), prior.promptCharacters(), prior.estimatedPromptTokens(),
                prior.rawModelOutput(), prior.groundingSafetyDecision(),
                prior.canonicalResponse(), diagnostics));
    }

    private void update(UUID npcId, UUID responseId,
            java.util.function.Function<Trace, Trace> updater) {
        if (npcId == null || responseId == null) return;
        latest.computeIfPresent(npcId, (ignored, prior) -> prior.context() != null
                && responseId.equals(prior.context().responseId()) ? updater.apply(prior) : prior);
    }

    public Optional<Trace> latest(UUID npcId) {
        return Optional.ofNullable(latest.get(npcId));
    }
}
