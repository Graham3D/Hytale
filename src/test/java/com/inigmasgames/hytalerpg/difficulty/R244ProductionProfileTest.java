package com.inigmasgames.hytalerpg.difficulty;
import com.inigmasgames.hytalerpg.progress.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class R244ProductionProfileTest {
    @TempDir Path dir;
    @Test void allInstalledCampaignBiomesAndConcreteRegistryRolesUseProductionResolver(){
        var catalog=AuthoredEncounterCatalog.load();var registry=EnemyRewardRegistry.load();
        var worlds=new WorldDifficultyRegistry(dir.resolve("worlds.json"));
        var modes=new EnumMap<DifficultyId,UUID>(DifficultyId.class);
        for(var era:DifficultyId.values()){
            var id=UUID.randomUUID();modes.put(era,id);
            String profile=switch(era){case NORMAL->"rpg.encounters.r031.pilot";case NIGHTMARE->"rpg.encounters.nightmare.pending";case HELL->"rpg.encounters.hell.pending";};
            worlds.register(new WorldDifficultyRegistry.Binding(id,era.name(),WorldDifficultyRegistry.Kind.CAMPAIGN,era,profile,"shared",true));
        }
        var resolver=catalog.resolver(worlds);var profiles=catalog.profiles();
        var ordinary=new TreeSet<String>();registry.roles().forEach(r->ordinary.add(r.roleId()));registry.aliases().forEach(r->ordinary.add(r.roleId()));
        catalog.roles().stream().filter(r->!r.campaignRegion().isBlank()).forEach(r->ordinary.remove(r.id()));
        var observed=List.of("Snake_Cobra","Bear_Polar","Bear_Grizzly","Fox","Skeleton_Frost_Fighter", "Skeleton_Frost_Soldier_Wander",
                "Wolf_White","Spider","Skeleton_Frost_Fighter_Wander","Skeleton_Frost_Soldier","Leopard_Snow", "Skeleton_Fighter",
                "Skeleton_Frost_Mage","Skeleton_Frost_Knight","Skeleton_Frost_Knight_Wander");
        assertTrue(ordinary.containsAll(observed));assertEquals(237,CampaignBiomes.current().biomes().size());
        int count=0;
        for(var era:DifficultyId.values())for(var role:ordinary)for(var biome:CampaignBiomes.current().biomes()){
            var result=resolver.classifyNatural(registry,modes.get(era),UUID.randomUUID(),role,biome.key(),12,
                    EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,1);
            assertEquals(EncounterProfileResolver.Admission.READY,result.reason(),()->result.context().toString());
            var spawn=result.spawn().orElseThrow();assertEquals(role,spawn.roleId());assertEquals(biome.key(),spawn.biomeKey());
            assertEquals(12,result.context().nativeEnvironment());assertEquals(biome.region(),result.context().region());count++;
        }
        System.out.println("R244_PRODUCTION_CONTEXTS="+count+" concreteRoles="+ordinary.size());
        var world=modes.get(DifficultyId.NORMAL);var actor=UUID.randomUUID();
        assertEquals(EncounterProfileResolver.Admission.WORLD_NOT_READY,resolver.classifyNatural(registry,UUID.randomUUID(),actor,"Bear_Grizzly","Default/Zone1_Tier1/Plains",12,EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,1).reason());
        assertEquals(EncounterProfileResolver.Admission.BIOME_KEY_UNMAPPED,resolver.classifyNatural(registry,world,actor,"Bear_Grizzly","Default/Zone1_Tier1/Invented",12,EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,1).reason());
        assertEquals(EncounterProfileResolver.Admission.UNSUPPORTED_GENERATOR,resolver.classifyNatural(registry,world,actor,"Bear_Grizzly","Other/Zone/Plains",12,EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,1).reason());
        assertEquals(EncounterProfileResolver.Admission.REGION_UNMAPPED,resolver.classifyNatural(registry,world,actor,"Bear_Grizzly","Default/Oceans/Ocean",12,EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,1).reason());
    }
}
