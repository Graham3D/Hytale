package com.inigmasgames.persistentnpcs;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.action.NpcActionContext;
import com.inigmasgames.persistentnpcs.action.NpcActionRegistry;
import com.inigmasgames.persistentnpcs.action.NpcActionRequest;
import com.inigmasgames.persistentnpcs.autonomy.AgentOperationStore;
import com.inigmasgames.persistentnpcs.cognition.CognitionTraceStore;
import com.inigmasgames.persistentnpcs.cognition.GroundedIntent;
import com.inigmasgames.persistentnpcs.cognition.NpcCognitionService;
import com.inigmasgames.persistentnpcs.cognition.NpcEmotionStore;
import com.inigmasgames.persistentnpcs.cognition.SourcedBeliefStore;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.conversation.DialogueMode;
import com.inigmasgames.persistentnpcs.economy.ObligationStore;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.perception.KnownNpcLocationStatus;
import com.inigmasgames.persistentnpcs.perception.KnownNpcLocatorResult;
import com.inigmasgames.persistentnpcs.perception.KnownNpcLocatorService;
import com.inigmasgames.persistentnpcs.perception.NpcPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.perception.RawPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.perception.SemanticPerceptionNormalizer;
import com.inigmasgames.persistentnpcs.plan.SharedPlanStore;
import com.inigmasgames.persistentnpcs.profile.AuthoredNpcRelationship;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import com.inigmasgames.persistentnpcs.social.KnownNpcGuideCoordinator;
import com.inigmasgames.persistentnpcs.task.NpcTask;
import com.inigmasgames.persistentnpcs.task.NpcTaskState;
import com.inigmasgames.persistentnpcs.task.NpcTaskStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.joml.Vector3d;

public final class R031KnownNpcGuideTest {
    private R031KnownNpcGuideTest() { }

    public static void main(String[] args) throws Exception {
        authoredRelationshipsGateSocialKnowledge();
        semanticLocatorNeverExposesEngineDetails();
        locatorDrivesGroundedIntentFlow();
        acceptedGuidePersistsExistingOperationPlanAndTask();
        suspendedMovementResumesAfterGuide();
        System.out.println("R031 relationship locator and guide tests passed.");
    }

    private static void authoredRelationshipsGateSocialKnowledge() throws Exception {
        Fixture fixture = fixture(true);
        assert fixture.relationships.knows(fixture.speaker.id(), fixture.target.id());
        var record = fixture.relationships.get(fixture.speaker.id(), fixture.target.id())
                .orElseThrow();
        assert record.knowsEntity();
        assert record.relationshipType().equals("FRIEND");
        assert record.familiarity() >= 75;

        Fixture stranger = fixture(false);
        assert !stranger.relationships.knows(stranger.speaker.id(), stranger.target.id());
    }

    private static void semanticLocatorNeverExposesEngineDetails() {
        UUID target = UUID.randomUUID();
        KnownNpcLocatorResult result = new KnownNpcLocatorResult(
                KnownNpcLocationStatus.FOUND, target, "Rowan", "a short walk away",
                "northeast", "within the currently loaded area", true, false);
        String semantic = result.semanticBlock();
        assert semantic.contains("Rowan");
        assert semantic.contains("northeast");
        assert !semantic.contains(target.toString());
        assert !semantic.matches(".*-?\\d+\\.\\d+[, ]+-?\\d+\\.\\d+.*");
        assert KnownNpcLocatorService.distanceBand(499).equals("near the edge of the area");
        assert KnownNpcLocatorService.direction(new Vector3d(1, 0, 1))
                .equals("northeast");
    }

    private static void locatorDrivesGroundedIntentFlow() throws Exception {
        Fixture fixture = fixture(true);
        UUID response = UUID.randomUUID();
        RawPerceptionSnapshot raw = RawPerceptionSnapshot.fromLegacy(
                response, fixture.perception);
        var base = new SemanticPerceptionNormalizer().normalize(raw, fixture.speaker,
                "Where is Rowan?");
        var found = base.withKnownNpcLocator(new KnownNpcLocatorResult(
                KnownNpcLocationStatus.FOUND, fixture.target.id(), fixture.target.name(),
                "a short walk away", "east", "within the currently loaded area",
                true, false));
        var offer = fixture.cognition.evaluateGrounded(response, fixture.speaker,
                fixture.session, "Where is Rowan?", raw, found,
                DialogueMode.ORDINARY_CONVERSATION, List.of());
        assert offer.decision().selectedIntent() == GroundedIntent.OFFER_GUIDE_TO_NPC;
        assert offer.decision().beliefUpdates().stream().anyMatch(belief ->
                belief.predicate().equals("KNOWN_NPC_LOCATION_FOUND"));
        assert fixture.session.pendingGuideOffer() != null;

        UUID acceptedResponse = UUID.randomUUID();
        RawPerceptionSnapshot acceptedRaw = RawPerceptionSnapshot.fromLegacy(
                acceptedResponse, fixture.perception);
        var acceptedSemantic = new SemanticPerceptionNormalizer().normalize(
                acceptedRaw, fixture.speaker, "Yes").withKnownNpcLocator(
                        new KnownNpcLocatorResult(KnownNpcLocationStatus.FOUND,
                                fixture.target.id(), fixture.target.name(),
                                "a short walk away", "east",
                                "within the currently loaded area", true, true));
        var guide = fixture.cognition.evaluateGrounded(acceptedResponse, fixture.speaker,
                fixture.session, "Yes", acceptedRaw, acceptedSemantic,
                DialogueMode.ORDINARY_CONVERSATION, List.of());
        assert guide.decision().selectedIntent() == GroundedIntent.GUIDE_PLAYER_TO_NPC;
        assert guide.decision().actionRequests().equals(List.of("GUIDE_PLAYER_TO_NPC"));

        Fixture stranger = fixture(false);
        UUID unknownResponse = UUID.randomUUID();
        RawPerceptionSnapshot unknownRaw = RawPerceptionSnapshot.fromLegacy(
                unknownResponse, stranger.perception);
        var unknownSemantic = new SemanticPerceptionNormalizer().normalize(
                unknownRaw, stranger.speaker, "Where is Rowan?").withKnownNpcLocator(
                        new KnownNpcLocatorResult(KnownNpcLocationStatus.UNKNOWN_RELATIONSHIP,
                                stranger.target.id(), stranger.target.name(),
                                "unknown distance", "unknown direction", "location unknown",
                                false, false));
        var unable = stranger.cognition.evaluateGrounded(unknownResponse, stranger.speaker,
                stranger.session, "Where is Rowan?", unknownRaw, unknownSemantic,
                DialogueMode.ORDINARY_CONVERSATION, List.of());
        assert unable.decision().selectedIntent() == GroundedIntent.REPORT_UNABLE_TO_LOCATE;
    }

    private static void acceptedGuidePersistsExistingOperationPlanAndTask() throws Exception {
        Fixture fixture = fixture(true);
        NpcActionRegistry registry = new NpcActionRegistry();
        new KnownNpcGuideCoordinator(fixture.relationships, fixture.operations,
                fixture.plans, fixture.tasks, fixture.memories).register(registry);
        KnownNpcLocatorResult locator = new KnownNpcLocatorResult(
                KnownNpcLocationStatus.FOUND, fixture.target.id(), fixture.target.name(),
                "a short walk away", "east", "within the currently loaded area", true, true);
        NpcActionContext context = new NpcActionContext(fixture.speaker, fixture.session,
                fixture.perception, "Yes, lead me there.", locator);
        JsonObject values = new JsonObject();
        values.addProperty("targetName", fixture.target.name());
        var result = registry.execute(new NpcActionRequest(
                KnownNpcGuideCoordinator.ACTION_ID, values, "test"), context).join();
        assert result.success() : result;
        NpcTask guide = fixture.tasks.activeFor(fixture.speaker.id()).stream()
                .filter(task -> task.type().equals("GUIDE_PLAYER_TO_NPC"))
                .findFirst().orElseThrow();
        assert guide.targetX() == null && guide.targetY() == null && guide.targetZ() == null;
        assert guide.data().get("targetProfileId").equals(fixture.target.id().toString());
        assert fixture.operations.latestFor(fixture.speaker.id(), "GUIDE_PLAYER_TO_NPC")
                .orElseThrow().status().equals("IN_PROGRESS");
        assert fixture.plans.activeFor(fixture.speaker.id()).stream()
                .anyMatch(plan -> plan.purpose().contains(fixture.target.name()));
    }

    private static void suspendedMovementResumesAfterGuide() throws Exception {
        Path root = Files.createTempDirectory("r031-tasks-");
        NpcTaskStore tasks = new NpcTaskStore(root);
        tasks.load();
        UUID npc = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        UUID guide = UUID.randomUUID();
        NpcTask prior = new NpcTask(UUID.randomUUID(), npc, player, "GO_TO",
                UUID.randomUUID(), 1.0, 2.0, 3.0, null, "Previous work",
                NpcTaskState.TRAVELING, Instant.now(), null, Map.of());
        tasks.put(prior);
        assert tasks.suspendMovementTasks(npc, guide, "guide") == 1;
        assert tasks.activeFor(npc).isEmpty();
        assert tasks.resumeAfterGuide(npc, guide) == 1;
        assert tasks.activeFor(npc).getFirst().state() == NpcTaskState.TRAVELING;
    }

    private static Fixture fixture(boolean authoredRelationship) throws Exception {
        Path root = Files.createTempDirectory("r031-fixture-");
        NpcProfileRegistry profiles = new NpcProfileRegistry(new ProfileRepository(root));
        NpcProfile target = profile(UUID.randomUUID(), "Rowan");
        NpcProfile speaker = profile(UUID.randomUUID(), "Elara");
        if (authoredRelationship) {
            speaker = speaker.withRelationships(List.of(new AuthoredNpcRelationship(
                    target.id().toString(), target.name(), "FRIEND", null, 0.7, 0.8,
                    0.7, 0.0, 0.0, 0.0, "Elara knows Rowan well.")));
        }
        profiles.register(speaker);
        profiles.register(target);
        RelationshipStore relationships = new RelationshipStore(root);
        relationships.load();
        relationships.importAuthored(List.of(speaker), profiles);
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
        UUID player = UUID.randomUUID();
        ConversationSession session = new ConversationSession(UUID.randomUUID(),
                speaker.id(), player, Instant.now());
        NpcCognitionService cognition = new NpcCognitionService(relationships, tasks,
                emotions, profiles, memories, obligations, plans, operations, beliefs,
                new CognitionTraceStore());
        NpcPerceptionSnapshot perception = NpcPerceptionSnapshot.unavailable(speaker.id());
        return new Fixture(speaker, target, player, session, profiles, relationships,
                memories, tasks, plans, operations, cognition, perception);
    }

    private static NpcProfile profile(UUID id, String name) {
        return new NpcProfile(id, name, "Village resident", "Practical and observant",
                "An authored resident.", "Live responsibly.", "village", "village",
                List.of(), List.of(), List.of(), List.of(), 0).validated();
    }

    private record Fixture(NpcProfile speaker, NpcProfile target, UUID playerId,
            ConversationSession session, NpcProfileRegistry profiles,
            RelationshipStore relationships, MemoryStore memories, NpcTaskStore tasks,
            SharedPlanStore plans, AgentOperationStore operations,
            NpcCognitionService cognition, NpcPerceptionSnapshot perception) { }
}
