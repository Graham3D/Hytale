package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.autonomy.AffordanceRegistry;
import com.inigmasgames.persistentnpcs.autonomy.AttentionScore;
import com.inigmasgames.persistentnpcs.autonomy.AttentionScorer;
import com.inigmasgames.persistentnpcs.autonomy.GroundedSemanticClassifier;
import com.inigmasgames.persistentnpcs.autonomy.GroundedStimulus;
import com.inigmasgames.persistentnpcs.cognition.NpcEmotion;
import com.inigmasgames.persistentnpcs.cognition.NpcEmotionalState;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class R026EmbodiedAgencyTest {
    private R026EmbodiedAgencyTest() { }

    public static void main(String[] args) throws Exception {
        authoritativeAssetMetadataMapsToSupportedSemantics();
        attentionUsesProfileMoodDistanceObligationsAndRepetition();
        opportunitiesFailClosedAndCoverVerticalSlices();
        System.out.println("R026 embodied autonomous agency targeted tests passed.");
    }

    private static void authoritativeAssetMetadataMapsToSupportedSemantics() {
        GroundedSemanticClassifier classifier = new GroundedSemanticClassifier();
        assert classifier.classifyBlockIdentity("Plant_Flower_Rose_Red", false, false)
                .equals("FLOWER");
        assert classifier.classifyBlockIdentity("Plant_Bush_Forest", false, false)
                .equals("BUSH");
        assert classifier.classifyBlockIdentity("Ore_Mithril_Stone", false, false)
                .equals("RARE_ORE");
        assert classifier.classifyBlockIdentity("Village_Forge", true, false)
                .equals("WORKSTATION");
        assert classifier.classifyBlockIdentity("Furniture_Chair", false, true)
                .equals("CHAIR");
        assert classifier.classifyEntity("Wildlife_Fox_Red").equals("FOX");
        assert classifier.classifyEntity("Wildlife_Deer").equals("ANIMAL");
        assert classifier.classifyEntity("Villager_Generic").equals("NPC");
        assert classifier.classifyWeather("Weather_Rain_Heavy").equals("RAIN");
        assert classifier.classifyWeather("Weather_Thunderstorm").equals("STORM");
        assert classifier.classifyWeather("Weather_Clear").isBlank();
    }

    private static void attentionUsesProfileMoodDistanceObligationsAndRepetition()
            throws Exception {
        Path root = Files.createTempDirectory("r026-attention-");
        NpcProfile profile = new ProfileRepository(root).loadTestProfile();
        UUID world = UUID.randomUUID();
        Instant now = Instant.now();
        GroundedStimulus flower = new GroundedStimulus("block:" + world + ":1:2:3",
                "FLOWER", "Plant_Flower_Rose_Red", world, 1.5, 2.5, 3.5,
                1.0, "HYTALE_BLOCK_STATE", now);
        AttentionScorer scorer = new AttentionScorer();
        AttentionScore curious = scorer.score(profile,
                new NpcEmotionalState(profile.id(), NpcEmotion.CURIOUS, 0.6, now, "test"),
                flower, false, 0);
        AttentionScore uneasyBusy = scorer.score(profile,
                new NpcEmotionalState(profile.id(), NpcEmotion.UNEASY, 0.8, now, "test"),
                flower, false, 3);
        AttentionScore repeated = scorer.score(profile, null, flower, true, 0);
        assert curious.total() > uneasyBusy.total();
        assert curious.total() > repeated.total();
        assert curious.novelty() > repeated.novelty();
        assert uneasyBusy.obligations() > 0;
        assert repeated.repetitionPenalty() > 0;
        assert curious.total() >= 0.62 : curious;
        assert repeated.total() < 0.62 : repeated;
    }

    private static void opportunitiesFailClosedAndCoverVerticalSlices() {
        AffordanceRegistry registry = new AffordanceRegistry();
        assert registry.forType("FLOWER").contains("INVESTIGATE");
        assert registry.forType("FOX").contains("APPROACH_CAUTIOUSLY");
        assert registry.forType("RARE_ORE").equals(List.of("INSPECT"));
        assert registry.forType("RAIN").equals(List.of("WATCH_RAIN"));
        assert registry.forType("AMBIENT_HUM").equals(List.of("HUM"));
        assert registry.forType("AMBIENT_STRETCH").equals(List.of("STRETCH"));
        assert registry.forType("MODEL_INVENTED_OBJECT").isEmpty();
    }
}
