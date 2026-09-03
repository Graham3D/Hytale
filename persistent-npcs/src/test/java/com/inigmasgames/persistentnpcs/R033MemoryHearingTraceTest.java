package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.cognition.PlayerFactMemoryService;
import com.inigmasgames.persistentnpcs.cognition.PlayerInputKind;
import com.inigmasgames.persistentnpcs.cognition.SourcedBeliefStore;
import com.inigmasgames.persistentnpcs.memory.MemoryRecord;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.memory.MemoryType;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import com.inigmasgames.persistentnpcs.voice.EligibleNpcListener;
import com.inigmasgames.persistentnpcs.voice.PlayerSpeechIntent;
import com.inigmasgames.persistentnpcs.voice.PlayerUtteranceAudienceService;
import com.inigmasgames.persistentnpcs.voice.PlayerUtteranceEvent;
import com.inigmasgames.persistentnpcs.voice.SpeechProjection;
import com.inigmasgames.persistentnpcs.voice.UtteranceRangeClass;
import com.inigmasgames.persistentnpcs.voice.VoiceInteractionTraceStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class R033MemoryHearingTraceTest {
    private R033MemoryHearingTraceTest() { }

    public static void main(String[] args) throws Exception {
        declarativeFactsPersistWithProvenanceAndRecallSemantically();
        acknowledgementsAndRepetitionsDoNotDuplicateFacts();
        autobiographicalFactsOutrankUnrelatedActionResults();
        hearingAndSpeechOwnershipRemainSeparate();
        twoIndependentMindsRememberOneImmutableUtterance();
        perNpcResponseBindingsCannotBeReassigned();
        nativeInspectorExposesR033Sections();
        System.out.println("R033 memory, multi-NPC hearing, and trace tests passed.");
    }

    private static void declarativeFactsPersistWithProvenanceAndRecallSemantically()
            throws Exception {
        Fixture fixture = fixture();
        UUID utterance = UUID.randomUUID();
        UUID response = UUID.randomUUID();
        var result = fixture.facts.persist(fixture.mara.id(), fixture.player,
                UUID.randomUUID(), response, utterance,
                "I hid my old compass under the oak near the ruined bridge yesterday.",
                Instant.now());
        assert result.analysis().classification() == PlayerInputKind.DECLARATIVE_FACT;
        assert result.beliefWrites().size() == 1 : result;
        assert result.memoryWrites().size() == 1 : result;
        var belief = result.beliefWrites().getFirst();
        assert belief.sourceEntityId().equals(fixture.player);
        assert belief.utteranceId().equals(utterance);
        assert belief.responseId().equals(response);
        assert !belief.semanticLocation().isBlank();
        assert belief.temporalReference().equals("yesterday");
        List<MemoryStore.ScoredMemory> recall = fixture.memories
                .retrieveScoredForCognition(fixture.mara.id(), fixture.player,
                        "Where did I stash my compass?", 4);
        assert !recall.isEmpty() : recall;
        assert recall.getFirst().memory().memoryId().equals(result.memoryWrites().getFirst());
        assert recall.getFirst().memory().source().contains("utterance=" + utterance);
    }

    private static void acknowledgementsAndRepetitionsDoNotDuplicateFacts() throws Exception {
        Fixture fixture = fixture();
        String fact = "I hid the silver key beneath the old stairs.";
        var first = fixture.facts.persist(fixture.mara.id(), fixture.player, null, null,
                UUID.randomUUID(), fact, Instant.now());
        var duplicate = fixture.facts.persist(fixture.mara.id(), fixture.player, null, null,
                UUID.randomUUID(), fact, Instant.now());
        var acknowledgement = fixture.facts.persist(fixture.mara.id(), fixture.player,
                null, null, UUID.randomUUID(), "Yes, that's what I said.", Instant.now());
        assert first.memoryWrites().size() == 1;
        assert duplicate.memoryWrites().isEmpty();
        assert duplicate.rejectionReason().equals("DUPLICATE_PROPOSITION");
        assert acknowledgement.analysis().classification() == PlayerInputKind.ACKNOWLEDGEMENT
                || acknowledgement.analysis().classification() == PlayerInputKind.CONFIRMATION;
        assert acknowledgement.memoryWrites().isEmpty();
        assert fixture.memories.forNpc(fixture.mara.id()).size() == 1;
    }

    private static void autobiographicalFactsOutrankUnrelatedActionResults()
            throws Exception {
        Fixture fixture = fixture();
        fixture.facts.persist(fixture.mara.id(), fixture.player, null, null,
                UUID.randomUUID(), "I stashed my compass beside the mill.", Instant.now());
        fixture.memories.append(new MemoryRecord(UUID.randomUUID(), fixture.mara.id(),
                fixture.player, Instant.now(), MemoryType.ACTION_RESULT, 1.0,
                "Finished walking to the northern gate successfully.", 1.0,
                "ACTION_RESULT", List.of(fixture.player), "northern gate", "Completed action."));
        List<MemoryStore.ScoredMemory> results = fixture.memories
                .retrieveScoredForCognition(fixture.mara.id(), fixture.player,
                        "Where did I hide my compass?", 8);
        assert results.getFirst().memory().type() == MemoryType.PLAYER_FACT : results;
        double playerFact = results.stream().filter(value -> value.memory().type()
                == MemoryType.PLAYER_FACT).findFirst().orElseThrow().score();
        double action = results.stream().filter(value -> value.memory().type()
                == MemoryType.ACTION_RESULT).findFirst().map(MemoryStore.ScoredMemory::score)
                .orElse(Double.NEGATIVE_INFINITY);
        assert playerFact > action : results;
    }

    private static void hearingAndSpeechOwnershipRemainSeparate() {
        EligibleNpcListener mara = listener(UUID.randomUUID(), "Mara", false, 40);
        EligibleNpcListener lycander = listener(UUID.randomUUID(), "Lycander", true, 1000);
        List<EligibleNpcListener> eligible = List.of(mara, lycander);
        List<EligibleNpcListener> owners = PlayerUtteranceAudienceService
                .selectResponseOwners(eligible);
        assert eligible.size() == 2;
        assert owners.size() == 1 && owners.getFirst().npcId().equals(lycander.npcId());

        EligibleNpcListener directMara = listener(mara.npcId(), "Mara", true, 1001);
        owners = PlayerUtteranceAudienceService.selectResponseOwners(
                List.of(directMara, lycander));
        assert owners.size() == 2 : owners;
    }

    private static void twoIndependentMindsRememberOneImmutableUtterance() throws Exception {
        Fixture fixture = fixture();
        UUID utteranceId = UUID.randomUUID();
        String transcript = "I buried the brass seal behind the fountain.";
        var first = fixture.facts.persist(fixture.mara.id(), fixture.player, null, null,
                utteranceId, transcript, Instant.now());
        var second = fixture.facts.persist(fixture.lycander.id(), fixture.player, null, null,
                utteranceId, transcript, Instant.now());
        assert first.memoryWrites().size() == 1;
        assert second.memoryWrites().size() == 1;
        assert fixture.memories.forNpc(fixture.mara.id()).stream().anyMatch(value ->
                value.source().contains("utterance=" + utteranceId));
        assert fixture.memories.forNpc(fixture.lycander.id()).stream().anyMatch(value ->
                value.source().contains("utterance=" + utteranceId));
    }

    private static void perNpcResponseBindingsCannotBeReassigned() {
        UUID player = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        UUID utterance = UUID.randomUUID();
        EligibleNpcListener mara = listener(UUID.randomUUID(), "Mara", true, 1000);
        EligibleNpcListener lycander = listener(UUID.randomUUID(), "Lycander", true, 999);
        PlayerUtteranceEvent event = new PlayerUtteranceEvent(utterance, player,
                "Mara and Lycander, listen.", world, 0, 0, 0, Instant.now(),
                Set.of(mara.npcId(), lycander.npcId()), PlayerSpeechIntent.DIRECT_ADDRESS,
                List.of(mara, lycander), 100, 200, 1);
        var resolution = new PlayerUtteranceAudienceService.Resolution(event,
                List.of(mara, lycander), Map.of(), Map.of(), Map.of());
        VoiceInteractionTraceStore traces = new VoiceInteractionTraceStore();
        traces.begin(utterance, player, System.nanoTime());
        traces.audience(resolution);
        UUID maraResponse = UUID.randomUUID();
        UUID lycanderResponse = UUID.randomUUID();
        traces.bindResponse(utterance, mara.npcId(), maraResponse, SpeechProjection.NORMAL);
        traces.bindResponse(utterance, lycander.npcId(), lycanderResponse,
                SpeechProjection.CALL);
        var maraTrace = traces.latest(mara.npcId()).orElseThrow();
        var lycanderTrace = traces.latest(lycander.npcId()).orElseThrow();
        assert maraTrace.responseId().equals(maraResponse);
        assert maraTrace.responseNpcId().equals(mara.npcId());
        assert lycanderTrace.responseId().equals(lycanderResponse);
        assert lycanderTrace.responseNpcId().equals(lycander.npcId());
        assert maraTrace.projection() == SpeechProjection.NORMAL;
        assert lycanderTrace.projection() == SpeechProjection.CALL;
    }

    private static void nativeInspectorExposesR033Sections() throws Exception {
        String ui = Files.readString(Path.of("src/main/resources/Common/UI/Custom/Pages/"
                + "ImmersiveNpcCognitionInspector.ui"));
        for (String required : List.of("HEARING", "#Hearing", "MEMORY", "#Memory")) {
            assert ui.contains(required) : required;
        }
    }

    private static Fixture fixture() throws Exception {
        Path root = Files.createTempDirectory("r033-");
        ProfileRepository repository = new ProfileRepository(root);
        NpcProfileRegistry profiles = new NpcProfileRegistry(repository);
        NpcProfile mara = profile("Mara");
        NpcProfile lycander = profile("Lycander");
        profiles.register(mara);
        profiles.register(lycander);
        SourcedBeliefStore beliefs = new SourcedBeliefStore(root);
        beliefs.load();
        MemoryStore memories = new MemoryStore(root, 100);
        memories.load();
        UUID player = UUID.randomUUID();
        return new Fixture(mara, lycander, player, memories,
                new PlayerFactMemoryService(profiles, beliefs, memories));
    }

    private static EligibleNpcListener listener(UUID id, String name,
            boolean direct, double score) {
        return new EligibleNpcListener(id, name, 3.0, "nearby", "north",
                UtteranceRangeClass.ORDINARY, direct, false, score);
    }

    private static NpcProfile profile(String name) {
        return new NpcProfile(UUID.randomUUID(), name, "villager", "grounded",
                "An authored NPC.", "Live a grounded life.", "", "", List.of(),
                List.of(), List.of(), List.of(), 0).validated();
    }

    private record Fixture(NpcProfile mara, NpcProfile lycander, UUID player,
            MemoryStore memories, PlayerFactMemoryService facts) { }
}
