package com.inigmasgames.persistentnpcs;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.action.NpcActionContext;
import com.inigmasgames.persistentnpcs.action.NpcActionRegistry;
import com.inigmasgames.persistentnpcs.action.NpcActionRequest;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.conversation.ConversationContextBuilder;
import com.inigmasgames.persistentnpcs.conversation.MinimalWorldContext;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.perception.EnvironmentSnapshot;
import com.inigmasgames.persistentnpcs.perception.NpcPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.plan.SharedPlan;
import com.inigmasgames.persistentnpcs.plan.SharedPlanCoordinator;
import com.inigmasgames.persistentnpcs.plan.SharedPlanStartMode;
import com.inigmasgames.persistentnpcs.plan.SharedPlanStatus;
import com.inigmasgames.persistentnpcs.plan.SharedPlanStore;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import com.inigmasgames.persistentnpcs.scene.NpcActivityState;
import com.inigmasgames.persistentnpcs.scene.NpcAssignmentState;
import com.inigmasgames.persistentnpcs.scene.NpcAssignmentStatus;
import com.inigmasgames.persistentnpcs.scene.NpcAssignmentStore;
import com.inigmasgames.persistentnpcs.scene.NpcConversationTrigger;
import com.inigmasgames.persistentnpcs.scene.NpcConversationTriggerService;
import com.inigmasgames.persistentnpcs.scene.NpcHearingSnapshot;
import com.inigmasgames.persistentnpcs.scene.NpcSpeechAttention;
import com.inigmasgames.persistentnpcs.scene.NpcSpeechEvent;
import com.inigmasgames.persistentnpcs.scene.NpcSpeechLocation;
import com.inigmasgames.persistentnpcs.scene.NpcSpeechRouter;
import com.inigmasgames.persistentnpcs.task.NpcTaskStore;
import com.inigmasgames.persistentnpcs.voice.TtsTextNormalizer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class R023NpcIntelligencePassTest {
    private R023NpcIntelligencePassTest() { }

    public static void main(String[] args) throws Exception {
        ttsNormalizationIsSpeechOnly();
        exactNpcSpeechIsEligibleAndBounded();
        overdueAssignmentTriggerIsFactualAndOneShot();
        sharedPlansPersistPurposeAndExecution();
        System.out.println("R023 NPC intelligence targeted tests passed.");
    }

    private static void ttsNormalizationIsSpeechOnly() {
        String display = "Hey! You're here again—what's up?";
        String normalized = TtsTextNormalizer.normalize(display);
        assert display.equals("Hey! You're here again—what's up?");
        assert normalized.equals("Hey! You're here again. What's up?") : normalized;
    }

    private static void exactNpcSpeechIsEligibleAndBounded() {
        UUID speaker = UUID.randomUUID();
        UUID listener = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();
        NpcSpeechLocation origin = new NpcSpeechLocation(world, 1, 2, 3);
        List<String> direct = new ArrayList<>();
        List<String> attention = new ArrayList<>();
        NpcSpeechRouter router = new NpcSpeechRouter(2, 30,
                id -> new NpcHearingSnapshot(id,
                        id.equals(listener) ? new NpcSpeechLocation(world, 3, 2, 3) : origin,
                        NpcActivityState.WORKING, true, conversation),
                (id, event) -> direct.add(id + ":" + event.text()), event -> { },
                new NpcSpeechAttention() {
                    @Override public boolean beginListening(UUID id, UUID from, UUID scene) {
                        attention.add("LISTENING_TO_NPC:" + id + ":" + from);
                        return true;
                    }
                    @Override public void finishListening(UUID id, UUID scene, boolean stopped) {
                        attention.add("RESUME:" + id);
                    }
                }, ignored -> { });
        NpcSpeechEvent first = new NpcSpeechEvent(speaker, listener, Set.of(listener),
                "You were due back at fourteen hundred.", conversation, origin,
                Instant.now(), "15:10", "REPRIMAND", "OVERDUE_ASSIGNMENT", "CALM", 8);
        assert router.route(first).delivered();
        assert direct.equals(List.of(listener + ":You were due back at fourteen hundred."));
        assert attention.getFirst().startsWith("LISTENING_TO_NPC:");
        NpcSpeechEvent second = new NpcSpeechEvent(listener, speaker, Set.of(speaker),
                "I know. The bridge was blocked, but I finished the errand.", conversation,
                origin, Instant.now(), "15:10", "RESPONSE", "OVERDUE_ASSIGNMENT",
                "UNEASY", 8);
        assert router.route(second).delivered();
        assert !router.route(first).delivered();
        router.finishConversation(conversation, Instant.now(), false);
        assert attention.stream().anyMatch(value -> value.startsWith("RESUME:"));

        NpcSpeechRouter blocked = new NpcSpeechRouter(2, 30,
                id -> new NpcHearingSnapshot(id, origin,
                        NpcActivityState.COMBAT, true, null),
                (id, event) -> { throw new AssertionError("combat listener received speech"); },
                event -> { }, NpcSpeechAttention.noOp(), ignored -> { });
        assert !blocked.route(new NpcSpeechEvent(speaker, listener, Set.of(listener),
                "Stop fighting and listen.", UUID.randomUUID(), origin, Instant.now(),
                "15:11", "ADDRESS", "TEST", "CALM", 8)).delivered();
    }

    private static void overdueAssignmentTriggerIsFactualAndOneShot() throws Exception {
        Path root = Files.createTempDirectory("r023-assignment-");
        NpcAssignmentStore store = new NpcAssignmentStore(root);
        store.load();
        UUID employer = UUID.randomUUID();
        UUID worker = UUID.randomUUID();
        Instant expected = Instant.parse("2026-08-27T14:00:00Z");
        Instant actual = Instant.parse("2026-08-27T15:10:00Z");
        NpcAssignmentState assignment = store.put(new NpcAssignmentState(
                UUID.randomUUID(), employer, worker, "deliver repaired tools",
                expected.minusSeconds(3600), expected, actual, NpcAssignmentStatus.COMPLETED,
                1, false, "Village workshop", "A blocked bridge delayed me"));
        NpcConversationTriggerService service = new NpcConversationTriggerService(store);
        NpcConversationTrigger trigger = service.overdueReturn(
                assignment.id(), true, true, actual).orElseThrow();
        assert trigger.speakerFacts().contains("Lateness: 70 minutes");
        assert trigger.speakerFacts().contains("Prior warnings: 1");
        assert !trigger.speakerFacts().contains("blocked bridge");
        assert trigger.listenerFacts().contains("blocked bridge");
        assert service.overdueReturn(assignment.id(), true, true, actual).isEmpty();
        service.markAddressed(assignment.id());
        assert store.get(assignment.id()).latenessAddressed();
    }

    private static void sharedPlansPersistPurposeAndExecution() throws Exception {
        Path root = Files.createTempDirectory("r023-plans-");
        SharedPlanStore plans = new SharedPlanStore(root);
        plans.load();
        NpcTaskStore tasks = new NpcTaskStore(root);
        tasks.load();
        MemoryStore memories = new MemoryStore(root, 100);
        memories.load();
        SharedPlanCoordinator coordinator = new SharedPlanCoordinator(plans, tasks, memories);
        NpcActionRegistry actions = new NpcActionRegistry();
        coordinator.register(actions);
        UUID npc = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        NpcProfile profile = new NpcProfile(npc, "Test NPC", "Traveler", "Careful",
                "A local traveler", "Act on grounded agreements", "", "",
                List.of(), List.of(), List.of(), List.of("SHARED_PLAN"), 0);
        ConversationSession session = new ConversationSession(
                UUID.randomUUID(), npc, player, Instant.now());
        NpcPerceptionSnapshot perception = new NpcPerceptionSnapshot(npc, UUID.randomUUID(),
                world, LocalDateTime.of(2026, 8, 27, 18, 30), 10, 20, 30,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                0, null, List.of(), EnvironmentSnapshot.unavailable(world, 10, 20, 30));
        NpcActionContext context = new NpcActionContext(
                profile, session, perception, "Want to go on a date at the tavern?");
        assert actions.toolsFor(context, context.playerMessage()).stream()
                .anyMatch(tool -> tool.function().name().equals("CREATE_SHARED_PLAN"));

        JsonObject immediate = new JsonObject();
        immediate.addProperty("purpose", "go to the tavern for a date");
        immediate.addProperty("startMode", "NOW");
        immediate.addProperty("leader", "PLAYER");
        immediate.addProperty("destination", "Tavern");
        assert actions.execute(new NpcActionRequest(
                "CREATE_SHARED_PLAN", immediate, "tool-1"), context).join().success();
        SharedPlan active = plans.all().getFirst();
        assert active.status() == SharedPlanStatus.ACTIVE;
        assert active.leader().equals(player);
        assert tasks.activeFor(npc).stream().anyMatch(task ->
                task.type().equals("FOLLOW_PLAYER")
                        && task.purpose().contains("date")
                        && task.data().get("sharedPlanId").equals(active.id().toString()));
        assert memories.recent(npc, player, 10).stream()
                .anyMatch(memory -> memory.summary().contains("date"));

        JsonObject scheduled = new JsonObject();
        scheduled.addProperty("purpose", "meet at the tavern to talk");
        scheduled.addProperty("startMode", "SCHEDULED");
        scheduled.addProperty("leader", "NPC");
        scheduled.addProperty("destination", "Tavern");
        scheduled.addProperty("hour", 19);
        scheduled.addProperty("minute", 0);
        assert actions.execute(new NpcActionRequest(
                "CREATE_SHARED_PLAN", scheduled, "tool-2"), context).join().success();
        assert plans.all().stream().anyMatch(plan ->
                plan.startMode() == SharedPlanStartMode.SCHEDULED
                        && plan.status() == SharedPlanStatus.SCHEDULED
                        && plan.scheduledTime() != null);

        SharedPlanStore reloaded = new SharedPlanStore(root);
        reloaded.load();
        assert reloaded.all().size() == 2;
        RelationshipStore relationships = new RelationshipStore(root);
        relationships.load();
        ConversationContextBuilder contextBuilder = new ConversationContextBuilder(
                relationships, memories, tasks, null, reloaded, 6);
        String prompt = contextBuilder.build(session, profile, "What are we doing?",
                new MinimalWorldContext("test", 10, 20, 30),
                perception, List.of()).messages().getFirst().content();
        assert prompt.contains("VALIDATED_SHARED_PLAN") : prompt;
        assert prompt.contains("go to the tavern for a date") : prompt;
        SharedPlan sanitized = new SharedPlan(UUID.randomUUID(), "walk together",
                List.of(player, npc), player, player, null, SharedPlanStartMode.NOW,
                null, SharedPlanStatus.ACTIVE, Instant.now(),
                Map.of("expressedPurpose", "see the village", "hiddenIntent", "steal"))
                .normalized();
        assert !sanitized.relevantContext().containsKey("hiddenIntent");
    }
}
