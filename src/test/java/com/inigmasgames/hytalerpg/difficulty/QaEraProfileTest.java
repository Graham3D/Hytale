package com.inigmasgames.hytalerpg.difficulty;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class QaEraProfileTest {
    @TempDir Path temp;

    @Test void previewsThreeAuthoredErasWithoutChangingDurableWorldBinding(){
        var worlds=new WorldDifficultyRegistry(temp.resolve("worlds.json"));
        UUID world=UUID.randomUUID(), actor=UUID.randomUUID();
        worlds.register(new WorldDifficultyRegistry.Binding(world,"default",
                WorldDifficultyRegistry.Kind.CAMPAIGN,DifficultyId.NORMAL,
                "rpg.encounters.r031.pilot","character",true));
        var resolver=AuthoredEncounterCatalog.load().resolver(worlds);
        String biome="Default/Zone1_Spawn/Plains_Spawn";
        var normal=resolver.resolveAuthoredQa(world,actor,"Skeleton_Fighter",biome,DifficultyId.NORMAL);
        var nightmare=resolver.resolveAuthoredQa(world,actor,"Skeleton_Fighter",biome,DifficultyId.NIGHTMARE);
        var hell=resolver.resolveAuthoredQa(world,actor,"Skeleton_Fighter",biome,DifficultyId.HELL);
        assertTrue(normal.sourceCombatLevel()<nightmare.sourceCombatLevel());
        assertTrue(nightmare.sourceCombatLevel()<hell.sourceCombatLevel());
        assertEquals(DifficultyId.HELL,hell.difficulty());
        assertEquals(DifficultyId.NORMAL,worlds.require(world).difficulty());
    }
}
