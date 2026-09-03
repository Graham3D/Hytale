package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.config.FrameworkConfig;
import com.inigmasgames.persistentnpcs.conversation.ConversationContextBuilder;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.conversation.ConversationService;
import com.inigmasgames.persistentnpcs.conversation.MinimalWorldContext;
import com.inigmasgames.persistentnpcs.llm.OpenAiCompatibleProvider;
import com.inigmasgames.persistentnpcs.memory.MemoryRecord;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.perception.NpcPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.perception.PerceivedItem;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Optional real-endpoint probe run after deterministic tests. */
public final class RealLocalLlmBenchmark {
    private RealLocalLlmBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        String endpoint = environmentOrDefault("PERSISTENT_NPC_LLM_ENDPOINT",
                "http://127.0.0.1:11434/v1/chat/completions");
        String model = environmentOrDefault("PERSISTENT_NPC_LLM_MODEL",
                "nemotron-3-nano:4b");
        FrameworkConfig config = new FrameworkConfig(endpoint, model, "",
                1500, 12000, 0.2, 96, 600, 0, 10, 60,
                true, 60000, 15000, "none");
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(config);
        var status = provider.checkStatus().join();
        if (!status.reachable()
                || !status.reason().startsWith("Connected successfully")) {
            System.out.println("Real local-model benchmark unavailable: endpoint=" + endpoint
                    + " model=" + model + " reason=" + status.reason());
            return;
        }

        Path directory = Files.createTempDirectory("persistent-npc-grounding-");
        try {
            UUID npcId = UUID.fromString("3f84ec9e-37c5-4f11-9a74-106cd3bc04da");
            UUID playerId = UUID.randomUUID();
            RelationshipStore relationships = new RelationshipStore(directory);
            relationships.load();
            MemoryStore memories = new MemoryStore(directory, 20);
            memories.load();
            memories.append(new MemoryRecord(UUID.randomUUID(), npcId, playerId,
                    Instant.now(), 0.35,
                    "Mara replied: Morning. What brings you to the forge?"));
            NpcProfile profile = new NpcProfile(npcId, "Mara",
                    "Villager with blacksmith training",
                    "Direct, observant, dryly funny, and attentive.",
                    "Mara has practical blacksmith training as background.",
                    "Listen and respond to the current situation.", "Village room",
                    "Village workshop", List.of("honest people"),
                    List.of("being ignored"), List.of("BLACKSMITH"),
                    List.of("FOLLOW_PLAYER", "INSPECT_ITEM"), 5).validated();
            ConversationContextBuilder builder = new ConversationContextBuilder(
                    relationships, memories, 4);
            ConversationSession session = new ConversationSession(
                    UUID.randomUUID(), npcId, playerId, Instant.now());
            NpcPerceptionSnapshot heldSword = new NpcPerceptionSnapshot(
                    npcId, UUID.randomUUID(), UUID.randomUUID(), null,
                    10, 64, 10, List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of(),
                    2,
                    new PerceivedItem(null, "Weapon_Sword_Iron", "Iron Sword",
                            1, 100, 100, "{}", 0),
                    List.of());
            var requests = List.of(
                    builder.build(session, profile, "Hello Mara, how are you?",
                            new MinimalWorldContext("default", 10, 64, 10),
                            NpcPerceptionSnapshot.unavailable(npcId), List.of()),
                    builder.build(session, profile, "What am I holding?",
                            new MinimalWorldContext("default", 10, 64, 10),
                            heldSword, List.of()));
            for (int index = 0; index < requests.size(); index++) {
            AtomicInteger deltas = new AtomicInteger();
            var result = provider.generateResponse(
                    requests.get(index), ignored -> deltas.incrementAndGet()).join();
            if (result.text().isBlank()) {
                throw new AssertionError("Real local response was blank");
            }
            String normalized = result.text().toLowerCase(java.util.Locale.ROOT);
            if (index == 0 && (normalized.contains("forge")
                    || normalized.contains("blacksmith") || normalized.contains("weapon"))) {
                throw new AssertionError("Greeting was not grounded: " + result.text());
            }
            if (index == 1 && !normalized.contains("sword")) {
                throw new AssertionError("Held item was not identified: " + result.text());
            }
            System.out.printf("Real local-model message %d latency: requestStart=%s TTFT=%dms "
                            + "completion=%dms streaming=%s deltas=%d model=%s response=%s%n",
                    index + 1, result.latency().requestStartedAt(),
                    result.latency().timeToFirstTokenMillis(),
                    result.latency().completionMillis(), result.latency().streaming(),
                    deltas.get(), model, result.text().replaceAll("\\s+", " ").strip());
            }

            ConversationService conversation = new ConversationService(
                    builder, provider, relationships, memories, 1200, ignored -> { });
            ConversationSession nameSession = new ConversationSession(
                    UUID.randomUUID(), npcId, playerId, Instant.now());
            conversation.converse(nameSession, profile, "My name is Graham.",
                    new MinimalWorldContext("Hytale", 10, 64, 10)).join();
            var immediate = conversation.converse(nameSession, profile,
                    "What is my name?",
                    new MinimalWorldContext("Hytale", 10, 64, 10)).join();
            if (!immediate.dialogue().toLowerCase(java.util.Locale.ROOT)
                    .contains("graham")) {
                throw new AssertionError("Immediate name recall failed: "
                        + immediate.dialogue());
            }

            MemoryStore reloadedMemories = new MemoryStore(directory, 20);
            reloadedMemories.load();
            ConversationContextBuilder reloadedBuilder = new ConversationContextBuilder(
                    relationships, reloadedMemories, 4);
            ConversationService afterRestart = new ConversationService(
                    reloadedBuilder, provider, relationships, reloadedMemories,
                    1200, ignored -> { });
            ConversationSession restartedSession = new ConversationSession(
                    UUID.randomUUID(), npcId, playerId, Instant.now());
            var persistent = afterRestart.converse(restartedSession, profile,
                    "What is my name?",
                    new MinimalWorldContext("Hytale", 10, 64, 10)).join();
            if (!persistent.dialogue().toLowerCase(java.util.Locale.ROOT)
                    .contains("graham")) {
                throw new AssertionError("Persistent name recall failed: "
                        + persistent.dialogue());
            }
            System.out.println("Real local name recall: immediate=" + immediate.dialogue()
                    + " persistentAfterReload=" + persistent.dialogue());
            conversation.shutdown();
            afterRestart.shutdown();
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
