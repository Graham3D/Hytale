package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.conversation.AuthoritativeDialogueValidator;
import com.inigmasgames.persistentnpcs.conversation.CognitiveContextPlan;
import com.inigmasgames.persistentnpcs.conversation.CognitiveContextRouter;
import com.inigmasgames.persistentnpcs.conversation.CognitiveDepth;
import com.inigmasgames.persistentnpcs.conversation.ConversationContextBuilder;
import com.inigmasgames.persistentnpcs.conversation.ConversationGrounding;
import com.inigmasgames.persistentnpcs.conversation.ConversationGroundingService;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.conversation.DialogueMode;
import com.inigmasgames.persistentnpcs.conversation.DialogueRequestState;
import com.inigmasgames.persistentnpcs.conversation.MinimalWorldContext;
import com.inigmasgames.persistentnpcs.memory.MemoryRecord;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.memory.MemoryType;
import com.inigmasgames.persistentnpcs.perception.EnvironmentFeature;
import com.inigmasgames.persistentnpcs.perception.EnvironmentSnapshot;
import com.inigmasgames.persistentnpcs.perception.NpcPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.profile.AuthoredNpcRelationship;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class R035DialogueIntelligenceTest {
    private R035DialogueIntelligenceTest() { }

    public static void main(String[] args) throws Exception {
        identityAndAuthoredRelationshipsAreHardConstraints();
        simpleTurnsExcludeWorldWeatherMemoryAndShrinkPrompts();
        complexIntentKeepsFullCognition();
        generatedDialogueIsNotFactualMemoryEvidence();
        semanticGatePrecedesImportanceAndDuplicateFactsReinforce();
        validNoActionDialogueIsPreservedAndEnvironmentFallbackIsNatural();
        inspectorAndTtsExposeR035Diagnostics();
        System.out.println("R035 dialogue intelligence and trace-driven regression tests passed.");
    }

    private static void identityAndAuthoredRelationshipsAreHardConstraints() throws Exception {
        Fixture fixture = fixture();
        CognitiveContextPlan identity = CognitiveContextRouter.route(fixture.mara,
                "What is your name?", DialogueMode.ORDINARY_CONVERSATION,
                fixture.registry, fixture.relationships);
        assert identity.depth() == CognitiveDepth.DIRECT_FACT;
        var validator = new AuthoritativeDialogueValidator();
        var wrongName = validator.validate("I'm Jeff.", identity);
        assert wrongName.rewritten();
        assert wrongName.dialogue().equals("I'm Mara.") : wrongName;

        CognitiveContextPlan relationship = CognitiveContextRouter.route(fixture.mara,
                "Who is Lycander to you?", DialogueMode.ORDINARY_CONVERSATION,
                fixture.registry, fixture.relationships);
        assert relationship.depth() == CognitiveDepth.DIRECT_FACT : relationship;
        assert relationship.constraintBlock().contains("grandfather") : relationship;
        var rabbit = validator.validate("Lycander is a rabbit.", relationship);
        assert rabbit.rewritten();
        assert rabbit.dialogue().toLowerCase().contains("grandfather") : rabbit;
        assert !rabbit.dialogue().toLowerCase().contains("rabbit") : rabbit;
    }

    private static void simpleTurnsExcludeWorldWeatherMemoryAndShrinkPrompts() throws Exception {
        Fixture fixture = fixture();
        CognitiveContextPlan simple = CognitiveContextRouter.route(fixture.mara,
                "Greetings, Mara.", DialogueMode.ORDINARY_CONVERSATION,
                fixture.registry, fixture.relationships);
        assert simple.depth() == CognitiveDepth.SIMPLE_SOCIAL;
        for (String excluded : List.of("WEATHER", "SEMANTIC_WORLD", "MEMORIES", "GOALS")) {
            assert !simple.includes(excluded) : excluded;
        }
        ConversationSession session = new ConversationSession(UUID.randomUUID(),
                fixture.mara.id(), UUID.randomUUID(), Instant.now());
        ConversationContextBuilder builder = new ConversationContextBuilder(
                fixture.relationships, fixture.memories, 8);
        DialogueRequestState state = new DialogueRequestState(
                DialogueMode.ORDINARY_CONVERSATION, List.of(), List.of(), false);
        var minimal = builder.build(session, fixture.mara, "Greetings, Mara.",
                new MinimalWorldContext("default", 1, 2, 3),
                NpcPerceptionSnapshot.unavailable(fixture.mara.id()), List.of(),
                ConversationGrounding.none(), state, null, true, simple);
        var full = builder.build(session, fixture.mara, "Greetings, Mara.",
                new MinimalWorldContext("default", 1, 2, 3),
                NpcPerceptionSnapshot.unavailable(fixture.mara.id()), List.of(),
                ConversationGrounding.none(), state, null, false,
                CognitiveContextPlan.full("TEST_FULL"));
        int minimalChars = characters(minimal);
        int fullChars = characters(full);
        assert minimalChars < fullChars * 0.55
                : "minimal=" + minimalChars + " full=" + fullChars;
        String prompt = minimal.messages().getFirst().content();
        assert !prompt.contains("CURRENT_WEATHER");
        assert !prompt.contains("MEMORY (");

        CognitiveContextPlan spokenRequest = CognitiveContextRouter.route(fixture.mara,
                "Can you tell me a dry joe?", DialogueMode.ORDINARY_CONVERSATION,
                fixture.registry, fixture.relationships);
        assert spokenRequest.depth() != CognitiveDepth.COMPLEX_INTENT : spokenRequest;
        assert !spokenRequest.detectedIntent().equals("REQUEST_ACTION_OR_COMMITMENT")
                : spokenRequest;
    }

    private static void complexIntentKeepsFullCognition() throws Exception {
        Fixture fixture = fixture();
        CognitiveContextPlan complex = CognitiveContextRouter.route(fixture.mara,
                "Would you like to go on an adventure?", DialogueMode.PROPOSED_PLAN,
                fixture.registry, fixture.relationships);
        assert complex.depth() == CognitiveDepth.COMPLEX_INTENT;
        for (String required : List.of("MEMORIES", "SEMANTIC_WORLD", "TASKS", "ACTIONS")) {
            assert complex.includes(required) : required;
        }
        assert !complex.includes("WEATHER") : "weather is intent scoped";
    }

    private static void generatedDialogueIsNotFactualMemoryEvidence() throws Exception {
        Fixture fixture = fixture();
        UUID player = UUID.randomUUID();
        fixture.memories.append(new MemoryRecord(UUID.randomUUID(), fixture.mara.id(), player,
                Instant.now(), MemoryType.CONVERSATION, 0.95,
                "Player asked about Lycander. NPC replied that Lycander was a rabbit.",
                1.0, "DIRECT", List.of(player), "", "Generated dialogue history."));
        var result = fixture.memories.retrieveDetailedForCognition(fixture.mara.id(), player,
                "Who is Lycander?", 8, "CALM", 0.0);
        assert result.selected().isEmpty() : result.selected();
        assert result.rejected().stream().anyMatch(value -> value.reason()
                .equals("NPC_GENERATED_DIALOGUE_IS_NOT_FACTUAL_EVIDENCE"));
    }

    private static void semanticGatePrecedesImportanceAndDuplicateFactsReinforce()
            throws Exception {
        Fixture fixture = fixture();
        UUID player = UUID.randomUUID();
        fixture.memories.append(new MemoryRecord(UUID.randomUUID(), fixture.mara.id(), player,
                Instant.now(), MemoryType.PLAYER_FACT, 0.99,
                "Player-reported belief: My name is Graham.", 0.9,
                "PLAYER_REPORT:source=" + player, List.of(player), "",
                "Player report."));
        fixture.memories.append(new MemoryRecord(UUID.randomUUID(), fixture.mara.id(), player,
                Instant.now(), MemoryType.EPISODIC, 0.25,
                "Placed the blue cup on the pantry shelf.", 0.9,
                "DIRECT_PERCEPTION", List.of(player), "pantry", "Witnessed."));
        var recall = fixture.memories.retrieveScoredForCognition(fixture.mara.id(), player,
                "Where is the blue cup?", 8);
        assert recall.size() == 1 : recall;
        assert recall.getFirst().memory().summary().contains("blue cup") : recall;

        int before = fixture.memories.forNpc(fixture.mara.id()).size();
        fixture.memories.append(new MemoryRecord(UUID.randomUUID(), fixture.mara.id(), player,
                Instant.now(), MemoryType.PLAYER_FACT, 0.99,
                "Player-reported belief: My name is Graham.", 0.9,
                "PLAYER_REPORT:source=" + player, List.of(player), "", "Player report."));
        assert fixture.memories.forNpc(fixture.mara.id()).size() == before;
        MemoryRecord reinforced = fixture.memories.forNpc(fixture.mara.id()).stream()
                .filter(value -> value.summary().contains("Graham")).findFirst().orElseThrow();
        assert reinforced.rehearsalCount() == 1 : reinforced;
    }

    private static void validNoActionDialogueIsPreservedAndEnvironmentFallbackIsNatural()
            throws Exception {
        ConversationSession session = new ConversationSession(UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        String natural = "That sounds like a fine idea.";
        String kept = new ConversationGroundingService(null).enforceModelDialogue(session,
                natural, NpcPerceptionSnapshot.unavailable(session.npcId()));
        assert kept.equals(natural) : kept;
        EnvironmentSnapshot environment = new EnvironmentSnapshot(UUID.randomUUID(),
                Instant.now(), 0, 0, 0, 1.0, 0.0, 1.0, 16, 300, 3,
                "village", "a constructed stone/masonry area with vegetation",
                List.of(new EnvironmentFeature("player", "focused player", 1, 1,
                        "northeast", 100),
                        new EnvironmentFeature("door", "door", 13, 8, "north", 80),
                        new EnvironmentFeature("bench", "bench weapon", 32, 3,
                                "north", 70)), List.of(),
                List.of(new EnvironmentFeature("water", "water", 59, 5, "south", 50)),
                Map.of("stone", 120));
        String description = environment.groundedDescription();
        assert description.contains("stone building") : description;
        for (String forbidden : List.of("constructed", "masonry", "focused player",
                "samples=", "bench weapon")) assert !description.contains(forbidden) : description;
    }

    private static void inspectorAndTtsExposeR035Diagnostics() throws Exception {
        String inspector = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/ui/CognitionInspectorPage.java"));
        for (String required : List.of("cognitiveDepth", "ContextRouting", "rawModelOutput",
                "groundingSafetyDecision", "canonicalResponse", "rejectedMemories")) {
            assert inspector.contains(required) : required;
        }
        String voice = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/orbis/OrbisSpeechCoordinator.java"))
                + Files.readString(Path.of("src/main/java/com/inigmasgames/"
                        + "persistentnpcs/orbis/OrbisResourceScheduler.java"))
                + Files.readString(Path.of("src/main/java/com/inigmasgames/"
                        + "persistentnpcs/voice/TurboVoiceWorker.java"));
        for (String required : List.of("TTS_QUEUE_WAIT", "TTS_WORKER_QUEUE_WAIT",
                "TTS_SYNTHESIS_DURATION", "RESOURCE_PRESSURE", "modelLoadCount")) {
            assert voice.contains(required) : required;
        }
    }

    private static int characters(com.inigmasgames.persistentnpcs.llm.LlmRequest request) {
        return request.messages().stream().mapToInt(value -> value.content().length()).sum();
    }

    private static Fixture fixture() throws Exception {
        Path root = Files.createTempDirectory("r035-");
        UUID maraId = UUID.randomUUID();
        UUID lycanderId = UUID.randomUUID();
        NpcProfile mara = profile(maraId, "Mara").withRelationships(List.of(
                new AuthoredNpcRelationship(lycanderId.toString(), "Lycander",
                        "GRANDFATHER", .9, .95, .92, .97, .1, 0.0, 0.0,
                        "Lycander is Mara's grandfather and only remaining close family.")));
        NpcProfile lycander = profile(lycanderId, "Lycander");
        NpcProfileRegistry registry = new NpcProfileRegistry(new ProfileRepository(root));
        registry.register(mara);
        registry.register(lycander);
        RelationshipStore relationships = new RelationshipStore(root);
        relationships.load();
        relationships.importAuthored(List.of(mara, lycander), registry);
        MemoryStore memories = new MemoryStore(root, 100);
        memories.load();
        return new Fixture(mara, registry, relationships, memories);
    }

    private static NpcProfile profile(UUID id, String name) {
        return new NpcProfile(id, name, "Village smith", "Warm, direct, and observant.",
                "An authored village resident.", "Respond naturally without inventing facts.",
                "A village home", "Village workshop", List.of("honesty"),
                List.of("lies"), List.of("VILLAGER"), List.of("FOLLOW_PLAYER"), 5)
                .validated();
    }

    private record Fixture(NpcProfile mara, NpcProfileRegistry registry,
            RelationshipStore relationships, MemoryStore memories) { }
}
