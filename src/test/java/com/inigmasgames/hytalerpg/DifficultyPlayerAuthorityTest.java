package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.cooldown.SavedCooldown;
import com.inigmasgames.hytalerpg.difficulty.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DifficultyPlayerAuthorityTest {
    @Test void existingPlayerAuthorityCommitsChecklistAndCooldownUpdatesWithoutOverwritingEither() {
        var bundle = Stage01BTestSupport.bundle(); UUID player = UUID.randomUUID();
        try (var service = bundle.service()) {
            var before = service.getPresentationView(player).state();
            var result = service.mutateProgress(player, before.revision, "difficulty-fixture", state -> {
                state.difficulty = state.difficulty.complete(DifficultyId.NORMAL, "fixture.earth");
            });
            assertTrue(result.success());
            service.saveCooldowns(player, Map.of("quick_slash", new SavedCooldown(9, .25)));
            var after = service.getPresentationView(player).state();
            assertTrue(after.difficulty.milestones().get(DifficultyId.NORMAL).contains("fixture.earth"));
            assertEquals(9, after.cooldowns.get("quick_slash").remainingWork());
            assertEquals(before.currentXp, after.currentXp); assertArrayEquals(before.equippedSkills, after.equippedSkills);
            assertEquals(after.difficulty, bundle.repository().load(player).state().difficulty);
            assertFalse(service.mutateProgress(player, before.revision, "stale", state -> state.difficulty = DifficultyProgress.INITIAL).success());
            assertEquals(after.difficulty, service.getPresentationView(player).state().difficulty);
        }
    }
    @Test void failedChecklistPersistenceDoesNotPublishUnlocks() {
        var bundle = Stage01BTestSupport.bundle(); UUID player = UUID.randomUUID();
        try (var service = bundle.service()) {
            var before = service.getPresentationView(player).state(); bundle.repository().failSave = true;
            var result = service.mutateProgress(player, before.revision, "fail", state -> state.difficulty = state.difficulty.complete(DifficultyId.NORMAL, "fixture.earth").unlockNext(DifficultyId.NORMAL, Set.of("fixture.earth")));
            assertFalse(result.success());
            assertEquals(DifficultyProgress.INITIAL, bundle.repository().load(player).state().difficulty);
        }
    }
}
