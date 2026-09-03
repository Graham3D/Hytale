package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.cognition.LatencyBudgetConfig;
import com.inigmasgames.persistentnpcs.cognition.ResponseLatencyStage;
import com.inigmasgames.persistentnpcs.cognition.ResponseLatencyTraceStore;
import com.inigmasgames.persistentnpcs.conversation.ConversationContextBuilder;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.conversation.MinimalWorldContext;
import com.inigmasgames.persistentnpcs.conversation.SpokenTextSafetyValidator;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.perception.EnvironmentSample;
import com.inigmasgames.persistentnpcs.perception.EnvironmentSemanticAnalyzer;
import com.inigmasgames.persistentnpcs.perception.NpcPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.perception.PerceivedEntity;
import com.inigmasgames.persistentnpcs.perception.PerceivedItem;
import com.inigmasgames.persistentnpcs.perception.RawPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.perception.SemanticPerceptionNormalizer;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class R030SemanticPerceptionLatencyTest {
    private R030SemanticPerceptionLatencyTest() { }

    public static void main(String[] args) throws Exception {
        semanticBoundaryExcludesRawDiagnostics();
        conversationalPromptUsesSemanticUnderstanding();
        spokenCommitRejectsDebugRepresentations();
        latencyTraceIsResponseScopedAndBudgeted();
        System.out.println("R030 semantic perception and latency tests passed.");
    }

    private static void semanticBoundaryExcludesRawDiagnostics() {
        Fixture fixture = fixture();
        var semantic = new SemanticPerceptionNormalizer().normalize(
                fixture.raw, fixture.profile, "What do you see around us?");
        String prompt = semantic.promptBlock("What do you see around us?", true);
        assert prompt.contains("door");
        assert prompt.contains("water");
        assert prompt.contains("focused player");
        assert prompt.contains("Lycander") || prompt.contains("lycander");
        assert !prompt.contains(fixture.entityId.toString());
        assert !prompt.contains("samples=");
        assert !prompt.contains("Stone_Door_Fancy");
        assert !prompt.contains("12.5, 64.0, -8.5");
        assert fixture.raw.debugBlock().contains(fixture.entityId.toString());
        assert fixture.raw.debugBlock().contains("sampleCount=");
        assert fixture.raw.debugBlock().contains("Stone_Door_Fancy");
    }

    private static void conversationalPromptUsesSemanticUnderstanding() throws Exception {
        Fixture fixture = fixture();
        var root = Files.createTempDirectory("r030-prompt-");
        RelationshipStore relationships = new RelationshipStore(root);
        relationships.load();
        MemoryStore memories = new MemoryStore(root, 50);
        memories.load();
        ConversationContextBuilder builder = new ConversationContextBuilder(
                relationships, memories, 4);
        ConversationSession session = new ConversationSession(UUID.randomUUID(),
                fixture.profile.id(), UUID.randomUUID(), Instant.now());
        var request = builder.build(session, fixture.profile, "What is around us?",
                new MinimalWorldContext("diagnostic-world", 12, 64, -8),
                fixture.raw.engineSnapshot(), List.of());
        String prompt = request.messages().stream().map(value -> value.content())
                .collect(java.util.stream.Collectors.joining("\n"));
        assert prompt.contains("SEMANTIC SURROUNDINGS");
        assert prompt.contains("door");
        assert !prompt.contains("samples=");
        assert !prompt.contains(fixture.entityId.toString());
        assert !prompt.contains("Stone_Door_Fancy");
        assert !prompt.contains("12.5, 64.0, -8.5");
    }

    private static void spokenCommitRejectsDebugRepresentations() {
        assert SpokenTextSafetyValidator.isSafe(
                "There is a doorway close by, with water beyond it.");
        assert !SpokenTextSafetyValidator.isSafe(
                "samples=42 around Stone_Door_Fancy");
        assert !SpokenTextSafetyValidator.isSafe(
                "My entityId=123e4567-e89b-42d3-a456-426614174000");
        assert !SpokenTextSafetyValidator.isSafe("I stand at 12.5, 64.0, -8.5.");
        assert !SpokenTextSafetyValidator.isSafe("RawPerceptionSnapshot says it is nearby.");
        assert !SpokenTextSafetyValidator.isSafe("CURRENT_WORLD_STATE is ready.");
    }

    private static void latencyTraceIsResponseScopedAndBudgeted() {
        ResponseLatencyTraceStore traces = new ResponseLatencyTraceStore(
                new LatencyBudgetConfig(Map.of(
                        ResponseLatencyStage.SEMANTIC_NORMALIZATION, 1L)));
        UUID response = UUID.randomUUID();
        UUID npc = UUID.randomUUID();
        traces.begin(response, npc, UUID.randomUUID());
        traces.recordDuration(response, ResponseLatencyStage.PERCEPTION_CAPTURE, 12);
        traces.recordDuration(response, ResponseLatencyStage.SEMANTIC_NORMALIZATION, 5);
        traces.mark(response, ResponseLatencyStage.NEMOTRON_REQUEST_START);
        traces.complete(response);
        var trace = traces.latest(npc).orElseThrow();
        assert trace.responseId().equals(response);
        assert trace.complete();
        assert trace.anyOverBudget();
        assert trace.stages().stream().anyMatch(value ->
                value.stage() == ResponseLatencyStage.SEMANTIC_NORMALIZATION
                        && value.overBudget());
    }

    private static Fixture fixture() {
        UUID npcId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        UUID playerEntityId = UUID.randomUUID();
        List<EnvironmentSample> samples = List.of(
                new EnvironmentSample("Stone_Door_Fancy", "Doors", "STONE", "DoorModel",
                        14.5, 64.0, -8.5, true, false, true, false,
                        false, false, false),
                new EnvironmentSample("Water_Source_Block", "fluid", "fluid", "",
                        17.5, 63.0, -8.5, false, false, false, false,
                        false, false, true),
                new EnvironmentSample("Grass_Block", "terrain", "GRASS", "",
                        12.5, 63.0, -8.5, false, false, false, false,
                        false, false, false));
        var environment = new EnvironmentSemanticAnalyzer().summarize(worldId, Instant.now(),
                12.5, 64.0, -8.5, 13.0, 64.0, -8.0, 14, samples, 7);
        NpcProfile profile = new NpcProfile(npcId, "Lycander", "village smith",
                "stern and practical", "An authored NPC.", "Live honestly.",
                "unknown", "unknown", List.of(), List.of(), List.of(), List.of(), 0);
        NpcPerceptionSnapshot snapshot = new NpcPerceptionSnapshot(npcId, entityId, worldId,
                LocalDateTime.of(42, 3, 7, 14, 30), 12.5, 64.0, -8.5,
                List.of(new PerceivedEntity(playerEntityId, "focused player", "player", 1.2)),
                List.of(), List.of(), List.of(),
                List.of(new PerceivedEntity(null, "Stone_Door_Fancy",
                        "interactable_block", 2.0)), List.of(), 1,
                new PerceivedItem(null, "Iron_Hammer", "Iron Hammer", 1,
                        0, 0, "{}", 0), List.of(), environment);
        return new Fixture(profile, entityId, new RawPerceptionSnapshot(UUID.randomUUID(),
                Instant.now(), "world-thread", 9, snapshot, samples.size(), samples));
    }

    private record Fixture(NpcProfile profile, UUID entityId, RawPerceptionSnapshot raw) { }
}
