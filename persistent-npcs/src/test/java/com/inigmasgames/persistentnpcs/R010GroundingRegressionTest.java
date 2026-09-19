package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.conversation.ConversationContextBuilder;
import com.inigmasgames.persistentnpcs.conversation.ConversationService;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.conversation.DialogueMode;
import com.inigmasgames.persistentnpcs.conversation.MinimalWorldContext;
import com.inigmasgames.persistentnpcs.llm.LlmLatency;
import com.inigmasgames.persistentnpcs.llm.LlmProvider;
import com.inigmasgames.persistentnpcs.llm.LlmProviderStatus;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.ConversationRateLimiter;
import com.inigmasgames.persistentnpcs.llm.LlmResult;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.quest.DynamicQuestStore;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import com.inigmasgames.persistentnpcs.task.NpcTask;
import com.inigmasgames.persistentnpcs.task.NpcTaskState;
import com.inigmasgames.persistentnpcs.task.NpcTaskStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public final class R010GroundingRegressionTest {
    private R010GroundingRegressionTest() { }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("immersive-ai-r010-");
        try {
            verifyNarrativeModesAndClaimValidation(directory);
            System.out.println("Immersive AI R010 grounding regression tests passed.");
        } finally {
            delete(directory);
        }
    }

    private static void verifyNarrativeModesAndClaimValidation(Path directory) {
        UUID npcId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        NpcProfile profile = new NpcProfile(npcId, "Mara", "Village resident",
                "Direct and attentive", "A grounded resident", "Talk naturally", "", "",
                List.of(), List.of(), List.of("CIVILIAN"),
                List.of("FOLLOW_PLAYER", "WAIT"), 0).validated();
        RelationshipStore relationships = new RelationshipStore(directory);
        MemoryStore memories = new MemoryStore(directory, 100);
        NpcTaskStore tasks = new NpcTaskStore(directory);
        DynamicQuestStore quests = new DynamicQuestStore(directory);
        relationships.load();
        memories.load();
        tasks.load();
        quests.load();
        ConversationContextBuilder builder = new ConversationContextBuilder(
                relationships, memories, tasks, quests, 6);
        List<LlmRequest> requests = new CopyOnWriteArrayList<>();
        List<String> serverLogs = new CopyOnWriteArrayList<>();
        List<String> streamedToPlayer = new CopyOnWriteArrayList<>();
        LlmProvider provider = scriptedProvider(requests);
        ConversationService service = new ConversationService(builder, provider,
                relationships, memories, null, null, 1200, serverLogs::add,
                new ConversationRateLimiter(100));
        ConversationSession session = new ConversationSession(
                UUID.randomUUID(), npcId, playerId, Instant.now());

        var story = service.converse(session, profile, "Can you tell me a story?",
                new MinimalWorldContext("Hytale", 1, 64, 2), streamedToPlayer::add).join();
        assert story.dialogueMode() == DialogueMode.FICTIONAL_STORY;
        assert story.dialogue().startsWith("Here's a fictional story:");
        assert streamedToPlayer.isEmpty()
                : "Unvalidated raw SSE must not reach Hytale chat: " + streamedToPlayer;
        assert tasks.all().isEmpty();
        assert quests.all().isEmpty();
        assert memories.recent(npcId, playerId, 10).isEmpty()
                : "Fictional story events must not enter persistent memory";
        assert session.recentConversationBlock("Mara", 6)
                .contains("[FICTIONAL_STORY; not current world state]");
        String storyPrompt = requests.getFirst().messages().getFirst().content();
        assert storyPrompt.contains("DIALOGUE_MODE=FICTIONAL_STORY");
        assert storyPrompt.contains("CURRENT_WORLD_STATE");
        assert storyPrompt.contains("PROFILE/BACKSTORY");
        assert storyPrompt.contains("PROPOSED_PLAN");
        assert storyPrompt.contains("VALIDATED_ACTIVE_TASK");
        assert storyPrompt.contains("VALIDATED_QUEST");
        assert storyPrompt.contains("No Director framing is injected");

        var idle = service.converse(session, profile, "What are you doing?",
                new MinimalWorldContext("Hytale", 1, 64, 2)).join();
        assert idle.dialogueMode() == DialogueMode.CURRENT_WORLD_STATE;
        assert idle.dialogue().equals("I'm idle right now.") : idle.dialogue();

        for (String question : List.of("What's on your mind?", "How does today feel?",
                "What would make an interesting adventure?")) {
            var answer = service.converse(session, profile, question,
                    new MinimalWorldContext("Hytale", 1, 64, 2)).join();
            String lower = answer.dialogue().toLowerCase(java.util.Locale.ROOT);
            assert !lower.contains("we're moving");
            assert !lower.contains("my post");
            assert !lower.contains("quest is underway");
            assert !lower.contains("air's dry");
        }
        assert tasks.all().isEmpty() && quests.all().isEmpty();

        tasks.put(new NpcTask(UUID.randomUUID(), npcId, playerId, "FOLLOW_PLAYER", worldId,
                1.0, 64.0, 2.0, Instant.now(), "Follow the requester",
                NpcTaskState.ACTIVE, Instant.now(), "", Map.of()));
        ConversationSession activeSession = new ConversationSession(
                UUID.randomUUID(), npcId, playerId, Instant.now());
        var active = service.converse(activeSession, profile, "What are you doing?",
                new MinimalWorldContext("Hytale", 1, 64, 2)).join();
        assert active.dialogueMode() == DialogueMode.VALIDATED_ACTIVE_TASK;
        assert active.dialogue().equals("I'm following you right now.") : active.dialogue();

        assert serverLogs.stream().anyMatch(log -> log.contains("dialogueMode=FICTIONAL_STORY")
                && log.contains("hasActiveTask=false")
                && log.contains("hasActiveQuest=false")
                && log.contains("directorContextIncluded=false")
                && log.contains("fictionalStoryMode=true")
                && log.contains("authoritativeLocation=unavailable")
                && log.contains("claimedCurrentAction=true"));
        assert serverLogs.stream().anyMatch(log -> log.contains("dialogueMode=VALIDATED_ACTIVE_TASK")
                && log.contains("hasActiveTask=true")
                && log.contains("claimedCurrentAction=true")
                && log.contains("rewritten=false")) : serverLogs;
        service.shutdown();
    }

    private static LlmProvider scriptedProvider(List<LlmRequest> requests) {
        return new LlmProvider() {
            @Override
            public CompletableFuture<LlmResult> generateResponse(LlmRequest request) {
                return generateResponse(request, ignored -> { });
            }

            @Override
            public CompletableFuture<LlmResult> generateResponse(
                    LlmRequest request, java.util.function.Consumer<String> tokenConsumer) {
                requests.add(request);
                String message = request.messages().getLast().content().toLowerCase(
                        java.util.Locale.ROOT);
                String reply;
                if (message.contains("story")) {
                    reply = "We're moving toward sunrise. The potion's stamina lasts just long enough.";
                } else if (message.contains("what are you doing")) {
                    boolean active = request.messages().getFirst().content()
                            .contains("follow player is active");
                    reply = active ? "I'm following you right now."
                            : "I'm watching you from my post.";
                } else if (message.contains("today feel")) {
                    reply = "The air's dry and the heat is rising.";
                } else if (message.contains("adventure")) {
                    reply = "The quest is underway.";
                } else {
                    reply = "We're moving toward the ridge now.";
                }
                tokenConsumer.accept(reply);
                return CompletableFuture.completedFuture(new LlmResult(reply,
                        new LlmLatency(Instant.now(), 1, 2, true)));
            }

            @Override
            public CompletableFuture<LlmProviderStatus> checkStatus() {
                return CompletableFuture.completedFuture(new LlmProviderStatus(
                        "test", "test", true, true, true, "test"));
            }

            @Override
            public String description() { return "R010 scripted grounding provider"; }
        };
    }

    private static void delete(Path directory) throws Exception {
        MemoryStore.flushAll();
        if (!Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception failure) {
                    throw new RuntimeException(failure);
                }
            });
        }
    }
}
