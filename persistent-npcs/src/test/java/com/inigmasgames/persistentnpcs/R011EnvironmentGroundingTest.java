package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.conversation.DialogueClaimValidator;
import com.inigmasgames.persistentnpcs.conversation.DialogueMode;
import com.inigmasgames.persistentnpcs.conversation.DialogueRequestState;
import com.inigmasgames.persistentnpcs.perception.EnvironmentSample;
import com.inigmasgames.persistentnpcs.perception.EnvironmentSemanticAnalyzer;
import com.inigmasgames.persistentnpcs.perception.EnvironmentSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class R011EnvironmentGroundingTest {
    private R011EnvironmentGroundingTest() { }

    public static void main(String[] args) {
        flatWorldIsGrounded();
        crossroadsPrioritizesPortal();
        queryModeAndValidation();
        movementFreshnessInputsRemainDistinct();
        System.out.println("Immersive AI R011 environment grounding tests passed.");
    }

    private static void flatWorldIsGrounded() {
        List<EnvironmentSample> samples = new ArrayList<>();
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                samples.add(sample("Grass_Block", x, 79, z));
            }
        }
        EnvironmentSnapshot snapshot = analyzer().summarize(UUID.randomUUID(), Instant.now(),
                0, 80, 0, 1.0, 80.0, 1.0, 14, samples, 3);
        assert snapshot.terrain().contains("grassy terrain") : snapshot.semanticBlock();
        assert snapshot.supports("grass");
        assert !snapshot.supports("forest");
        assert !snapshot.semanticBlock().toLowerCase().contains("forest");
    }

    private static void crossroadsPrioritizesPortal() {
        List<EnvironmentSample> samples = new ArrayList<>();
        samples.add(new EnvironmentSample("Hub_Portal_Default", "Portal", "Solid",
                "Blocks/Miscellaneous/Platform_MagicInactive.blockymodel",
                8, 0, 0, true, false, false, false, false, true, false));
        for (int index = 0; index < 90; index++) {
            samples.add(sample(index % 9 == 0 ? "Stone_Ruins_Pillar" : "Stone_Brick_Wall",
                    (index % 12) - 6, index % 8, (index % 10) - 5));
        }
        for (int index = 0; index < 18; index++) {
            samples.add(sample("Moss_Vine", index % 6, index % 5, -(index % 4)));
        }
        EnvironmentSnapshot snapshot = analyzer().summarize(UUID.randomUUID(), Instant.now(),
                0, 0, 0, 0.0, 0.0, -2.0, 14, samples, 5);
        assert snapshot.importantObjects().getFirst().category().equals("portal")
                : snapshot.semanticBlock();
        assert snapshot.supports("portal");
        assert snapshot.supports("ruins");
        assert snapshot.supports("vegetation");
        assert snapshot.terrain().contains("stone/masonry") : snapshot.terrain();
        assert snapshot.semanticBlock().contains("nearby to the east")
                : snapshot.semanticBlock();
        assert !snapshot.semanticBlock().contains("samples=") : snapshot.semanticBlock();
        System.out.println("R011_CROSSROADS_FIXTURE " + snapshot.groundedDescription());
        DialogueClaimValidator validator = new DialogueClaimValidator();
        DialogueRequestState query = new DialogueRequestState(
                DialogueMode.ENVIRONMENT_QUERY, List.of(), List.of(), false);
        var omission = validator.validate(DialogueMode.ENVIRONMENT_QUERY,
                "What do you see around us?", "I see open ground.", query, snapshot);
        assert omission.rewritten();
        assert omission.dialogue().toLowerCase().contains("portal") : omission.dialogue();
        assert omission.dialogue().toLowerCase().contains("stone") : omission.dialogue();
    }

    private static void queryModeAndValidation() {
        assert DialogueMode.classify("What do you see around us?", false, false)
                == DialogueMode.ENVIRONMENT_QUERY;
        assert DialogueMode.classify("Do you know where we are?", false, false)
                == DialogueMode.ENVIRONMENT_QUERY;
        assert DialogueMode.classify("Tell me a story about a forest.", false, false)
                == DialogueMode.FICTIONAL_STORY;

        List<EnvironmentSample> grass = new ArrayList<>();
        for (int index = 0; index < 30; index++) {
            grass.add(sample("Grass_Block", index % 5, -1, index % 6));
        }
        EnvironmentSnapshot flat = analyzer().summarize(UUID.randomUUID(), Instant.now(),
                0, 0, 0, null, null, null, 14, grass, 2);
        DialogueClaimValidator validator = new DialogueClaimValidator();
        DialogueRequestState query = new DialogueRequestState(
                DialogueMode.ENVIRONMENT_QUERY, List.of(), List.of(), false);
        var rejected = validator.validate(DialogueMode.ENVIRONMENT_QUERY,
                "Where are we?", "We're at the edge of a forest beside the main path.",
                query, flat);
        assert rejected.rewritten();
        assert rejected.dialogue().contains("grassy terrain") : rejected.dialogue();
        assert !rejected.dialogue().toLowerCase().contains("forest");
        System.out.println("R011_FLAT_FIXTURE " + rejected.dialogue());

        var fiction = validator.validate(DialogueMode.FICTIONAL_STORY,
                "Tell me a story about a forest", "Once upon a time, a forest spoke.",
                new DialogueRequestState(DialogueMode.FICTIONAL_STORY,
                        List.of(), List.of(), false), flat);
        assert !fiction.rewritten();
        assert fiction.dialogue().contains("forest");
    }

    private static void movementFreshnessInputsRemainDistinct() {
        Instant now = Instant.now();
        List<EnvironmentSample> grassy = new ArrayList<>();
        List<EnvironmentSample> masonry = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            grassy.add(sample("Grass_Block", index % 4, 79, index / 4));
            masonry.add(sample("Stone_Brick_Wall", 30 + index % 4, 80 + index / 4,
                    30));
        }
        EnvironmentSnapshot first = analyzer().summarize(UUID.randomUUID(), now,
                0, 80, 0, 0.0, 80.0, 1.0, 14,
                grassy, 1);
        EnvironmentSnapshot moved = analyzer().summarize(first.worldId(), now.plusSeconds(1),
                30, 80, 30, 30.0, 80.0, 31.0, 14,
                masonry, 1);
        assert first.npcX() != moved.npcX();
        assert !first.terrain().equals(moved.terrain());
        assert moved.ageMillis(now.plusSeconds(1)) == 0;

        List<EnvironmentSample> volume = new ArrayList<>();
        for (int index = 0; index < 9_240; index++) {
            volume.add(sample(index % 37 == 0 ? "Stone_Brick_Wall" : "Grass_Block",
                    index % 29, (index / 29) % 15, (index / 435) % 29));
        }
        long started = System.nanoTime();
        analyzer().summarize(first.worldId(), Instant.now(), 0, 0, 0,
                1.0, 0.0, 1.0, 14, volume, 0);
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
        System.out.println("R011_SEMANTIC_BENCH samples=" + volume.size()
                + " elapsedMs=" + elapsedMillis);
        assert elapsedMillis < 1_000 : "Semantic aggregation unexpectedly slow: " + elapsedMillis;
    }

    private static EnvironmentSample sample(String id, double x, double y, double z) {
        return new EnvironmentSample(id, "", "Solid", "", x, y, z,
                false, false, false, false, false, false, false);
    }

    private static EnvironmentSemanticAnalyzer analyzer() {
        return new EnvironmentSemanticAnalyzer();
    }
}
