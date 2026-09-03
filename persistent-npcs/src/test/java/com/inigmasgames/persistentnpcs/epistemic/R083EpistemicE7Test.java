package com.inigmasgames.persistentnpcs.epistemic;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.action.NpcActionRequest;
import com.inigmasgames.persistentnpcs.action.NpcActionResult;
import com.inigmasgames.persistentnpcs.ai.*;
import com.inigmasgames.persistentnpcs.cognition.SourcedBeliefStore;
import com.inigmasgames.persistentnpcs.diagnostics.RuntimeResourceMonitor;
import com.inigmasgames.persistentnpcs.orbis.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** E7 support validation, outcome learning, invalidation, persistence, and yield gate. */
public final class R083EpistemicE7Test {
    private R083EpistemicE7Test() { }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("r083-e7-");
        UUID persistentNpc = UUID.randomUUID();
        UUID derivedId;
        try (SourcedBeliefStore store = store(root)) {
            validUnsupportedConfidenceAndContradiction(store);
            derivedId = invalidationAndRestartSeed(store, persistentNpc);
            actionOutcomeLearning(store);
            speechContamination(store);
            schedulingYield(store);
        }
        try (SourcedBeliefStore restarted = store(root)) {
            BeliefAssertion restored = restarted.assertion(derivedId).orElseThrow();
            assert restored.provenance().sourceKind() == EvidenceSourceKind.DERIVED_REFLECTION;
            assert !restored.supportIds().isEmpty();
            assert restored.status() == EpistemicStatus.DISPUTED;
        }
        System.out.println("R083 E7 reflection/outcome validation passed.");
    }

    private static void validUnsupportedConfidenceAndContradiction(SourcedBeliefStore store)
            throws Exception {
        UUID npc = UUID.randomUUID(), subject = UUID.randomUUID();
        BeliefAssertion a = observed(store, npc, subject, "VISIT_RESULT_ONE", "positive", .8);
        BeliefAssertion b = observed(store, npc, subject, "VISIT_RESULT_TWO", "positive", .7);
        try (ReflectionService service = new ReflectionService(store, ignored -> { })) {
            var result = service.submit(proposal(npc, subject, "VISIT_PATTERN", "POSITIVE",
                    "Recent supported visits have gone well.", List.of(a.assertionId(),
                            b.assertionId()), 1, ReflectionService.ReflectionKind
                            .REPEATED_RELATED_EVENTS)).get(2, TimeUnit.SECONDS);
            assert result.committed();
            assert result.assertion().supportIds().size() == 2;
            assert result.assertion().confidence() <= .8;
            assert result.assertion().provenance().sourceKind()
                    == EvidenceSourceKind.DERIVED_REFLECTION;
        }
        UUID otherNpc = UUID.randomUUID();
        BeliefAssertion support = observed(store, otherNpc, subject, "SEEN", "yes", .9);
        try (ReflectionService service = new ReflectionService(store, ignored -> { })) {
            var unsupported = service.submit(proposal(otherNpc, UUID.randomUUID(), "OWNS",
                    "mill", "An unsupported stranger owns a mill.", List.of(
                            support.assertionId()), .7, ReflectionService.ReflectionKind
                            .IMPORTANCE_CONSOLIDATION)).get(2, TimeUnit.SECONDS);
            assert !unsupported.committed() && unsupported.reason().equals("UNSUPPORTED_ENTITY");
        }
        UUID conflictNpc = UUID.randomUUID(), conflictSubject = UUID.randomUUID();
        BeliefAssertion left = observed(store, conflictNpc, conflictSubject, "IS_AT", "east", .8);
        BeliefAssertion right = observed(store, conflictNpc, conflictSubject, "IS_AT", "west", .8);
        try (ReflectionService service = new ReflectionService(store, ignored -> { })) {
            var conflict = service.submit(proposal(conflictNpc, conflictSubject, "LOCATION_PATTERN",
                    "east", "The target is usually east.", List.of(left.assertionId(),
                            right.assertionId()), .7, ReflectionService.ReflectionKind
                            .CONTRADICTION_SYNTHESIS)).get(2, TimeUnit.SECONDS);
            assert !conflict.committed() && conflict.reason().equals("CONTRADICTED_SUPPORT");
        }
    }

    private static UUID invalidationAndRestartSeed(SourcedBeliefStore store, UUID npc)
            throws Exception {
        UUID subject = UUID.randomUUID();
        BeliefAssertion first = observed(store, npc, subject, "WORK_RESULT_ONE", "good", .9);
        BeliefAssertion second = observed(store, npc, subject, "WORK_RESULT_TWO", "good", .85);
        BeliefAssertion derived;
        try (ReflectionService service = new ReflectionService(store, ignored -> { })) {
            derived = service.submit(proposal(npc, subject, "WORK_PATTERN", "RELIABLE",
                    "Recent supported work has been reliable.", List.of(first.assertionId(),
                            second.assertionId()), .8, ReflectionService.ReflectionKind
                            .REPEATED_RELATED_EVENTS)).get(2, TimeUnit.SECONDS).assertion();
        }
        store.retract(first.assertionId(), "AUTHORITATIVE_CORRECTION", Instant.now());
        assert store.assertion(derived.assertionId()).orElseThrow().status()
                == EpistemicStatus.DISPUTED;
        return derived.assertionId();
    }

    private static void actionOutcomeLearning(SourcedBeliefStore store) throws Exception {
        UUID npc = UUID.randomUUID(), player = UUID.randomUUID();
        try (ReflectionService service = new ReflectionService(store, ignored -> { })) {
            store.ingestActionResult(npc, player, request("GO_WEST_BRIDGE", "fail-1"),
                    NpcActionResult.failure("PATH_BLOCKED", "West bridge path blocked."),
                    Instant.now());
            assert !store.current(npc, null, "ACTION_FAILED").isEmpty();
            assert store.current(npc, null, "PROCEDURAL_OUTCOME").size() == 1;
            assert service.repeatedFailureProposal(npc, "GO_WEST_BRIDGE", Instant.now()).isEmpty();
            store.ingestActionResult(npc, player, request("GO_WEST_BRIDGE", "fail-2"),
                    NpcActionResult.failure("PATH_BLOCKED", "West bridge path blocked."),
                    Instant.now());
            store.ingestActionResult(npc, player, request("GO_WEST_BRIDGE", "fail-3"),
                    NpcActionResult.failure("PATH_BLOCKED", "West bridge path blocked."),
                    Instant.now());
            var proposal = service.repeatedFailureProposal(npc, "GO_WEST_BRIDGE",
                    Instant.now()).orElseThrow();
            BeliefAssertion lesson = service.submit(proposal).get(2, TimeUnit.SECONDS).assertion();
            assert lesson.supportIds().size() == 3;
            store.ingestActionResult(npc, player, request("GO_WEST_BRIDGE", "success-1"),
                    NpcActionResult.success("West bridge route succeeded."), Instant.now());
            assert store.assertion(lesson.assertionId()).orElseThrow().status()
                    == EpistemicStatus.DISPUTED;
        }
    }

    private static void speechContamination(SourcedBeliefStore store) throws Exception {
        UUID npc = UUID.randomUUID(), subject = UUID.randomUUID();
        BeliefAssertion speech = store.assertBelief(new BeliefProposal(null, npc, subject,
                "speech", "CLAIM", "invented", "Generated speech claimed an event.",
                BeliefAssertion.Polarity.POSITIVE, EpistemicStatus.BELIEVED, .7,
                new BeliefProvenance(EvidenceSourceKind.PLAYER_TESTIMONY, npc,
                        List.of("NPC_SPEECH:" + UUID.randomUUID()), true, false), null,
                BeliefAssertion.AssertionScope.EVENT, List.of(), Instant.now()));
        try (ReflectionService service = new ReflectionService(store, ignored -> { })) {
            var result = service.submit(proposal(npc, subject, "EVENT_PATTERN", "true",
                    "An invented event happened.", List.of(speech.assertionId()), .6,
                    ReflectionService.ReflectionKind.IMPORTANCE_CONSOLIDATION))
                    .get(2, TimeUnit.SECONDS);
            assert !result.committed() && result.reason().equals("SPEECH_ONLY_SUPPORT");
        }
    }

    private static void schedulingYield(SourcedBeliefStore store) throws Exception {
        try (OrbisResourceScheduler scheduler = scheduler();
             ReflectionService service = new ReflectionService(store, ignored -> { })) {
            FakeProvider provider = new FakeProvider("foreground", AiServiceKind.LANGUAGE_MODEL,
                    ExecutionPlacement.LOCAL_CPU, 1);
            OrbisResourceScheduler.Lease foreground = scheduler.admit(new OrbisResourceRequest(
                    UUID.randomUUID(), ResourceWorkload.LLM, ResourcePriority.HIGH, provider,
                    true, 2_000), ignored -> { }).get(2, TimeUnit.SECONDS);
            long snapshotDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (scheduler.snapshot().activeByWorkload().getOrDefault(
                    ResourceWorkload.LLM, 0) == 0 && System.nanoTime() < snapshotDeadline) {
                Thread.onSpinWait();
            }
            service.scheduler(scheduler);
            UUID npc = UUID.randomUUID(), subject = UUID.randomUUID();
            BeliefAssertion support = observed(store, npc, subject, "OBSERVED", "yes", .8);
            var result = service.submit(proposal(npc, subject, "PATTERN", "yes",
                    "A supported pattern.", List.of(support.assertionId()), .6,
                    ReflectionService.ReflectionKind.SCHEDULED_CONSOLIDATION))
                    .get(2, TimeUnit.SECONDS);
            assert !result.committed() && result.reason().equals("BACKGROUND_PREEMPTED")
                    : result + " scheduler=" + scheduler.snapshot();
            foreground.close();
        }
    }

    private static ReflectionService.ReflectionProposal proposal(UUID npc, UUID subject,
            String predicate, String value, String statement, List<UUID> supports,
            double confidence, ReflectionService.ReflectionKind kind) {
        return new ReflectionService.ReflectionProposal(UUID.randomUUID(), npc,
                new ReflectionService.DerivedProposition(subject, "supported subject", predicate,
                        value, statement, true, BeliefAssertion.AssertionScope.ENTITY),
                supports, confidence, kind, BeliefAssertion.TemporalScope.stable(Instant.now()),
                Optional.empty());
    }

    private static BeliefAssertion observed(SourcedBeliefStore store, UUID npc, UUID subject,
            String predicate, String value, double confidence) {
        return store.assertBelief(new BeliefProposal(null, npc, subject, "supported subject",
                predicate, value, "Observed " + predicate + " " + value + ".",
                BeliefAssertion.Polarity.POSITIVE, EpistemicStatus.KNOWN, confidence,
                new BeliefProvenance(EvidenceSourceKind.DIRECT_OBSERVATION, npc,
                        List.of("DIRECT_OBSERVATION:" + UUID.randomUUID()), false, false), null,
                BeliefAssertion.AssertionScope.ENTITY, List.of(), Instant.now()));
    }

    private static NpcActionRequest request(String id, String tool) {
        return new NpcActionRequest(id, new JsonObject(), tool);
    }
    private static SourcedBeliefStore store(Path root) {
        SourcedBeliefStore store = new SourcedBeliefStore(root); store.load(); return store;
    }
    private static OrbisResourceScheduler scheduler() {
        OrbisResourceConfig base = OrbisResourceConfig.defaults();
        RuntimeResourceMonitor.Snapshot host = new RuntimeResourceMonitor.Snapshot(Instant.now(),
                20, 8_000, 32_000, 500, 4_000, 0, 10, 2_000, 10_000, 12_000,
                "cpu", 16, "gpu", "", true, true, "");
        return new OrbisResourceScheduler(base, () -> host, ignored -> { });
    }
    private record FakeProvider(String providerId, AiServiceKind serviceKind,
            ExecutionPlacement placement, int concurrencyLimit) implements AiProvider {
        public ProviderExecutionMode executionMode() { return ProviderExecutionMode.LOCAL; }
        public AiProviderCapabilities capabilities() {
            return new AiProviderCapabilities(true, true, true, Set.of("test"));
        }
        public AiResourceRequirements resourceRequirements() {
            return new AiResourceRequirements(placement, "test", 32, 0, concurrencyLimit,
                    false, true, 50);
        }
    }
}
