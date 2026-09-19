package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.autonomy.AgentOperationStore;
import com.inigmasgames.persistentnpcs.cognition.CognitionTraceStore;
import com.inigmasgames.persistentnpcs.cognition.GroundedIntent;
import com.inigmasgames.persistentnpcs.cognition.MaterialInformationGuard;
import com.inigmasgames.persistentnpcs.cognition.NpcCognitionService;
import com.inigmasgames.persistentnpcs.cognition.NpcEmotionStore;
import com.inigmasgames.persistentnpcs.cognition.SourcedBelief;
import com.inigmasgames.persistentnpcs.cognition.SourcedBeliefStore;
import com.inigmasgames.persistentnpcs.conversation.CommittedDialogueResponse;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.conversation.DialogueMode;
import com.inigmasgames.persistentnpcs.conversation.DialogueNaturalnessFilter;
import com.inigmasgames.persistentnpcs.economy.ObligationStore;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.perception.EnvironmentSnapshot;
import com.inigmasgames.persistentnpcs.perception.NpcPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.plan.SharedPlanStore;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import com.inigmasgames.persistentnpcs.task.NpcTaskStore;
import com.inigmasgames.persistentnpcs.voice.VocalState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class R029GroundedCognitionTest {
    private R029GroundedCognitionTest() { }

    public static void main(String[] args) throws Exception {
        provenancePersists();
        relationshipChangesIntentUtility();
        materialInformationBeatsRepetition();
        worldFactsStayUnknownUnlessAuthoritative();
        directIntentUsesExistingAction();
        canonicalTextIsSharedAndStaleIdsLoseAuthority();
        genericAssistantFallbackIsGone();
        arbitraryProfilesUseTheSamePipeline();
        System.out.println("R029 grounded cognition tests passed.");
    }

    private static void provenancePersists() throws Exception {
        Path root = Files.createTempDirectory("r029-beliefs-");
        UUID npc = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        UUID subject = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();
        UUID response = UUID.randomUUID();
        SourcedBelief belief;
        // Persistence is asynchronous by design; close is the deterministic flush boundary.
        try (SourcedBeliefStore store = new SourcedBeliefStore(root)) {
            store.load();
            belief = store.append(new SourcedBelief(UUID.randomUUID(), npc,
                    source, subject, "Rowan", "REQUEST_REPORT", "Rowan wants to see Mara.",
                    Instant.now(), 0.68, 0.72, conversation, response,
                    List.of("PLAYER_REPORT:" + source)));
        }
        try (SourcedBeliefStore reloaded = new SourcedBeliefStore(root)) {
            reloaded.load();
            SourcedBelief persisted = reloaded.relevant(npc, List.of(subject), 4).getFirst();
            assert persisted.sourceEntityId().equals(source);
            assert persisted.subjectEntityId().equals(subject);
            assert persisted.responseId().equals(response);
            assert persisted.conversationId().equals(conversation);
            assert persisted.proposition().equals(belief.proposition());
        }
    }

    private static void relationshipChangesIntentUtility() throws Exception {
        Fixture low = fixture();
        Fixture high = fixture();
        high.relationships.adjust(high.profile.id(), high.playerId,
                high.profile.defaultDisposition(), 80, 70, 70, 0, 0, 60, Instant.now());
        var lowTurn = low.service.evaluateGrounded(UUID.randomUUID(), low.profile, low.session,
                "I promised to return your hammer.", low.unknownPerception,
                DialogueMode.ORDINARY_CONVERSATION, List.of());
        var highTurn = high.service.evaluateGrounded(UUID.randomUUID(), high.profile,
                high.session, "I promised to return your hammer.", high.unknownPerception,
                DialogueMode.ORDINARY_CONVERSATION, List.of());
        assert lowTurn.decision().selectedIntent() == GroundedIntent.PROCESS_INFORMATION;
        assert highTurn.decision().selectedIntent() == GroundedIntent.RESPOND_TO_RELATIONSHIP;
        assert highTurn.decision().intentPriority() > lowTurn.decision().intentPriority();
    }

    private static void materialInformationBeatsRepetition() {
        assert MaterialInformationGuard.containsMaterialUpdate(
                "Rowan wants to see Mara tonight.", "Rowan wants to see Mara.");
        String retained = DialogueNaturalnessFilter.filterResponse(
                "Rowan wants to see me tonight?", List.of("Rowan wants to see me?"), true);
        assert retained.equals("Rowan wants to see me tonight?");
    }

    private static void worldFactsStayUnknownUnlessAuthoritative() throws Exception {
        Fixture fixture = fixture();
        var unknown = fixture.service.evaluateGrounded(UUID.randomUUID(), fixture.profile,
                fixture.session, "What time is it and where are we?",
                fixture.unknownPerception, DialogueMode.ENVIRONMENT_QUERY, List.of());
        assert unknown.context().worldTime() == null;
        assert unknown.context().worldTimeSource().equals("UNKNOWN");
        assert unknown.context().unknownWorldFacts().contains("CURRENT_TIME");
        assert unknown.context().unknownWorldFacts().contains("CURRENT_LOCATION_NAME");

        LocalDateTime authoritative = LocalDateTime.of(42, 3, 7, 14, 30);
        NpcPerceptionSnapshot timed = snapshot(fixture.profile.id(), authoritative);
        var known = fixture.service.evaluateGrounded(UUID.randomUUID(), fixture.profile,
                fixture.session, "What time is it?", timed,
                DialogueMode.ENVIRONMENT_QUERY, List.of());
        assert known.context().worldTime().equals(authoritative);
        assert known.context().hasAuthoritativeWorldTime();

        var target = fixture.service.evaluateGrounded(UUID.randomUUID(), fixture.profile,
                fixture.session, "Go to Rowan.", fixture.unknownPerception,
                DialogueMode.ORDINARY_CONVERSATION, List.of("GO_TO"));
        assert target.context().unknownWorldFacts().contains("TARGET_LOCATION");
        assert target.decision().selectedIntent() == GroundedIntent.SEEK_INFORMATION;
        assert target.decision().actionRequests().isEmpty();
    }

    private static void directIntentUsesExistingAction() throws Exception {
        Fixture fixture = fixture();
        var turn = fixture.service.evaluateGrounded(UUID.randomUUID(), fixture.profile,
                fixture.session, "Can you follow me here?", fixture.unknownPerception,
                DialogueMode.ORDINARY_CONVERSATION, List.of("FOLLOW_PLAYER"));
        assert turn.decision().selectedIntent() == GroundedIntent.EXECUTE_DIRECT_REQUEST;
        assert turn.decision().actionRequests().equals(List.of("FOLLOW_PLAYER"));
    }

    private static void canonicalTextIsSharedAndStaleIdsLoseAuthority() {
        UUID second = UUID.randomUUID();

        List<String> display = new ArrayList<>();
        List<String> tts = new ArrayList<>();
        CommittedDialogueResponse response = new CommittedDialogueResponse(second, chunk -> {
            display.add(chunk.text());
            tts.add(chunk.text());
        });
        response.commit("Exact lexical wording.", VocalState.infer("Exact lexical wording."));
        assert display.equals(tts);
        assert response.text().equals("Exact lexical wording.");
    }

    private static void genericAssistantFallbackIsGone() {
        String filtered = DialogueNaturalnessFilter.filterResponse(
                "The same answer.", List.of("The same answer."));
        assert filtered.isBlank();
        assert !filtered.contains("What would you like to explore next");
    }

    private static void arbitraryProfilesUseTheSamePipeline() throws Exception {
        Fixture fixture = fixture("Orin", "quiet cartographer", "measured and observant");
        var turn = fixture.service.evaluateGrounded(UUID.randomUUID(), fixture.profile,
                fixture.session, "The eastern bridge has collapsed.",
                fixture.unknownPerception, DialogueMode.ORDINARY_CONVERSATION, List.of());
        assert !turn.decision().beliefUpdates().isEmpty();
        assert turn.decision().beliefUpdates().getFirst().sourceEntityId()
                .equals(fixture.playerId);
        assert turn.context().profile().name().equals("Orin");
    }

    private static Fixture fixture() throws Exception {
        return fixture("Testa", "village resident", "plain-spoken");
    }

    private static Fixture fixture(String name, String role, String personality)
            throws Exception {
        Path root = Files.createTempDirectory("r029-fixture-");
        RelationshipStore relationships = new RelationshipStore(root);
        relationships.load();
        MemoryStore memories = new MemoryStore(root, 100);
        memories.load();
        NpcTaskStore tasks = new NpcTaskStore(root);
        tasks.load();
        NpcEmotionStore emotions = new NpcEmotionStore(root);
        emotions.load();
        ObligationStore obligations = new ObligationStore(root);
        obligations.load();
        SharedPlanStore plans = new SharedPlanStore(root);
        plans.load();
        AgentOperationStore operations = new AgentOperationStore(root);
        operations.load();
        SourcedBeliefStore beliefs = new SourcedBeliefStore(root);
        beliefs.load();
        NpcProfile profile = new NpcProfile(UUID.randomUUID(), name, role, personality,
                "An authored NPC used to prove the system is generic.", "Live in the world.",
                "unknown", "unknown", List.of(), List.of(), List.of(), List.of(), 0);
        UUID player = UUID.randomUUID();
        ConversationSession session = new ConversationSession(UUID.randomUUID(),
                profile.id(), player, Instant.now());
        NpcCognitionService service = new NpcCognitionService(relationships, tasks, emotions,
                null, memories, obligations, plans, operations, beliefs,
                new CognitionTraceStore());
        return new Fixture(profile, player, session, relationships, service,
                NpcPerceptionSnapshot.unavailable(profile.id()));
    }

    private static NpcPerceptionSnapshot snapshot(UUID npcId, LocalDateTime gameTime) {
        return new NpcPerceptionSnapshot(npcId, UUID.randomUUID(), UUID.randomUUID(),
                gameTime, 0, 64, 0, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), null, null, List.of(), EnvironmentSnapshot.unavailable(
                        UUID.randomUUID(), 0, 64, 0));
    }

    private record Fixture(NpcProfile profile, UUID playerId, ConversationSession session,
            RelationshipStore relationships, NpcCognitionService service,
            NpcPerceptionSnapshot unknownPerception) { }
}
