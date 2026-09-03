package com.inigmasgames.persistentnpcs.sentinel;

import static com.inigmasgames.persistentnpcs.sentinel.SentinelContracts.*;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Project-owned S1 invariant catalog. It is never populated from NPC/player content. */
public final class InvariantRegistry {
    public static final String VERSION = "S1.1";
    private static final Duration DEADLINE = Duration.ofMillis(1);
    private final Map<String, InvariantDefinition> definitions;

    public InvariantRegistry() {
        var values = List.of(
            d("TURN-001", "One accepted turn per physical utterance", Category.INPUT_INTEGRITY, Boundary.TRANSCRIPT_ACCEPT, Scope.TURN, Severity.CRITICAL, "turnIngress", "OrbisTurnCoordinator", "RECOVER_REJECT_DUPLICATE"),
            d("TURN-002", "Exactly one terminal transition", Category.OWNERSHIP_VIOLATION, Boundary.TERMINAL_CLEANUP, Scope.TURN, Severity.CRITICAL, "terminalCount", "RecoverySupervisor", "RECOVER_IDEMPOTENT_CLEANUP"),
            d("TURN-003", "Resource and provider cleanup occurs exactly once", Category.OWNERSHIP_VIOLATION, Boundary.TERMINAL_CLEANUP, Scope.TURN, Severity.CRITICAL, "cleanupCount", "RecoverySupervisor", "RECOVER_VERIFY_CLEANUP"),
            d("PLAN-001", "Provider dispatch has a valid TurnExecutionPlan", Category.CONTRACT_DRIFT, Boundary.PROVIDER_DISPATCH, Scope.TURN, Severity.CRITICAL, "planPresent", "TurnPlanCompiler", "RECOVER_RECOMPILE_PLAN"),
            d("PLAN-002", "Budgeted and dispatched prompt identities match", Category.CONTRACT_DRIFT, Boundary.PROVIDER_DISPATCH, Scope.TURN, Severity.CRITICAL, "promptIdentity", "ContractBudgetPlanner", "RECOVER_RERENDER_BUDGET"),
            d("PLAN-003", "Supported epistemic route carries authoritative contract", Category.EPISTEMIC_AUTHORITY, Boundary.PROVIDER_DISPATCH, Scope.ROUTE, Severity.CRITICAL, "epistemicRoute", "TurnPlanCompiler/EpistemicProductionRoute", "RECOVER_AUTHORITATIVE_ROUTE"),
            d("PLAN-004", "Actual prompt/schema/output fits compiled budget", Category.CONTRACT_DRIFT, Boundary.PROVIDER_DISPATCH, Scope.TURN, Severity.CRITICAL, "dispatchBudget", "ContractBudgetPlanner", "RECOVER_PRUNE_RECOMPILE"),
            d("PROV-001", "Provider event belongs to current request and epoch", Category.OWNERSHIP_VIOLATION, Boundary.PROVIDER_STREAM_EVENT, Scope.BRANCH, Severity.CRITICAL, "providerOwnership", "OrbisTurnCoordinator", "RECOVER_DISCARD_STALE"),
            d("PROV-002", "Provider READY/drained state has no active owner", Category.PROVIDER_LIFECYCLE, Boundary.PROVIDER_TERMINAL, Scope.PROVIDER, Severity.CRITICAL, "providerDrain", "ProviderLifecycleOwner", "RECOVER_PROVIDER_DRAIN"),
            d("PROV-003", "Provider deltas are monotonic and non-duplicate", Category.PROVIDER_LIFECYCLE, Boundary.PROVIDER_STREAM_EVENT, Scope.PROVIDER, Severity.CRITICAL, "providerSequence", "ProviderAdapter", "RECOVER_PROVIDER_STREAM"),
            d("RES-001", "READY represents a sustainable foreground envelope", Category.RESOURCE_ENVELOPE, Boundary.READINESS_SAMPLE, Scope.RESOURCE_PROFILE, Severity.DEGRADED, "resourceEnvelope", "OrbisResourceScheduler", "RECOVER_RESOURCE_PROFILE"),
            d("RES-002", "Repeated starvation uses one stable failure class", Category.RESOURCE_ENVELOPE, Boundary.READINESS_SAMPLE, Scope.RESOURCE_PROFILE, Severity.DEGRADED, "starvationSignature", "OrbisResourceScheduler", "RECOVER_STARVATION_CIRCUIT"),
            d("RES-003", "Residency does not ping-pong in one pressure epoch", Category.RESOURCE_ENVELOPE, Boundary.READINESS_SAMPLE, Scope.RESOURCE_PROFILE, Severity.DEGRADED, "residencyStability", "OrbisResourceScheduler", "RECOVER_STABLE_PROFILE"),
            d("EPI-001", "Objective claim has compatible claim verdict", Category.EPISTEMIC_AUTHORITY, Boundary.CLAIM_VALIDATION, Scope.ROUTE, Severity.CRITICAL, "claimCoverage", "DialogueClaimValidator/EpistemicClaimFirewall", "RECOVER_DROP_OR_REPAIR_CLAUSE"),
            d("EPI-002", "Property assertion has property-level support", Category.EPISTEMIC_AUTHORITY, Boundary.CLAIM_VALIDATION, Scope.ROUTE, Severity.CRITICAL, "propertySupport", "EpistemicClaimFirewall", "RECOVER_DROP_PROPERTY"),
            d("EPI-003", "Unknown/conflicted answerability is not certain", Category.EPISTEMIC_AUTHORITY, Boundary.CLAIM_VALIDATION, Scope.ROUTE, Severity.CRITICAL, "answerabilityCertainty", "EpistemicClaimFirewall", "RECOVER_SAFE_ANSWER_PLAN"),
            d("EPI-004", "Generated speech is not promoted as factual evidence", Category.DURABLE_STATE_INTEGRITY, Boundary.BELIEF_WRITE_PROPOSED, Scope.PERSISTENCE_STREAM, Severity.CRITICAL, "speechNotEvidence", "ExistingBeliefWriter(E4-scaffold)", "RECOVER_REJECT_WRITE"),
            d("SPEECH-001", "Objective ledger segment has claim evidence", Category.SPEECH_DELIVERY_INTEGRITY, Boundary.SPEECH_LEDGER_APPEND, Scope.TURN, Severity.CRITICAL, "ledgerClaimCoverage", "CanonicalSpeechLedger/EpistemicClaimFirewall", "RECOVER_REJECT_APPEND"),
            d("SPEECH-002", "Canonical spans are ordered contiguous and non-overlapping", Category.SPEECH_DELIVERY_INTEGRITY, Boundary.SPEECH_LEDGER_APPEND, Scope.TURN, Severity.CRITICAL, "speechSpanOrder", "CanonicalSpeechLedger", "RECOVER_PRESERVE_PREFIX"),
            d("SPEECH-003", "Only playback-confirmed text enters delivered history", Category.SPEECH_DELIVERY_INTEGRITY, Boundary.TERMINAL_CLEANUP, Scope.TURN, Severity.CRITICAL, "deliveryTruth", "CanonicalSpeechLedger/PlaybackCoordinator", "RECOVER_CORRECT_DELIVERY"),
            d("ACT-001", "Action claim has authoritative action result", Category.ACTION_TRUTH, Boundary.ACTION_COMMIT, Scope.TURN, Severity.CRITICAL, "actionAuthority", "AgentOperation/HytaleActionValidator", "RECOVER_BLOCK_ACTION_CLAIM"),
            d("PERSIST-001", "Factual durable proposal has provenance and not speech-only evidence", Category.DURABLE_STATE_INTEGRITY, Boundary.BELIEF_WRITE_PROPOSED, Scope.PERSISTENCE_STREAM, Severity.CRITICAL, "persistenceProvenance", "SourcedBeliefStore/BeliefEvent", "RECOVER_REJECT_WRITE"),
            d("PERSIST-002", "Belief revision is the exact next revision", Category.DURABLE_STATE_INTEGRITY, Boundary.BELIEF_WRITE_PROPOSED, Scope.PERSISTENCE_STREAM, Severity.CRITICAL, "beliefRevision", "SourcedBeliefStore/BeliefEvent", "RECOVER_REJECT_WRITE"),
            d("PERSIST-003", "Duplicate belief event IDs are identical", Category.DURABLE_STATE_INTEGRITY, Boundary.BELIEF_WRITE_PROPOSED, Scope.PERSISTENCE_STREAM, Severity.CRITICAL, "beliefEventIdentity", "SourcedBeliefStore/BeliefEvent", "RECOVER_REJECT_WRITE"),
            d("PERSIST-004", "Action occurrence belief has authoritative action result", Category.DURABLE_STATE_INTEGRITY, Boundary.BELIEF_WRITE_PROPOSED, Scope.PERSISTENCE_STREAM, Severity.CRITICAL, "beliefActionAuthority", "SourcedBeliefStore/ActionResult", "RECOVER_REJECT_WRITE"),
            d("PERSIST-005", "Belief persistence proposal is structurally valid", Category.DURABLE_STATE_INTEGRITY, Boundary.BELIEF_WRITE_PROPOSED, Scope.PERSISTENCE_STREAM, Severity.CRITICAL, "beliefProposal", "SourcedBeliefStore/BeliefEvent", "RECOVER_REJECT_WRITE"));
        var map = new LinkedHashMap<String, InvariantDefinition>();
        for (var value : values) {
            if (map.put(value.id(), value) != null) throw new IllegalStateException(
                    "duplicate invariant " + value.id());
        }
        definitions = Map.copyOf(map);
    }

    private static InvariantDefinition d(String id, String description, Category category,
            Boundary boundary, Scope scope, Severity severity, String evaluator,
            String owner, String recovery) {
        return new InvariantDefinition(id, 1, description, category, boundary, scope,
                severity, Confidence.PROVEN, evaluator, owner, recovery, DEADLINE, true, true);
    }

    public String version() { return VERSION; }
    public List<InvariantDefinition> all() { return List.copyOf(definitions.values()); }
    public List<InvariantDefinition> at(Boundary boundary) {
        return definitions.values().stream().filter(value -> value.boundary() == boundary).toList();
    }
    public InvariantDefinition require(String id) {
        var value = definitions.get(id);
        if (value == null) throw new IllegalArgumentException("unknown invariant " + id);
        return value;
    }
}
