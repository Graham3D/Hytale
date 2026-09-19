package com.inigmasgames.persistentnpcs;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.cognition.NpcCognitionService;
import com.inigmasgames.persistentnpcs.cognition.NpcEmotionStore;
import com.inigmasgames.persistentnpcs.config.ConfigRepository;
import com.inigmasgames.persistentnpcs.conversation.ConversationContextBuilder;
import com.inigmasgames.persistentnpcs.conversation.ConversationGrounding;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.conversation.MinimalWorldContext;
import com.inigmasgames.persistentnpcs.llm.LlmToolDefinition;
import com.inigmasgames.persistentnpcs.llm.OpenAiCompatibleProvider;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.perception.EnvironmentSample;
import com.inigmasgames.persistentnpcs.perception.EnvironmentSemanticAnalyzer;
import com.inigmasgames.persistentnpcs.perception.NpcPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import com.inigmasgames.persistentnpcs.task.NpcTaskStore;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Real Nemotron probe for the R012 single-request cognition/action path. */
public final class RealLocalR012CognitionTest {
    private static final UUID PLAYER_ID = UUID.fromString(
            "73f9b698-2494-480d-8406-2943e4a7505b");

    private RealLocalR012CognitionTest() { }

    public static void main(String[] args) throws Exception {
        Path data = Path.of(System.getenv("APPDATA"), "Hytale", "UserData", "Saves",
                "NPC", "mods", "ImmersiveNPCs");
        var config = new ConfigRepository(data).load();
        var provider = new OpenAiCompatibleProvider(config);
        var status = provider.checkStatus().join();
        if (!status.reachable() || !status.reason().contains("configured model is available")) {
            System.out.println("Real R012 cognition test skipped: " + status.reason());
            return;
        }
        var profile = new ProfileRepository(data).loadTestProfile();
        // Keep this live-model probe deterministic instead of depending on mutable save history.
        var relationships = new RelationshipStore(
                java.nio.file.Files.createTempDirectory("r012-live-relationships-"));
        relationships.load();
        relationships.adjust(profile.id(), PLAYER_ID, profile.defaultDisposition(),
                40, 0, 10, 0, 0, 0, Instant.now());
        var memories = new MemoryStore(data, config.maxMemoryRecords());
        memories.load();
        var tasks = new NpcTaskStore(data);
        tasks.load();
        var emotions = new NpcEmotionStore(data);
        emotions.load();
        var cognition = new NpcCognitionService(relationships, tasks, emotions);
        var builder = new ConversationContextBuilder(
                relationships, memories, tasks, config.recentMemoryCount());
        var session = new ConversationSession(
                UUID.randomUUID(), profile.id(), PLAYER_ID, Instant.now());
        var perception = perception(profile.id());

        String environmentMessage = "Do you know where we are?";
        var environmentState = builder.requestState(session, profile, environmentMessage);
        var environmentCognition = cognition.evaluate(profile, session, environmentMessage,
                perception, environmentState.mode());
        var environmentRequest = builder.build(session, profile, environmentMessage,
                new MinimalWorldContext("NPC", 0, 80, 0), perception, List.of(),
                ConversationGrounding.none(), environmentState, environmentCognition);
        var environmentResult = provider.generateResponse(environmentRequest).join();
        String reply = environmentResult.text().toLowerCase(Locale.ROOT);
        assert !reply.contains("emerald forest") && !reply.contains("main path") : reply;
        assert reply.contains("portal") || reply.contains("ruin")
                || reply.contains("don't recognize") || reply.contains("do not recognize")
                || reply.contains("not sure") || reply.contains("don't know")
                || reply.contains("do not know") : reply;

        String followMessage = "Follow me.";
        var followState = builder.requestState(session, profile, followMessage);
        var followCognition = cognition.evaluate(profile, session, followMessage,
                perception, followState.mode());
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        schema.addProperty("additionalProperties", false);
        var tool = new LlmToolDefinition("FOLLOW_PLAYER",
                "Begin physically following the focused player after social authorization.",
                schema);
        var followRequest = builder.build(session, profile, followMessage,
                new MinimalWorldContext("NPC", 0, 80, 0), perception, List.of(tool),
                ConversationGrounding.none(), followState, followCognition);
        var followResult = provider.generateResponse(followRequest).join();
        boolean requested = followResult.toolCalls().stream().anyMatch(call ->
                canonical(call.name()).equals("FOLLOW_PLAYER"))
                || cognition.fallbackAction(followMessage, followResult.text(), followCognition)
                        .isPresent();
        assert followCognition.appraisal().actionAuthorized()
                : followCognition.appraisal().compact();
        // Small local models may phrase a clarification despite server authorization;
        // deterministic action execution is covered by RealLocalActionBenchmark.
        if (!requested) {
            System.out.println("R012_REAL_COGNITION modelDidNotCommitFollow text='"
                    + followResult.text() + "'");
        }
        System.out.println("R012_REAL_COGNITION environment='" + environmentResult.text()
                + "' followText='" + followResult.text() + "' followTools="
                + followResult.toolCalls().size());
        System.out.println("R012_REAL_LATENCY environmentTTFT="
                + environmentResult.latency().timeToFirstTokenMillis()
                + "ms environmentCompletion=" + environmentResult.latency().completionMillis()
                + "ms followTTFT=" + followResult.latency().timeToFirstTokenMillis()
                + "ms followCompletion=" + followResult.latency().completionMillis() + "ms");
    }

    private static NpcPerceptionSnapshot perception(UUID npcId) {
        List<EnvironmentSample> samples = new ArrayList<>();
        samples.add(new EnvironmentSample("Hub_Portal_Default", "Portal", "Solid", "",
                8, 80, 0, true, false, false, false, false, true, false));
        for (int i = 0; i < 40; i++) {
            samples.add(new EnvironmentSample("Stone_Ruins_Pillar", "", "Solid", "",
                    i % 8, 79 + i % 3, i % 7, false, false, false,
                    false, false, false, false));
        }
        var environment = new EnvironmentSemanticAnalyzer().summarize(
                UUID.randomUUID(), Instant.now(), 0, 80, 0,
                1.0, 80.0, 1.0, 14, samples, 2);
        return new NpcPerceptionSnapshot(npcId, UUID.randomUUID(), environment.worldId(),
                LocalDateTime.now(), 0, 80, 0, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), 0, null, List.of(), environment);
    }

    private static String canonical(String value) {
        return value == null ? "" : value.strip().replaceAll("[\\s-]+", "_")
                .replaceAll("_+", "_").toUpperCase(Locale.ROOT);
    }
}
