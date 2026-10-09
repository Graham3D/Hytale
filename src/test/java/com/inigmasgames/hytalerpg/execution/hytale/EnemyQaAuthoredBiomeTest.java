package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.difficulty.*;
import com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EnemyQaAuthoredBiomeTest {
    @TempDir Path directory;

    @Test void exactBiomeIsPreservedAndUnlistedBiomeUsesOnlyItsAuthoredZone(){
        var registry=EnemyRewardRegistry.load();
        var zone=registry.biomes().stream().filter(b->b.zoneId().equals("Zone4_Tier4")).findFirst().orElseThrow();
        assertEquals(zone.key(),HytaleEncounterRewards.qaAuthoredBiome(registry,zone.key()).orElseThrow());
        assertEquals(zone.key(),HytaleEncounterRewards.qaAuthoredBiome(registry,
                "Default/Zone4_Tier4/QA_Unlisted").orElseThrow());
        assertEquals("Default/Zone1_Spawn/Plains_Spawn",
                HytaleEncounterRewards.qaAuthoredBiome(registry,"CUSTOM_WORLDGEN_PATH_UNAUDITED").orElseThrow());
        assertEquals("Default/Zone1_Spawn/Plains_Spawn",
                HytaleEncounterRewards.qaAuthoredBiome(registry,"Default/Unknown_Zone/QA_Unlisted").orElseThrow());
    }

    @Test void selectedLarvaBiomeResolvesTheExistingProfileInEveryCampaignMode(){
        var registry=EnemyRewardRegistry.load();
        var biome=HytaleEncounterRewards.qaAuthoredBiome(registry,
                "Default/Zone4_Tier4/QA_Unlisted").orElseThrow();
        var worlds=new WorldDifficultyRegistry(directory.resolve("worlds.json"));
        for(var mode:DifficultyId.values()){
            var world=UUID.randomUUID();
            var profile=mode==DifficultyId.NORMAL?registry.profileId():
                    "rpg.encounters."+mode.name().toLowerCase(Locale.ROOT)+".pending";
            worlds.register(new WorldDifficultyRegistry.Binding(world,mode.name(),
                    WorldDifficultyRegistry.Kind.CAMPAIGN,mode,profile,"server-character",true));
            var resolved=AuthoredEncounterCatalog.load().resolver(worlds)
                    .resolveAuthored(world,UUID.randomUUID(),"Larva_Void",biome);
            assertEquals(mode,resolved.difficulty());
            assertEquals(biome,resolved.biomeKey());
            assertTrue(resolved.sourceCombatLevel()>0);
        }
    }
}
