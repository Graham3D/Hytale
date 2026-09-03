package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.config.ConfigRepository;
import com.inigmasgames.persistentnpcs.conversation.ConversationContextBuilder;
import com.inigmasgames.persistentnpcs.conversation.ConversationGrounding;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.conversation.DialogueClaimValidator;
import com.inigmasgames.persistentnpcs.conversation.DialogueMode;
import com.inigmasgames.persistentnpcs.conversation.MinimalWorldContext;
import com.inigmasgames.persistentnpcs.llm.OpenAiCompatibleProvider;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.perception.EnvironmentSample;
import com.inigmasgames.persistentnpcs.perception.EnvironmentSemanticAnalyzer;
import com.inigmasgames.persistentnpcs.perception.EnvironmentSnapshot;
import com.inigmasgames.persistentnpcs.perception.NpcPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import com.inigmasgames.persistentnpcs.quest.DynamicQuestStore;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import com.inigmasgames.persistentnpcs.task.NpcTaskStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Real Nemotron checks using detached authoritative-world fixtures. */
public final class RealLocalR011EnvironmentTest {
    private RealLocalR011EnvironmentTest() { }

    public static void main(String[] args) throws Exception {
        Path production = Path.of(System.getenv("APPDATA"), "Hytale", "UserData", "Saves",
                "NPC", "mods", "ImmersiveNPCs");
        var config = new ConfigRepository(production).load();
        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(config);
        var status = provider.checkStatus().join();
        if (!status.reachable() || !status.reason().contains("configured model is available")) {
            System.out.println("Real R011 environment test skipped: " + status.reason());
            return;
        }
        Path temporary = Files.createTempDirectory("immersive-ai-real-r011-");
        try {
            var profile = new ProfileRepository(production).loadTestProfile();
            RelationshipStore relationships = new RelationshipStore(temporary);
            MemoryStore memories = new MemoryStore(temporary, 100);
            NpcTaskStore tasks = new NpcTaskStore(temporary);
            DynamicQuestStore quests = new DynamicQuestStore(temporary);
            relationships.load(); memories.load(); tasks.load(); quests.load();
            ConversationContextBuilder builder = new ConversationContextBuilder(
                    relationships, memories, tasks, quests, 6);
            DialogueClaimValidator validator = new DialogueClaimValidator();
            UUID playerId = UUID.randomUUID();

            EnvironmentSnapshot flat = flat();
            String flatAnswer = ask(provider, builder, validator, profile, playerId,
                    flat, "What do you see around us?");
            assert flatAnswer.toLowerCase().contains("grass") : flatAnswer;
            assertNone(flatAnswer, "forest", "village", "ruins", "river", "mountain");

            EnvironmentSnapshot crossroads = crossroads();
            String crossroadsAnswer = ask(provider, builder, validator, profile, playerId,
                    crossroads, "What do you see around us?");
            assert crossroadsAnswer.toLowerCase().contains("portal") : crossroadsAnswer;
            String portalAnswer = ask(provider, builder, validator, profile, playerId,
                    crossroads, "Do you see the portal?");
            assert portalAnswer.toLowerCase().contains("portal") : portalAnswer;

            String story = ask(provider, builder, validator, profile, playerId,
                    flat, "Tell me a story about a forest.");
            assert story.toLowerCase().contains("forest") : story;
            System.out.println("R011_REAL_FLAT final=" + flatAnswer);
            System.out.println("R011_REAL_CROSSROADS final=" + crossroadsAnswer);
            System.out.println("R011_REAL_PORTAL final=" + portalAnswer);
            System.out.println("R011_REAL_STORY final=" + story);
            System.out.println("Real R011 environment grounding passed.");
        } finally {
            delete(temporary);
        }
    }

    private static String ask(OpenAiCompatibleProvider provider,
            ConversationContextBuilder builder,
            DialogueClaimValidator validator,
            com.inigmasgames.persistentnpcs.profile.NpcProfile profile,
            UUID playerId,
            EnvironmentSnapshot environment,
            String playerMessage) {
        ConversationSession session = new ConversationSession(
                UUID.randomUUID(), profile.id(), playerId, Instant.now());
        NpcPerceptionSnapshot perception = new NpcPerceptionSnapshot(profile.id(),
                UUID.randomUUID(), environment.worldId(), LocalDateTime.now(),
                environment.npcX(), environment.npcY(), environment.npcZ(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                0, null, List.of(), environment);
        var state = builder.requestState(session, profile, playerMessage);
        var request = builder.build(session, profile, playerMessage,
                new MinimalWorldContext("fixture", (int) Math.floor(environment.npcX()),
                        (int) Math.floor(environment.npcY()),
                        (int) Math.floor(environment.npcZ())), perception, List.of(),
                ConversationGrounding.none(), state, null, true);
        var result = provider.generateResponse(request).join();
        String finalDialogue = validator.validate(state.mode(), playerMessage,
                result.text(), state, environment).dialogue();
        if (!com.inigmasgames.persistentnpcs.conversation.SpokenTextSafetyValidator
                .isSafe(finalDialogue)) {
            throw new AssertionError("R030 debug leakage reached environment dialogue: "
                    + finalDialogue);
        }
        System.out.println("R011_REAL_LATENCY query=" + playerMessage
                + " ttftMs=" + result.latency().timeToFirstTokenMillis()
                + " completionMs=" + result.latency().completionMillis());
        return finalDialogue;
    }

    private static EnvironmentSnapshot flat() {
        List<EnvironmentSample> samples = new ArrayList<>();
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                samples.add(sample("Grass_Block", x, 79, z));
            }
        }
        return new EnvironmentSemanticAnalyzer().summarize(UUID.randomUUID(), Instant.now(),
                0, 80, 0, 1.0, 80.0, 1.0, 14, samples, 3);
    }

    private static EnvironmentSnapshot crossroads() {
        List<EnvironmentSample> samples = new ArrayList<>();
        samples.add(new EnvironmentSample("Hub_Portal_Default", "Portal", "Solid",
                "Blocks/Miscellaneous/Platform_MagicInactive.blockymodel",
                8, 168, 0, true, false, false, false, false, true, false));
        for (int index = 0; index < 100; index++) {
            samples.add(sample(index % 8 == 0 ? "Stone_Ruins_Pillar" : "Stone_Brick_Wall",
                    (index % 12) - 5, 166 + index % 8, (index % 9) - 4));
        }
        for (int index = 0; index < 20; index++) {
            samples.add(sample("Moss_Vine", index % 5, 168 + index % 4, -(index % 4)));
        }
        return new EnvironmentSemanticAnalyzer().summarize(UUID.randomUUID(), Instant.now(),
                0, 168, 0, 0.0, 168.0, -2.0, 14, samples, 4);
    }

    private static EnvironmentSample sample(String id, double x, double y, double z) {
        return new EnvironmentSample(id, "", "Solid", "", x, y, z,
                false, false, false, false, false, false, false);
    }

    private static void assertNone(String value, String... forbidden) {
        String lower = value.toLowerCase();
        for (String term : forbidden) {
            if (lower.contains(term)) {
                throw new AssertionError("Unsupported category " + term + ": " + value);
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
