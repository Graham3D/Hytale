package com.inigmasgames.persistentnpcs.epistemic;

import com.inigmasgames.persistentnpcs.cognition.SourcedBeliefStore;
import com.inigmasgames.persistentnpcs.orbis.OrbisResourceScheduler;
import com.inigmasgames.persistentnpcs.orbis.ResourceWorkload;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** E7 bounded background derivation over the authoritative E4 materialized view. */
public final class ReflectionService implements AutoCloseable {
    private static final int MAX_QUEUE = 24;
    private static final Duration NPC_COOLDOWN = Duration.ofSeconds(10);
    private static final Duration GLOBAL_COOLDOWN = Duration.ofMillis(250);
    private final SourcedBeliefStore beliefs;
    private volatile Consumer<String> diagnostics;
    private final ThreadPoolExecutor background = new ThreadPoolExecutor(1, 1, 30,
            TimeUnit.SECONDS, new ArrayBlockingQueue<>(MAX_QUEUE), task -> Thread.ofPlatform()
                    .daemon(true).name("orbis-e7-reflection").unstarted(task),
            new ThreadPoolExecutor.AbortPolicy());
    private final Map<UUID, Instant> lastByNpc = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile Instant lastGlobal = Instant.EPOCH;
    private volatile OrbisResourceScheduler scheduler;
    private volatile ReflectionStatus latest = ReflectionStatus.empty();

    public ReflectionService(SourcedBeliefStore beliefs, Consumer<String> diagnostics) {
        this.beliefs = beliefs;
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
    }

    public void scheduler(OrbisResourceScheduler value) { scheduler = value; }
    public void diagnostics(Consumer<String> value) {
        diagnostics = value == null ? ignored -> { } : value;
    }
    public ReflectionStatus latest() { return latest; }

    public CompletableFuture<ReflectionResult> submit(ReflectionProposal proposal) {
        if (proposal == null || beliefs == null) return CompletableFuture.completedFuture(
                ReflectionResult.rejected(null, "SERVICE_UNAVAILABLE"));
        CompletableFuture<ReflectionResult> future = new CompletableFuture<>();
        try {
            background.execute(() -> {
                long started = System.nanoTime();
                if (mustYield()) { complete(future, proposal, null, "BACKGROUND_PREEMPTED",
                        started); return; }
                Instant now = Instant.now();
                Instant npcLast = lastByNpc.getOrDefault(proposal.npcStableId(), Instant.EPOCH);
                if (now.isBefore(npcLast.plus(NPC_COOLDOWN))
                        || now.isBefore(lastGlobal.plus(GLOBAL_COOLDOWN))) {
                    complete(future, proposal, null, "REFLECTION_COOLDOWN", started); return;
                }
                Validation validation = validate(proposal);
                diagnostics.accept("REFLECTION_PROPOSED id=" + proposal.reflectionId()
                        + " npc=" + proposal.npcStableId() + " kind=" + proposal.kind()
                        + " supports=" + proposal.supportIds());
                if (!validation.accepted()) {
                    complete(future, proposal, null, validation.reason(), started); return;
                }
                if (mustYield()) { complete(future, proposal, null, "BACKGROUND_PREEMPTED",
                        started); return; }
                BeliefProvenance provenance = new BeliefProvenance(
                        EvidenceSourceKind.DERIVED_REFLECTION, proposal.npcStableId(),
                        List.of("REFLECTION_ID:" + proposal.reflectionId(),
                                "REFLECTION_KIND:" + proposal.kind()), false, false);
                DerivedProposition p = proposal.proposition();
                BeliefAssertion assertion = beliefs.assertBelief(new BeliefProposal(null,
                        proposal.npcStableId(), p.subjectId(), p.subject(), p.predicate(),
                        p.value(), p.statement(), BeliefAssertion.Polarity.POSITIVE,
                        EpistemicStatus.BELIEVED, validation.confidence(), provenance,
                        proposal.temporalScope(), p.scope(), proposal.supportIds(), now));
                lastByNpc.put(proposal.npcStableId(), now); lastGlobal = now;
                complete(future, proposal, assertion, "COMMITTED", started);
            });
        } catch (java.util.concurrent.RejectedExecutionException full) {
            complete(future, proposal, null, "BACKGROUND_QUEUE_FULL", System.nanoTime());
        }
        return future;
    }

    /** Deterministic repeated-outcome trigger; one transient failure cannot reach this path. */
    public Optional<ReflectionProposal> repeatedFailureProposal(UUID npcId, String actionId,
            Instant now) {
        String action = actionId == null ? "" : actionId.toUpperCase(java.util.Locale.ROOT);
        List<BeliefAssertion> outcomes = beliefs.current(npcId, null,
                        "PROCEDURAL_OUTCOME").stream()
                .filter(value -> value.provenance().sourceKind()
                        == EvidenceSourceKind.PROCEDURAL_OUTCOME)
                .filter(value -> value.value().contains(":" + action + ":"))
                .sorted(Comparator.comparing(BeliefAssertion::learnedAt).reversed()).limit(8)
                .toList();
        Instant latestSuccess = outcomes.stream().filter(value -> value.value()
                        .startsWith("SUCCESS:" + action + ":"))
                .map(BeliefAssertion::learnedAt).max(Comparator.naturalOrder())
                .orElse(Instant.EPOCH);
        List<BeliefAssertion> failures = outcomes.stream()
                .filter(value -> value.value().startsWith("FAILURE:" + action + ":"))
                .filter(value -> value.learnedAt().isAfter(latestSuccess)).limit(5).toList();
        if (failures.size() < 3) return Optional.empty();
        UUID subject = UUID.nameUUIDFromBytes((npcId + "|procedure|" + action)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Optional.of(new ReflectionProposal(UUID.randomUUID(), npcId,
                new DerivedProposition(subject, "procedure " + action,
                        "PROCEDURAL_RELIABILITY", "UNRELIABLE_RECENTLY:" + action,
                        action + " has been unreliable recently.", true,
                        BeliefAssertion.AssertionScope.SELF),
                failures.stream().map(BeliefAssertion::assertionId).toList(), .8,
                ReflectionKind.REPEATED_ACTION_FAILURE,
                new BeliefAssertion.TemporalScope(BeliefPredicateRegistry.Stability.VOLATILE,
                        now, now.plus(Duration.ofHours(6)), "RECENT_OUTCOMES"),
                Optional.empty()));
    }

    public CompletableFuture<ReflectionResult> onActionResult(UUID npcId, String actionId,
            Instant now) {
        return repeatedFailureProposal(npcId, actionId, now).map(this::submit)
                .orElseGet(() -> CompletableFuture.completedFuture(
                        ReflectionResult.rejected(null, "INSUFFICIENT_REPEATED_OUTCOMES")));
    }

    private Validation validate(ReflectionProposal proposal) {
        if (proposal.reflectionId() == null || proposal.npcStableId() == null
                || proposal.proposition() == null || proposal.supportIds().isEmpty()) {
            return Validation.reject("MISSING_SUPPORT_OR_ID");
        }
        if (proposal.skillProposal().isPresent()) return Validation.reject("E8_SKILL_DISABLED");
        LinkedHashSet<UUID> unique = new LinkedHashSet<>(proposal.supportIds());
        if (unique.size() != proposal.supportIds().size()) return Validation.reject(
                "DUPLICATE_SUPPORT");
        ArrayList<BeliefAssertion> supports = new ArrayList<>();
        for (UUID id : unique) {
            BeliefAssertion value = beliefs.assertion(id).orElse(null);
            if (value == null || !value.ownerNpcId().equals(proposal.npcStableId()))
                return Validation.reject("NONEXISTENT_SUPPORT");
            if (value.provenance().generatedSpeechOnly()) return Validation.reject(
                    "SPEECH_ONLY_SUPPORT");
            if (!value.activeAt(Instant.now()) || value.status() == EpistemicStatus.DISPUTED
                    || !value.conflictIds().isEmpty()) return Validation.reject(
                    "CONTRADICTED_SUPPORT");
            supports.add(value);
        }
        DerivedProposition p = proposal.proposition();
        boolean entityPresent = p.subjectId() == null || supports.stream().anyMatch(value ->
                p.subjectId().equals(value.subjectId())
                        || value.provenance().sourceKind() == EvidenceSourceKind.AUTHORED_CANON);
        boolean procedural = proposal.kind() == ReflectionKind.REPEATED_ACTION_FAILURE
                && supports.size() >= 3 && supports.stream().allMatch(value ->
                        value.provenance().sourceKind() == EvidenceSourceKind.PROCEDURAL_OUTCOME
                                && value.value().startsWith("FAILURE:"));
        if (!entityPresent && !procedural) return Validation.reject("UNSUPPORTED_ENTITY");
        boolean relationSupported = p.inference()
                || supports.stream().anyMatch(value -> value.predicate().equals(p.predicate()));
        if (!relationSupported) return Validation.reject("UNSUPPORTED_RELATION");
        if (proposal.kind() == ReflectionKind.REPEATED_ACTION_FAILURE && !procedural)
            return Validation.reject("INSUFFICIENT_OUTCOMES");
        double strongest = supports.stream().mapToDouble(BeliefAssertion::confidence)
                .max().orElse(0);
        double confidence = Math.min(proposal.confidence(), strongest * .85);
        return new Validation(true, "ACCEPTED", confidence);
    }

    private boolean mustYield() {
        OrbisResourceScheduler value = scheduler;
        if (value == null) return false;
        var snapshot = value.snapshot();
        for (ResourceWorkload workload : List.of(ResourceWorkload.STT, ResourceWorkload.LLM,
                ResourceWorkload.TTS, ResourceWorkload.DIRECT_VOICE)) {
            if (snapshot.activeByWorkload().getOrDefault(workload, 0) > 0
                    || snapshot.queuedByWorkload().getOrDefault(workload, 0) > 0) return true;
        }
        return Set.of(OrbisResourceScheduler.OperatingState.PRESSURE,
                OrbisResourceScheduler.OperatingState.RECOVERING,
                OrbisResourceScheduler.OperatingState.ERROR).contains(value.operatingEnvelope().state());
    }

    private void complete(CompletableFuture<ReflectionResult> future,
            ReflectionProposal proposal, BeliefAssertion assertion, String reason, long started) {
        ReflectionResult result = assertion == null ? ReflectionResult.rejected(
                proposal == null ? null : proposal.reflectionId(), reason)
                : new ReflectionResult(proposal.reflectionId(), true, reason, assertion);
        latest = new ReflectionStatus(proposal == null ? null : proposal.reflectionId(),
                proposal == null ? null : proposal.kind(), reason,
                assertion == null ? null : assertion.assertionId(),
                proposal == null ? List.of() : proposal.supportIds(),
                (System.nanoTime() - started) / 1_000, "BACKGROUND_LOW");
        diagnostics.accept((assertion == null ? "REFLECTION_REJECTED" : "REFLECTION_COMMITTED")
                + " id=" + latest.reflectionId() + " kind=" + latest.kind()
                + " npc=" + (proposal == null ? null : proposal.npcStableId())
                + " supports=" + latest.supportIds() + " result=" + reason
                + " derivedAssertionId=" + latest.derivedAssertionId()
                + " timingMicros=" + latest.timingMicros());
        future.complete(result);
    }

    @Override public void close() { background.shutdownNow(); }

    public enum ReflectionKind { IMPORTANCE_CONSOLIDATION, REPEATED_RELATED_EVENTS,
        REPEATED_ACTION_FAILURE, REPEATED_ACTION_SUCCESS, RELATIONSHIP_MILESTONE,
        CONTRADICTION_SYNTHESIS, SCHEDULED_CONSOLIDATION }
    public record DerivedProposition(UUID subjectId, String subject, String predicate,
            String value, String statement, boolean inference,
            BeliefAssertion.AssertionScope scope) { }
    public record ReflectionProposal(UUID reflectionId, UUID npcStableId,
            DerivedProposition proposition, List<UUID> supportIds, double confidence,
            ReflectionKind kind, BeliefAssertion.TemporalScope temporalScope,
            Optional<String> skillProposal) {
        public ReflectionProposal { supportIds = List.copyOf(supportIds == null ? List.of()
                : supportIds); skillProposal = skillProposal == null ? Optional.empty()
                : skillProposal; }
    }
    public record ReflectionResult(UUID reflectionId, boolean committed, String reason,
            BeliefAssertion assertion) {
        static ReflectionResult rejected(UUID id, String reason) {
            return new ReflectionResult(id, false, reason, null);
        }
    }
    public record ReflectionStatus(UUID reflectionId, ReflectionKind kind, String result,
            UUID derivedAssertionId, List<UUID> supportIds, long timingMicros,
            String schedulingPriority) {
        static ReflectionStatus empty() { return new ReflectionStatus(null, null, "NONE", null,
                List.of(), 0, "BACKGROUND_LOW"); }
    }
    private record Validation(boolean accepted, String reason, double confidence) {
        static Validation reject(String reason) { return new Validation(false, reason, 0); }
    }
}
