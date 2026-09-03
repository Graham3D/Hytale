package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.config.FrameworkConfig;
import com.inigmasgames.persistentnpcs.config.ConfigRepository;
import com.inigmasgames.persistentnpcs.conversation.ConversationContextBuilder;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.conversation.MinimalWorldContext;
import com.inigmasgames.persistentnpcs.llm.OpenAiCompatibleProvider;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.perception.NpcPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.perception.PerceivedItem;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import com.inigmasgames.persistentnpcs.task.NpcTaskStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Production-sized real-model dialogue regression probe. */
public final class RealLocalDialogueRegressionTest {
    private static final UUID PLAYER_ID = UUID.fromString(
            "73f9b698-2494-480d-8406-2943e4a7505b");

    private RealLocalDialogueRegressionTest() { }

    public static void main(String[] args) {
        Path data = Path.of(System.getenv("APPDATA"), "Hytale", "UserData", "Saves",
                "NPC", "mods", "ImmersiveNPCs");
        FrameworkConfig config = new ConfigRepository(data).load();
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                config, System.out::println);
        var status = provider.checkStatus().join();
        if (!status.reachable() || !status.reason().contains("configured model is available")) {
            System.out.println("Real dialogue regression test skipped: " + status.reason());
            return;
        }
        var profile = new ProfileRepository(data).loadTestProfile();
        RelationshipStore relationships = new RelationshipStore(data);
        relationships.load();
        MemoryStore memories = new MemoryStore(data, config.maxMemoryRecords());
        memories.load();
        NpcTaskStore tasks = new NpcTaskStore(data);
        tasks.load();
        ConversationContextBuilder builder = new ConversationContextBuilder(
                relationships, memories, tasks, config.recentMemoryCount());
        ConversationSession session = new ConversationSession(
                UUID.randomUUID(), profile.id(), PLAYER_ID, Instant.now());
        NpcPerceptionSnapshot perception = new NpcPerceptionSnapshot(
                profile.id(), UUID.randomUUID(), UUID.randomUUID(), LocalDateTime.now(),
                10, 64, 10, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), 1,
                new PerceivedItem(null, "Weapon_Longsword_Adamantite_Saurian",
                        "Adamantite Saurian Longsword", 1, 100, 100, "{}", 0),
                List.of());
        String[] messages = {
                "Greetings", "How are you?", "Hello", "How has your day been?",
                "Nice to meet you.", "Is everything all right?",
                "What have you been thinking about?", "Tell me something simple.",
                "Are you comfortable here?", "Thank you for talking with me."
        };
        for (String message : messages) {
            var request = builder.buildCompact(session, profile, message,
                    new MinimalWorldContext("NPC", 10, 64, 10), perception, List.of());
            var result = provider.generateResponse(request).join();
            String dialogue = result.text() == null ? "" : result.text().strip();
            System.out.println("DIALOGUE_TEST player=" + message + " raw=" + dialogue);
            if (dialogue.isBlank() || dialogue.equalsIgnoreCase(profile.name())) {
                throw new AssertionError("Ordinary dialogue collapsed to NPC name: " + message
                        + " -> " + dialogue);
            }
            String normalized = dialogue.toLowerCase(java.util.Locale.ROOT);
            if (normalized.contains("forge") || normalized.contains("blade")
                    || normalized.contains("blacksmith")) {
                throw new AssertionError("Ordinary dialogue regressed into occupation fixation: "
                        + message + " -> " + dialogue);
            }
            session.appendTurn(message, dialogue, Instant.now());
        }
        var nameRequest = builder.buildCompact(session, profile, "What's your name?",
                new MinimalWorldContext("NPC", 10, 64, 10), perception, List.of());
        String name = provider.generateResponse(nameRequest).join().text();
        if (name == null || !name.toLowerCase(java.util.Locale.ROOT).contains("mara")) {
            throw new AssertionError("Name response did not identify Mara: " + name);
        }
        System.out.println("Real dialogue regression test passed; nameResponse=" + name);
    }
}
