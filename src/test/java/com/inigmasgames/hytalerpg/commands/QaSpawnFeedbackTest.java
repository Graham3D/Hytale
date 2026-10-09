package com.inigmasgames.hytalerpg.commands;

import java.util.List;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class QaSpawnFeedbackTest {
    @Test void successfulPlanReportsActualPublishedAffixesWithoutInternalIds() {
        assertEquals("Trork Warrior spawned with Extra Strong, Frenzied, and Armor Breaker.",
                QaSpawnFeedback.success("Trork_Warrior",List.of("ME-002","ME-019","ME-025")));
    }

    @Test void incompatibleAffixesAreNamedTogether() {
        assertEquals("Extra Fast is incompatible with Golem Crystal Earth.",
                QaSpawnFeedback.failure(new IllegalArgumentException(
                        "QA_AFFIX_CAPABILITY_UNSUPPORTED:ME-001:Golem_Crystal_Earth"),
                        "Golem_Crystal_Earth","hell"));
        assertEquals("Extra Fast and Mana Burn are incompatible with Golem Crystal Earth.",
                QaSpawnFeedback.failure(new CompletionException(new IllegalArgumentException(
                        "QA_AFFIX_CAPABILITY_UNSUPPORTED:ME-001,ME-013:Golem_Crystal_Earth")),
                        "Golem_Crystal_Earth","hell"));
    }

    @Test void unavailableAutoSetAndPlacementUsePlayerLanguage() {
        assertEquals("No compatible affix combination is available for Skeleton Fighter in Hell.",
                QaSpawnFeedback.failure(new IllegalArgumentException("QA_NO_SUPPORTED_RANDOM_AFFIX_SET"),
                        "Skeleton_Fighter","hell"));
        assertEquals("Could not find a safe place to spawn Trork Warrior nearby.",
                QaSpawnFeedback.failure(new IllegalStateException("QA_SPAWN_NO_VALID_POSITION attempts=9"),
                        "Trork_Warrior","normal"));
    }
}
