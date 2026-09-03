package com.inigmasgames.persistentnpcs.epistemic;

import com.inigmasgames.persistentnpcs.cognition.SourcedBeliefStore;
import com.inigmasgames.persistentnpcs.conversation.ConversationSessionManager;
import com.inigmasgames.persistentnpcs.memory.MemoryRecord;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.memory.MemoryType;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** E5 LongMemEval-inspired extraction/temporal/update/conflict/abstention gate. */
public final class R081EpistemicE5Test {
    private static final UUID NPC = UUID.randomUUID(), PLAYER = UUID.randomUUID();
    private R081EpistemicE5Test() { }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("r081-e5-");
        MemoryStore memories = new MemoryStore(root, 256);
        memories.load();
        factExtractionTemporalAndAbstention(memories);
        workspaceAndSessionContinuity(memories);
        correctionHistoryAndConflict(root.resolve("beliefs"));
        Result timing = benchmark(memories);
        memories.flush();
        MemoryStore restarted = new MemoryStore(root, 256);
        restarted.load();
        assertFact(restarted, "Where did I say I hid the rock today?", "stream");
        assert timing.p95Micros < 25_000 : timing;
        System.out.println("R081 E5 validation passed. p50Micros=" + timing.p50Micros
                + " p95Micros=" + timing.p95Micros + " maxMicros=" + timing.maxMicros);
    }

    private static void factExtractionTemporalAndAbstention(MemoryStore memories) {
        Instant now = Instant.now();
        memories.append(memory(now.minusSeconds(600), MemoryType.EPISODIC,
                "We discussed the weather. I hid a rock beside the stream today. "
                        + "Then we changed the subject.", "SESSION:first"));
        memories.append(memory(now.minus(Duration.ofDays(2)), MemoryType.EPISODIC,
                "Earlier the player was holding a lantern.", "SESSION:old"));
        memories.append(memory(now.minusSeconds(30), MemoryType.EPISODIC,
                "The player was holding an Onyxium dagger.", "SESSION:new"));

        var exact = facts(memories, "Where did I say I hid the rock today?", now);
        assert exact.selected().size() == 1 : exact;
        assert exact.selected().getFirst().fact().statement().equals(
                "I hid a rock beside the stream today.") : exact;
        assert exact.selected().getFirst().fact().sourceMemoryId() != null;
        EpistemicContract extracted = enrich(memories, null,
                "Where did I say I hid the rock today?", new ConversationWorkspace());
        assert extracted.evidence().supporting().size() == 1 : extracted.evidence();
        assert extracted.evidence().supporting().getFirst().compactProposition()
                .equals("I hid a rock beside the stream today.") : extracted.evidence();
        assert extracted.evidence().retrievalDiagnostics().candidateCount() > 0;

        var earlier = facts(memories, "What was I holding earlier?", now);
        assert earlier.selected().stream().anyMatch(value -> value.fact().statement()
                .toLowerCase().contains("lantern")) : earlier;

        var current = facts(memories, "What am I holding currently?", now);
        assert current.selected().size() == 1
                && current.selected().getFirst().fact().statement().contains("dagger") : current;

        var absent = facts(memories, "Do you remember the violet dragon treaty?", now);
        assert absent.selected().isEmpty() : absent;
        assert absent.rejected().stream().allMatch(value -> value.reason()
                .equals("WEAK_SEMANTIC_MATCH")) : absent;
        EpistemicContract abstained = enrich(memories, null,
                "Do you remember when we saw the violet dragon treaty?",
                new ConversationWorkspace());
        assert abstained.evidence().sufficiency() == EvidenceSufficiency.IRRELEVANT
                && abstained.answerability() == Answerability.UNKNOWN : abstained;
        long reads = memories.persistenceReadCount();
        facts(memories, "rock stream", now);
        assert memories.persistenceReadCount() == reads : "live retrieval touched disk";
    }

    private static void workspaceAndSessionContinuity(MemoryStore memories) {
        ConversationWorkspace workspace = new ConversationWorkspace();
        EpistemicShadowAnalyzer.analyzeInitial(
                "Meet me tonight and I will show you the mill.", workspace);
        assert workspace.snapshot(Instant.now()).commitments().size() == 1;
        String commitment = workspace.snapshot(Instant.now()).commitments().getFirst();
        memories.append(memory(Instant.now(), MemoryType.COMMITMENT, commitment,
                "E5_CONVERSATION_WORKSPACE_COMMITMENT"));
        EpistemicShadowAnalyzer.analyzeInitial(
                "Let's discuss the old bridge tomorrow.", workspace);
        String openTopic = workspace.snapshot(Instant.now()).openTopics().getFirst();
        memories.append(memory(Instant.now(), MemoryType.COMMITMENT, openTopic,
                "E5_CONVERSATION_WORKSPACE_OPEN_TOPIC"));
        ConversationSessionManager sessions = new ConversationSessionManager(
                Duration.ofMillis(1), memories);
        var first = sessions.focus(NPC, PLAYER, Instant.now());
        assert first.epistemicWorkspace().snapshot(Instant.now()).commitments()
                .contains(commitment);
        assert first.epistemicWorkspace().snapshot(Instant.now()).openTopics()
                .contains(openTopic);
        sessions.end(PLAYER, NPC);
        var next = sessions.focus(NPC, PLAYER, Instant.now().plusSeconds(2));
        assert next.epistemicWorkspace().snapshot(Instant.now().plusSeconds(2)).commitments()
                .contains(commitment) : "commitment did not bridge sessions";
        assert next.epistemicWorkspace().snapshot(Instant.now().plusSeconds(2)).openTopics()
                .contains(openTopic) : "open topic did not bridge sessions";

        EvidenceRef rock = new EvidenceRef(1, "fact:rock", EvidenceSourceKind.EPISODIC_MEMORY,
                EpistemicStatus.BELIEVED, .9, true, "The rock is beside the stream.",
                "CURRENT_PLAYER", "PAST_EVENT", "rock", Instant.now(), "RECENT",
                "CURRENT_PLAYER", false, false, "", "TODAY");
        EvidencePacket packet = packet(List.of(rock));
        next.epistemicWorkspace().observeEvidence(packet, Instant.now());
        EpistemicContract follow = EpistemicShadowAnalyzer.analyzeInitial(
                "Was it near the stream?", next.epistemicWorkspace());
        assert !follow.dialogueFrame().ambiguous()
                && follow.dialogueFrame().objectKey().contains("ROCK") : follow.dialogueFrame();
        EpistemicContract ambiguous = EpistemicShadowAnalyzer.analyzeInitial(
                "Is it valuable?", new ConversationWorkspace());
        assert ambiguous.dialogueFrame().ambiguous();
    }

    private static void correctionHistoryAndConflict(Path root) {
        try (SourcedBeliefStore store = new SourcedBeliefStore(root)) {
            store.load();
            store.assertBelief(proposal("NAME", "Grant", PLAYER));
            store.assertBelief(proposal("NAME", "Graham", PLAYER));
            E5QueryExpansion currentPlan = E5QueryExpansion.expand("What is my current name?",
                    null, null, Instant.now());
            var current = store.queryAssertionsReadOnly(NPC, PLAYER, "NAME",
                    "What is my current name?", currentPlan, 4, Instant.now());
            assert current.size() == 1 && current.getFirst().assertion().value()
                    .equals("Graham") : current;
            E5QueryExpansion oldPlan = E5QueryExpansion.expand("What was my name before?",
                    null, null, Instant.now());
            var history = store.queryAssertionsReadOnly(NPC, PLAYER, "NAME",
                    "What was my name before?", oldPlan, 4, Instant.now());
            assert history.stream().anyMatch(value -> value.assertion().value()
                    .equals("Grant") && value.assertion().status()
                    == EpistemicStatus.SUPERSEDED) : history;

            UUID witness = UUID.randomUUID();
            store.assertBelief(proposal("IS_AT", "east gate", witness));
            store.assertBelief(proposal("IS_AT", "west gate", UUID.randomUUID()));
            var conflict = store.queryAssertionsReadOnly(NPC, PLAYER, "IS_AT",
                    "Where is the player?", E5QueryExpansion.expand("Where is the player?",
                            null, null, Instant.now()), 4, Instant.now());
            assert conflict.size() == 2 && conflict.stream().allMatch(value ->
                    value.assertion().status() == EpistemicStatus.DISPUTED) : conflict;

            store.assertBelief(proposal("NAME", "Gray", UUID.randomUUID()));
            EpistemicContract conflicted = enrich(null, store, "What's my name?",
                    new ConversationWorkspace());
            assert conflicted.evidence().sufficiency() == EvidenceSufficiency.CONFLICTED
                    && conflicted.evidence().contradicting().size() >= 1
                    && conflicted.answerability() == Answerability.CONFLICTED : conflicted;
        }
    }

    private static Result benchmark(MemoryStore memories) {
        ArrayList<Long> samples = new ArrayList<>();
        Instant now = Instant.now();
        for (int i = 0; i < 1_000; i++) {
            long started = System.nanoTime();
            var result = facts(memories, i % 2 == 0
                    ? "Where did I hide the rock today?"
                    : "What was I holding earlier?", now);
            assert !result.selected().isEmpty();
            samples.add((System.nanoTime() - started) / 1_000);
        }
        samples.sort(Comparator.naturalOrder());
        return new Result(samples.get(samples.size() / 2),
                samples.get((int) (samples.size() * .95)), samples.getLast());
    }

    private static MemoryStore.FactRetrieval facts(MemoryStore store, String query, Instant now) {
        return store.queryFactsReadOnly(NPC, PLAYER, query,
                Set.of(MemoryType.EPISODIC, MemoryType.PLAYER_FACT,
                        MemoryType.ACTION_RESULT, MemoryType.COMMITMENT), 1,
                E5QueryExpansion.expand(query, null, null, now), now);
    }
    private static void assertFact(MemoryStore store, String query, String expected) {
        var result = facts(store, query, Instant.now());
        assert !result.selected().isEmpty() && result.selected().getFirst().fact().statement()
                .toLowerCase().contains(expected) : result;
    }
    private static EpistemicContract enrich(MemoryStore memories, SourcedBeliefStore beliefs,
            String query, ConversationWorkspace workspace) {
        System.setProperty(EpistemicFeatureMode.PROPERTY, "AUTHORITATIVE");
        EpistemicContract base = EpistemicShadowAnalyzer.analyzeInitial(query, workspace);
        NpcProfile profile = new NpcProfile(NPC, "Mara", "blacksmith", "practical",
                "", "", "", "", List.of(), List.of(), List.of(), List.of(), 0);
        return new EpistemicEvidenceRetriever(memories, beliefs, null, null, null, null)
                .enrich(base, UUID.randomUUID(), profile, PLAYER, query, null, null,
                        workspace, List.of());
    }
    private static MemoryRecord memory(Instant at, MemoryType type, String summary,
            String source) {
        return new MemoryRecord(UUID.randomUUID(), NPC, PLAYER, at, type, .75, summary,
                .9, source, List.of(PLAYER), "", "Player-authored or observed fact.");
    }
    private static BeliefProposal proposal(String predicate, String value, UUID actor) {
        return new BeliefProposal(null, NPC, PLAYER, "player", predicate, value,
                "The player's " + predicate.toLowerCase() + " is " + value + ".",
                BeliefAssertion.Polarity.POSITIVE, EpistemicStatus.BELIEVED, .85,
                new BeliefProvenance(EvidenceSourceKind.PLAYER_TESTIMONY, actor,
                        List.of("TEST:" + UUID.randomUUID()), false, false), null,
                BeliefAssertion.AssertionScope.ENTITY, List.of(), Instant.now());
    }
    private static EvidencePacket packet(List<EvidenceRef> supporting) {
        return new EvidencePacket(2, UUID.randomUUID(), NPC, PLAYER, null, "EPISODIC_RECALL",
                "CURRENT_PLAYER", "PAST_EVENT", supporting, List.of(), List.of(), List.of(),
                EvidenceSufficiency.SUFFICIENT, List.of(), List.of(), 0, List.of(),
                List.of(EvidenceSourceKind.EPISODIC_MEMORY), 32, 10, false,
                RetrievalDiagnostics.empty());
    }
    private record Result(long p50Micros, long p95Micros, long maxMicros) { }
}
