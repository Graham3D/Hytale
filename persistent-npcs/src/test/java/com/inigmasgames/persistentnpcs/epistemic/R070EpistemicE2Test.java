package com.inigmasgames.persistentnpcs.epistemic;

import com.inigmasgames.persistentnpcs.cognition.CognitionContext;
import com.inigmasgames.persistentnpcs.conversation.CognitiveContextPlan;
import com.inigmasgames.persistentnpcs.memory.MemoryRecord;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.memory.MemoryType;
import com.inigmasgames.persistentnpcs.perception.NpcPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.perception.PerceivedItem;
import com.inigmasgames.persistentnpcs.perception.RawPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.perception.SemanticSelfState;
import com.inigmasgames.persistentnpcs.perception.SemanticWorldModel;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class R070EpistemicE2Test {
    private static final UUID NPC = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID WORLD = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private R070EpistemicE2Test() { }

    public static void main(String[] args) throws Exception {
        System.setProperty(EpistemicFeatureMode.PROPERTY, "SHADOW");
        identityRecallIsKnownAndReadOnly();
        entityExistenceCannotAuthorizeProperty();
        currentPerceptionAndSelfStateAreDirect();
        actionCapabilityDoesNotExecute();
        clarificationUsesDeliveredProposition();
        conflictsRemainConflicts();
        malformedInputDoesNoRetrieval();
        subjectiveModeCannotSmuggleBiography();
        irrelevantAndWrongEntityEvidenceAreRejected();
        currentAuthorityExcludesStaleMemory();
        e2CorpusAndLatency();
        System.out.println("R070 E2 evidence/answerability/AnswerPlan tests passed.");
    }

    private static void identityRecallIsKnownAndReadOnly() throws Exception {
        Path root = Files.createTempDirectory("r070-identity-");
        MemoryStore memories = new MemoryStore(root, 100);
        memories.append(nameMemory("Graham", Instant.now()));
        memories.flush();
        long writes = memories.persistenceWriteCount();
        EpistemicContract result = enrich("What's my name?", memories, null,
                emptyRaw(), null, new ConversationWorkspace(), List.of());
        assert result.answerability() == Answerability.KNOWN : result.answerability();
        assert result.evidence().supporting().size() == 1;
        EvidenceRef fact = result.evidence().supporting().getFirst();
        assert fact.sourceKind() == EvidenceSourceKind.PLAYER_TESTIMONY;
        assert fact.predicateKey().equals("NAME") && fact.objectValue().equals("Graham");
        assert result.answerPlan().authorizedPropositions().getFirst().contains("Graham");
        assert memories.persistenceWriteCount() == writes : "E2 wrote persistence";
    }

    private static void entityExistenceCannotAuthorizeProperty() {
        RawPerceptionSnapshot raw = heldLantern();
        EpistemicContract result = enrich("Is my lantern flickering?", null, null,
                raw, cognition(raw, "social attention"), new ConversationWorkspace(), List.of());
        assert result.answerability() == Answerability.UNKNOWN;
        assert result.evidence().supporting().isEmpty();
        assert result.evidence().contextual().size() == 1;
        assert result.evidence().contextual().getFirst().predicateKey().equals("HELD_ITEM");
        assert result.answerPlan().unsupportedRequestedProperties()
                .contains("PROPERTY:FLICKERING");
        assert result.answerPlan().authorizedPropositions().stream()
                .noneMatch(value -> value.toLowerCase().contains("flicker"));
    }

    private static void currentPerceptionAndSelfStateAreDirect() {
        RawPerceptionSnapshot raw = heldLantern();
        EpistemicContract held = enrich("What am I holding?", null, null, raw,
                cognition(raw, "walking to the forge"), new ConversationWorkspace(), List.of());
        assert held.answerability() == Answerability.KNOWN;
        assert held.evidence().supporting().getFirst().authoritative();
        assert held.evidence().supporting().getFirst().objectValue().equals("Lantern");
        EpistemicContract scene = enrich("What do you see around us?", null, null, raw,
                cognition(raw, "walking to the forge"), new ConversationWorkspace(), List.of());
        assert scene.answerability() == Answerability.KNOWN;
        assert scene.evidence().supporting().stream().allMatch(EvidenceRef::authoritative);
        EpistemicContract self = enrich("Where are you going?", null, null, raw,
                cognition(raw, "walking to the forge"), new ConversationWorkspace(), List.of());
        assert self.answerability() == Answerability.KNOWN;
        assert self.answerPlan().authorizedPropositions().getFirst().contains("walking to the forge");
    }

    private static void actionCapabilityDoesNotExecute() {
        EpistemicContract result = enrich("Can you follow me?", null, null, emptyRaw(), null,
                new ConversationWorkspace(), List.of("FOLLOW_PLAYER"));
        assert result.answerability() == Answerability.NEEDS_ACTION;
        assert result.evidence().supporting().getFirst().sourceKind()
                == EvidenceSourceKind.ACTION_CAPABILITY;
        assert result.answerPlan().requestedAction().equals("FOLLOW_PLAYER");
        assert result.answerPlan().forbiddenClaimClasses().contains("UNCOMMITTED_ACTION_PROMISE");
        assert result.answerPlan().maxObjectiveClaims() == 0;
    }

    private static void clarificationUsesDeliveredProposition() {
        ConversationWorkspace workspace = new ConversationWorkspace();
        workspace.observeDelivered("I meant that the west bridge looked unsafe.", Instant.now());
        EpistemicContract result = enrich("What did you mean by that?", null, null,
                emptyRaw(), null, workspace, List.of());
        assert result.answerability() == Answerability.KNOWN;
        assert result.evidence().supporting().getFirst().sourceKind()
                == EvidenceSourceKind.CONVERSATION_WORKSPACE;
        assert result.answerPlan().answerKind().equals("BOUND_CLARIFICATION");
    }

    private static void conflictsRemainConflicts() throws Exception {
        MemoryStore memories = new MemoryStore(Files.createTempDirectory("r070-conflict-"), 100);
        memories.append(nameMemory("Graham", Instant.now().minusSeconds(30)));
        memories.append(nameMemory("Greg", Instant.now()));
        memories.flush();
        EpistemicContract result = enrich("What's my name?", memories, null,
                emptyRaw(), null, new ConversationWorkspace(), List.of());
        assert result.answerability() == Answerability.CONFLICTED : result.answerability();
        assert result.evidence().supporting().size() == 1;
        assert result.evidence().contradicting().size() == 1;
        assert result.evidence().sufficiency() == EvidenceSufficiency.CONFLICTED;
        assert result.answerPlan().answerKind().equals("CONFLICT_DISCLOSURE");
    }

    private static void malformedInputDoesNoRetrieval() throws Exception {
        MemoryStore memories = new MemoryStore(Files.createTempDirectory("r070-malformed-"), 100);
        memories.append(nameMemory("Graham", Instant.now())); memories.flush();
        EpistemicContract result = enrich("I want you to tell me what's in my", memories, null,
                heldLantern(), cognition(heldLantern(), "idle"),
                new ConversationWorkspace(), List.of("FOLLOW_PLAYER"));
        assert result.answerability() == Answerability.UNRESOLVED;
        assert result.evidence().supporting().isEmpty();
        assert result.evidence().contextual().isEmpty();
        assert result.evidence().retrievalMicros() < 10_000;
    }

    private static void subjectiveModeCannotSmuggleBiography() {
        EpistemicContract known = enrich("Do you like apples?", null, null, emptyRaw(), null,
                new ConversationWorkspace(), List.of());
        assert known.answerability() == Answerability.SUBJECTIVE;
        assert known.answerPlan().answerKind().equals("AUTHORED_PREFERENCE");
        EpistemicContract premise = enrich(
                "Do you like spiders because your brother was killed by one?", null, null,
                emptyRaw(), null, new ConversationWorkspace(), List.of());
        assert premise.answerability() == Answerability.SUBJECTIVE;
        assert premise.claimPolicy().restrictions().contains(
                "SUBJECTIVE_EMBEDDED_OBJECTIVE_PREMISE_REQUIRES_EVIDENCE");
        assert premise.answerPlan().forbiddenClaimClasses().contains(
                "SUBJECTIVE_MODE_CANNOT_SMUGGLE_OBJECTIVE_PREMISES");
    }

    private static void irrelevantAndWrongEntityEvidenceAreRejected() throws Exception {
        MemoryStore memories = new MemoryStore(Files.createTempDirectory("r070-irrelevant-"), 100);
        memories.append(new MemoryRecord(UUID.randomUUID(), NPC, PLAYER, Instant.now(),
                MemoryType.PLAYER_FACT, .8, "Player fact: favorite lantern maker is Graham.",
                .8, "PLAYER_REPORT:source=" + PLAYER, List.of(PLAYER), "", "test"));
        memories.flush();
        EpistemicContract identity = enrich("What's my name?", memories, null,
                emptyRaw(), null, new ConversationWorkspace(), List.of());
        assert identity.answerability() == Answerability.UNKNOWN;
        RelationshipStore relationships = new RelationshipStore(
                Files.createTempDirectory("r070-relationship-"));
        relationships.adjust(NPC, UUID.randomUUID(), 0, 20, 0, 0, 0, 0, 0, Instant.now());
        EpistemicContract friends = enrich("Do you have any friends?", null, relationships,
                emptyRaw(), null, new ConversationWorkspace(), List.of());
        assert friends.answerability() == Answerability.UNKNOWN;
        assert friends.evidence().supporting().isEmpty();
    }

    private static void currentAuthorityExcludesStaleMemory() throws Exception {
        MemoryStore memories = new MemoryStore(Files.createTempDirectory("r070-stale-"), 100);
        memories.append(new MemoryRecord(UUID.randomUUID(), NPC, PLAYER,
                Instant.now().minusSeconds(86_400), MemoryType.EPISODIC, .7,
                "Yesterday the player was holding a sword and the lantern was hot.",
                .7, "PLAYER_REPORT:source=" + PLAYER, List.of(PLAYER), "", "historical"));
        memories.flush();
        RawPerceptionSnapshot current = heldLantern();
        EpistemicContract held = enrich("What am I holding?", memories, null, current,
                cognition(current, "social attention"), new ConversationWorkspace(), List.of());
        assert held.answerability() == Answerability.KNOWN;
        assert held.evidence().supporting().size() == 1;
        assert held.evidence().supporting().getFirst().objectValue().equals("Lantern");
        assert held.evidence().supporting().stream()
                .noneMatch(value -> value.compactProposition().contains("sword"));
        EpistemicContract hot = enrich("Is my lantern hot?", memories, null, current,
                cognition(current, "social attention"), new ConversationWorkspace(), List.of());
        assert hot.answerability() == Answerability.UNKNOWN;
        assert hot.evidence().supporting().isEmpty();
        assert hot.evidence().contextual().size() == 1;
    }

    private static void e2CorpusAndLatency() {
        ArrayList<Long> samples = new ArrayList<>();
        RawPerceptionSnapshot raw = heldLantern();
        for (int index = 0; index < 1_000; index++) {
            EpistemicContract result = enrich(index % 2 == 0 ? "What am I holding?"
                    : "Can you follow me?", null, null, raw,
                    cognition(raw, "social attention"), new ConversationWorkspace(),
                    List.of("FOLLOW_PLAYER"));
            assert result.mode() == EpistemicFeatureMode.SHADOW;
            assert result.answerPlan().status().equals("E2_SHADOW");
            samples.add(result.evidence().retrievalMicros());
        }
        samples.sort(Comparator.naturalOrder());
        long p95 = samples.get((int) (samples.size() * .95));
        assert p95 < 5_000 : "E2 p95Micros=" + p95;
        System.out.println("R070 E2 retrieval p95Micros=" + p95
                + " maxMicros=" + samples.getLast());
    }

    private static EpistemicContract enrich(String text, MemoryStore memories,
            RelationshipStore relationships, RawPerceptionSnapshot raw,
            CognitionContext cognition, ConversationWorkspace workspace,
            List<String> actions) {
        EpistemicContract base = EpistemicShadowAnalyzer.analyzeWithWorkspace(text,
                CognitiveContextPlan.full("E2_TEST"), workspace);
        EpistemicEvidenceRetriever retriever = new EpistemicEvidenceRetriever(memories, null,
                relationships, null, null, null);
        return retriever.enrich(base, UUID.randomUUID(), profile(), PLAYER, text, raw,
                cognition, workspace, actions);
    }

    private static MemoryRecord nameMemory(String name, Instant at) {
        return new MemoryRecord(UUID.randomUUID(), NPC, PLAYER, at, MemoryType.PLAYER_FACT,
                .95, "Player fact: stated name=" + name + ".", .82,
                "DIRECT", List.of(PLAYER), "",
                "The player stated this; it was not independently verified.");
    }

    private static NpcProfile profile() {
        return new NpcProfile(NPC, "Mara", "Tavern keeper", "curious", "Biography",
                "Run the tavern", "Tavern", "Tavern", List.of("apples"),
                List.of("spiders"), List.of(), List.of("FOLLOW_PLAYER"), 0).validated();
    }

    private static RawPerceptionSnapshot emptyRaw() {
        return RawPerceptionSnapshot.unavailable(UUID.randomUUID(), NPC);
    }

    private static RawPerceptionSnapshot heldLantern() {
        PerceivedItem lantern = new PerceivedItem(null, "lantern", "Lantern", 1,
                100, 100, "{}", 0);
        NpcPerceptionSnapshot snapshot = new NpcPerceptionSnapshot(NPC, UUID.randomUUID(),
                WORLD, null, 0, 0, 0, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), 0, lantern, List.of());
        return RawPerceptionSnapshot.fromLegacy(UUID.randomUUID(), snapshot);
    }

    private static CognitionContext cognition(RawPerceptionSnapshot raw, String activity) {
        SemanticSelfState self = new SemanticSelfState("Mara", true, "current position",
                "tavern", "present", activity, "none", "player", List.of("the player"));
        SemanticWorldModel semantic = new SemanticWorldModel(self, null, "inside a tavern",
                List.of("forge", "table"), List.of("the player"), List.of(), List.of(),
                List.of("lantern"), List.of(), true);
        return new CognitionContext(raw.responseId(), UUID.randomUUID(), PLAYER, Instant.now(),
                profile(), raw.engineSnapshot(), null, "UNKNOWN", activity, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), null, Set.of(), List.of(),
                raw, semantic, "", List.of(), CognitiveContextPlan.full("E2_TEST"), List.of());
    }
}
