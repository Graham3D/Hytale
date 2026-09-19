package com.inigmasgames.persistentnpcs.evaluation;

import com.inigmasgames.persistentnpcs.epistemic.Answerability;
import com.inigmasgames.persistentnpcs.orbis.OrbisEventType;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Schema-versioned semantic contracts for the development-only Orbis harness. */
public final class EvaluationContracts {
    public static final int SCHEMA_VERSION = 1;

    private EvaluationContracts() { }

    public enum EvaluationMode {
        OFF, STATIC_REPLAY, LIVE_HEADLESS, LIVE_FULL_PIPELINE,
        CONNECTED_HYTALE, MULTI_AGENT_HEADLESS, TRACE_IMPORT
    }

    public enum CampaignIntent { DISCOVER, VERIFY_FIX, FREEZE, SOAK, COMPARE }
    public enum EvaluationVerdict { PASS, FAIL, NEEDS_REVIEW, NOT_APPLICABLE, BLOCKED }
    public enum FailureClass {
        INGRESS, DIALOGUE_STATE, ROUTE, RETRIEVAL, ANSWERABILITY, ANSWER_PLAN,
        TURN_PLAN, CONTEXT_RENDER, PROVIDER_REALIZATION, CLAIM_AUTHORITY,
        CANONICAL_DELIVERY, ACTION_TRUTH, STATE_LEARNING, PERSISTENCE,
        CLEANUP, LIFECYCLE, RESOURCE
    }

    public enum BoundaryId {
        INGRESS, DIALOGUE_STATE, QUERY_PLAN, RETRIEVAL, ANSWERABILITY, ANSWER_PLAN,
        TURN_PLAN, CONTEXT_RENDER, PROVIDER, CLAIM_FIREWALL, CANONICAL_RESPONSE,
        STATE_DELTA, CLEANUP
    }

    public enum IngressKind { AUTHORITATIVE_EVALUATION_TEXT, RECORDED_VOICE }
    public enum PacingPolicy { WAIT_FOR_TERMINAL, BARGE_IN_AT_STAGE, SIMULATED_DELAY,
        CONCURRENT_SCENE }
    public enum ResetPolicy { RESET_EACH_SCENARIO, INHERIT_PRIOR, RESTORE_CHECKPOINT }

    public record ScenarioActor(UUID stableId, String name, Path profileSource) {
        public ScenarioActor {
            if (stableId == null || name == null || name.isBlank()) {
                throw new IllegalArgumentException("stable actor identity required");
            }
        }
    }

    public record ScenarioWorldState(String worldName, int x, int y, int z,
            Map<String, String> semanticFacts, Set<String> visibleEntities,
            Map<UUID, Set<String>> inventory) {
        public ScenarioWorldState {
            worldName = worldName == null || worldName.isBlank() ? "evaluation" : worldName;
            semanticFacts = Map.copyOf(semanticFacts == null ? Map.of() : semanticFacts);
            visibleEntities = Set.copyOf(visibleEntities == null ? Set.of() : visibleEntities);
            inventory = Map.copyOf(inventory == null ? Map.of() : inventory);
        }
    }

    public record ScenarioCognitiveState(List<String> authoredFacts,
            List<String> memories, List<String> beliefs, List<String> relationships,
            List<String> commitments, List<String> secrets) {
        public ScenarioCognitiveState {
            authoredFacts = List.copyOf(authoredFacts == null ? List.of() : authoredFacts);
            memories = List.copyOf(memories == null ? List.of() : memories);
            beliefs = List.copyOf(beliefs == null ? List.of() : beliefs);
            relationships = List.copyOf(relationships == null ? List.of() : relationships);
            commitments = List.copyOf(commitments == null ? List.of() : commitments);
            secrets = List.copyOf(secrets == null ? List.of() : secrets);
        }
    }

    public record ExpectedProposition(String subject, String predicate, String value,
            String claimMode, String temporalScope, Set<String> evidenceSources) {
        public ExpectedProposition {
            subject = clean(subject); predicate = clean(predicate); value = clean(value);
            claimMode = clean(claimMode); temporalScope = clean(temporalScope);
            evidenceSources = Set.copyOf(evidenceSources == null ? Set.of() : evidenceSources);
        }
    }

    public record ExpectedStateDelta(Set<String> memoryContains,
            Set<String> beliefContains, Set<String> relationshipContains,
            Set<String> forbiddenWrites) {
        public ExpectedStateDelta {
            memoryContains = Set.copyOf(memoryContains == null ? Set.of() : memoryContains);
            beliefContains = Set.copyOf(beliefContains == null ? Set.of() : beliefContains);
            relationshipContains = Set.copyOf(relationshipContains == null
                    ? Set.of() : relationshipContains);
            forbiddenWrites = Set.copyOf(forbiddenWrites == null ? Set.of() : forbiddenWrites);
        }
        public static ExpectedStateDelta none() {
            return new ExpectedStateDelta(Set.of(), Set.of(), Set.of(), Set.of());
        }
    }

    public record ExpectedTurnContract(String dialogueAct, String queryKind,
            Set<String> requiredEvidence, Set<String> allowedSources,
            Answerability expectedAnswerability,
            List<ExpectedProposition> requiredPropositions,
            Set<String> forbiddenClaims, String expectedAction,
            ExpectedStateDelta stateDelta, Set<String> requiredContextSections,
            Set<String> forbiddenContextSections, long maximumLatencyMillis) {
        public ExpectedTurnContract {
            requiredEvidence = Set.copyOf(requiredEvidence == null ? Set.of() : requiredEvidence);
            allowedSources = Set.copyOf(allowedSources == null ? Set.of() : allowedSources);
            requiredPropositions = List.copyOf(requiredPropositions == null
                    ? List.of() : requiredPropositions);
            forbiddenClaims = Set.copyOf(forbiddenClaims == null ? Set.of() : forbiddenClaims);
            stateDelta = stateDelta == null ? ExpectedStateDelta.none() : stateDelta;
            requiredContextSections = Set.copyOf(requiredContextSections == null
                    ? Set.of() : requiredContextSections);
            forbiddenContextSections = Set.copyOf(forbiddenContextSections == null
                    ? Set.of() : forbiddenContextSections);
            maximumLatencyMillis = maximumLatencyMillis <= 0 ? 15_000 : maximumLatencyMillis;
        }
        public static ExpectedTurnContract openSocial() {
            return new ExpectedTurnContract("", "GENERAL_SOCIAL", Set.of(), Set.of(),
                    Answerability.SUBJECTIVE, List.of(), Set.of("UNSUPPORTED_OBJECTIVE_FACT"),
                    "", ExpectedStateDelta.none(), Set.of(), Set.of(), 15_000);
        }
    }

    public record ScenarioTurn(int index, UUID speaker, List<UUID> audience,
            String utterance, IngressKind ingress, PacingPolicy pacing,
            ExpectedTurnContract expected, Map<String, String> externalResult) {
        public ScenarioTurn {
            if (index < 0 || speaker == null || utterance == null || utterance.isBlank()) {
                throw new IllegalArgumentException("valid scenario turn required");
            }
            audience = List.copyOf(audience == null ? List.of() : audience);
            ingress = ingress == null ? IngressKind.AUTHORITATIVE_EVALUATION_TEXT : ingress;
            pacing = pacing == null ? PacingPolicy.WAIT_FOR_TERMINAL : pacing;
            expected = expected == null ? ExpectedTurnContract.openSocial() : expected;
            externalResult = Map.copyOf(externalResult == null ? Map.of() : externalResult);
        }
    }

    public record ConversationScenario(String id, String description,
            List<ScenarioActor> actors, ScenarioWorldState world,
            ScenarioCognitiveState cognition, List<ScenarioTurn> turns,
            Set<String> coverageTags, ResetPolicy resetPolicy) {
        public ConversationScenario {
            if (id == null || !id.matches("[A-Za-z0-9_.-]{1,96}")) {
                throw new IllegalArgumentException("safe scenario id required");
            }
            description = description == null ? "" : description.strip();
            actors = List.copyOf(actors == null ? List.of() : actors);
            turns = List.copyOf(turns == null ? List.of() : turns);
            if (actors.isEmpty() || turns.isEmpty()) {
                throw new IllegalArgumentException("scenario requires actors and turns");
            }
            world = world == null ? new ScenarioWorldState("evaluation", 0, 64, 0,
                    Map.of(), Set.of(), Map.of()) : world;
            cognition = cognition == null ? new ScenarioCognitiveState(List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of()) : cognition;
            coverageTags = Set.copyOf(coverageTags == null ? Set.of() : coverageTags);
            resetPolicy = resetPolicy == null ? ResetPolicy.RESET_EACH_SCENARIO : resetPolicy;
        }
    }

    public record ResourceBudget(int maximumProviderCalls, int maximumTurns,
            Duration maximumWallTime, long maximumArtifactBytes) {
        public ResourceBudget {
            maximumProviderCalls = maximumProviderCalls <= 0 ? 50 : maximumProviderCalls;
            maximumTurns = maximumTurns <= 0 ? 100 : maximumTurns;
            maximumWallTime = maximumWallTime == null ? Duration.ofMinutes(20)
                    : maximumWallTime;
            maximumArtifactBytes = maximumArtifactBytes <= 0 ? 32L * 1024 * 1024
                    : maximumArtifactBytes;
        }
    }

    public record EvaluationCampaign(String id, CampaignIntent intent,
            EvaluationMode mode, List<ConversationScenario> scenarios,
            int repetitions, List<Long> seeds, double temperature,
            ResourceBudget resources) {
        public EvaluationCampaign {
            if (id == null || !id.matches("[A-Za-z0-9_.-]{1,96}")) {
                throw new IllegalArgumentException("safe campaign id required");
            }
            intent = intent == null ? CampaignIntent.DISCOVER : intent;
            mode = mode == null ? EvaluationMode.STATIC_REPLAY : mode;
            if (mode == EvaluationMode.OFF) throw new IllegalArgumentException(
                    "OFF cannot execute a campaign");
            scenarios = List.copyOf(scenarios == null ? List.of() : scenarios);
            if (scenarios.isEmpty()) throw new IllegalArgumentException(
                    "campaign requires scenarios");
            repetitions = Math.max(1, repetitions);
            seeds = List.copyOf(seeds == null ? List.of() : seeds);
            resources = resources == null ? new ResourceBudget(50, 100,
                    Duration.ofMinutes(20), 32L * 1024 * 1024) : resources;
        }
    }

    public record StageObservation(BoundaryId boundary, long sequence, Instant at,
            UUID turnId, UUID responseId, OrbisEventType eventType,
            Map<String, String> facts) {
        public StageObservation {
            if (boundary == null || at == null || eventType == null) {
                throw new IllegalArgumentException("typed observation required");
            }
            facts = Map.copyOf(facts == null ? Map.of() : facts);
        }
    }

    public record StageVerdict(BoundaryId boundary, EvaluationVerdict verdict,
            String invariantId, String expected, String actual) { }

    public record RootCauseDiagnosis(BoundaryId earliestFailedBoundary,
            FailureClass failureClass, String invariantId, String authoritativeOwner,
            String expected, String actual, List<Long> supportingSequences,
            List<String> downstreamSymptoms, double confidence) {
        public RootCauseDiagnosis {
            supportingSequences = List.copyOf(supportingSequences == null
                    ? List.of() : supportingSequences);
            downstreamSymptoms = List.copyOf(downstreamSymptoms == null
                    ? List.of() : downstreamSymptoms);
            confidence = Math.max(0.0, Math.min(1.0, confidence));
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
