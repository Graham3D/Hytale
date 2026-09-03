package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.config.ConfigRepository;
import com.inigmasgames.persistentnpcs.conversation.ConversationContextBuilder;
import com.inigmasgames.persistentnpcs.conversation.ConversationService;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.conversation.DialogueMode;
import com.inigmasgames.persistentnpcs.conversation.MinimalWorldContext;
import com.inigmasgames.persistentnpcs.llm.ConversationRateLimiter;
import com.inigmasgames.persistentnpcs.llm.OpenAiCompatibleProvider;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import com.inigmasgames.persistentnpcs.quest.DynamicQuestStore;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import com.inigmasgames.persistentnpcs.task.NpcTaskStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Real Nemotron coverage for R010 story/current-scene separation. */
public final class RealLocalR010GroundingRegressionTest {
    private RealLocalR010GroundingRegressionTest() { }

    public static void main(String[] args) throws Exception {
        Path production = Path.of(System.getenv("APPDATA"), "Hytale", "UserData", "Saves",
                "NPC", "mods", "ImmersiveNPCs");
        var config = new ConfigRepository(production).load();
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(config);
        var status = provider.checkStatus().join();
        if (!status.reachable() || !status.reason().contains("configured model is available")) {
            System.out.println("Real R010 grounding regression skipped: " + status.reason());
            return;
        }
        Path temporary = Files.createTempDirectory("immersive-ai-real-r010-");
        try {
            var profile = new ProfileRepository(production).loadTestProfile();
            RelationshipStore relationships = new RelationshipStore(temporary);
            MemoryStore memories = new MemoryStore(temporary, 100);
            NpcTaskStore tasks = new NpcTaskStore(temporary);
            DynamicQuestStore quests = new DynamicQuestStore(temporary);
            relationships.load();
            memories.load();
            tasks.load();
            quests.load();
            ConversationContextBuilder builder = new ConversationContextBuilder(
                    relationships, memories, tasks, quests, 6);
            ConversationService service = new ConversationService(builder, provider,
                    relationships, memories, null, null, 1200, System.out::println,
                    new ConversationRateLimiter(30));
            ConversationSession session = new ConversationSession(UUID.randomUUID(),
                    profile.id(), UUID.randomUUID(), Instant.now());
            var story = service.converse(session, profile, "Can you tell me a story?",
                    new MinimalWorldContext("NPC", 4, 64, 7)).join();
            System.out.println("R010_REAL_STORY final=" + story.dialogue());
            if (story.dialogueMode() != DialogueMode.FICTIONAL_STORY
                    || !clearlyFictional(story.dialogue())) {
                throw new AssertionError("Story was not explicitly fictional: "
                        + story.dialogue());
            }
            if (!tasks.all().isEmpty() || !quests.all().isEmpty()
                    || !memories.recent(profile.id(), session.playerId(), 10).isEmpty()) {
                throw new AssertionError("Fictional story mutated persistent world state");
            }
            for (String question : List.of("What are you doing?", "What's on your mind?",
                    "What would make an interesting adventure?")) {
                var answer = service.converse(session, profile, question,
                        new MinimalWorldContext("NPC", 4, 64, 7)).join();
                System.out.println("R010_REAL_OPEN player=" + question
                        + " final=" + answer.dialogue());
                assertNoUnsupportedCurrentClaim(answer.dialogue());
            }
            service.shutdown();
            System.out.println("Real R010 grounding regression passed.");
        } finally {
            delete(temporary);
        }
    }

    private static boolean clearlyFictional(String dialogue) {
        String value = dialogue.toLowerCase(Locale.ROOT).strip();
        return value.startsWith("here's a fictional story") || value.startsWith("here is a fictional story")
                || value.startsWith("here's a story") || value.startsWith("once")
                || value.startsWith("in this story") || value.startsWith("imagine");
    }

    private static void assertNoUnsupportedCurrentClaim(String dialogue) {
        String value = dialogue.toLowerCase(Locale.ROOT);
        for (String forbidden : List.of("we're moving", "we are moving", "from my post",
                "quest is underway", "air's dry", "potion's stamina lasts")) {
            if (value.contains(forbidden)) {
                throw new AssertionError("Unsupported current-scene claim survived: "
                        + dialogue);
            }
        }
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
