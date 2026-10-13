package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.AddReason;
import com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChapterIEncounterContextSelectionTest {
    @Test void freshUnclassifiedActorsDoNotConsumeDurableContextLoadsButSavedActorsStillCanRestore(){
        assertFalse(HytaleEncounterRewards.needsEncounterContextLoad(AddReason.SPAWN,Optional.empty()));
        assertTrue(HytaleEncounterRewards.needsEncounterContextLoad(AddReason.LOAD,Optional.empty()));
        UUID world=UUID.randomUUID(),enemy=UUID.randomUUID();
        var eligible=EnemyRewardRegistry.load().classify(world,enemy,"Wolf_Black","Default/Zone1_Tier1/Forest_Birch",
                EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,100).orElseThrow();
        assertTrue(HytaleEncounterRewards.needsEncounterContextLoad(AddReason.SPAWN,Optional.of(eligible)));
    }
}
