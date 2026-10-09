package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.difficulty.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.progress.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GearMultiPickDeliveryTest {
    @TempDir Path root;
    private static GearDropGenerator generator(){return new GearDropGenerator(GearCatalog.load(),new GearBindings(),GearAffixRuntime.ENABLED);}
    @Test void bossPicksHaveIndependentDurableCustodyAcrossRetryAndRestart() {
        UUID world=UUID.nameUUIDFromBytes("multi-pick-world".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID player=UUID.nameUUIDFromBytes("multi-pick-owner".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID enemy=null;
        for(int i=0;i<100;i++){
            UUID candidate=UUID.nameUUIDFromBytes(("multi-pick-boss-"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String event="enemy-death/"+world+"/"+candidate;
            if(GearLootProfiles.CURRENT.decide("BossRole",ProgressionMath.Rank.BOSS,event).succeeded()>=2){enemy=candidate;break;}
        }
        assertNotNull(enemy);
        var combat=new EncounterProfileResolver.Resolved(world,enemy,DifficultyId.HELL,"fixture","fixture","BossRole","biome",95,100,10,1,1,
                MonsterResistanceProfile.NONE,EncounterProfileResolver.Evidence.FIXTURE_ONLY);
        var spawn=new EnemyRewardRegistry.Spawn(world,enemy,"BossRole","boss","biome",95,
                ProgressionMath.Rank.BOSS,ProgressionMath.Rarity.ORDINARY,"fixture",1000,null,combat);
        long xp=ProgressionMath.enemyReward(95,spawn.rank(),spawn.rarity(),95);
        var plan=new EncounterContributions.DeathPlan(spawn,Vec3.ZERO,2000,
                List.of(new EncounterContributions.Share(player,xp,spawn.rank().insight,95,1)));
        List<GearLootService.Loot> first;
        try(var store=new FileEncounterStore(root)){
            var service=new GearLootService(store,generator(),()->3000);
            service.contribute(world,enemy,player,GearClaims.Policy.solo(player),1500);
            service.freezeMagicFind(spawn.eventId(),Map.of(player,1.60));
            first=service.deliverPicks(plan);
            assertEquals(first,service.deliverPicks(plan));
            assertEquals(first.size(),first.stream().map(GearLootService.Loot::source).map(EnemyRewardRegistry.LootSource::eventId).distinct().count());
            assertTrue(first.stream().allMatch(row->row.source().eventId().startsWith(spawn.eventId()+"/gear-pick/")));
            assertTrue(service.inspect(spawn.eventId()).isEmpty(),"Parent is a decision, never an item receipt");
            assertEquals(first.size(),service.records().size());
            assertTrue(first.stream().allMatch(row->row.state().equals("WORLD")&&row.result().item()!=null));
            assertTrue(first.size()>=2,"fixture seed should exercise multi-item boss delivery");
        }
        try(var store=new FileEncounterStore(root)){
            var service=new GearLootService(store,generator(),()->9000);
            assertEquals(first,service.deliverPicks(plan));
            assertEquals(first.size(),service.records().size());
            assertEquals(first.stream().map(row->row.result().item().identity()).distinct().count(),first.size());
        }
    }
    @Test void failedOptionalPickSavesOnlyTheDeathDecision() {
        UUID world=UUID.nameUUIDFromBytes("dry-world".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID player=UUID.nameUUIDFromBytes("dry-owner".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID enemy=null;
        for(int i=0;i<100;i++){
            UUID candidate=UUID.nameUUIDFromBytes(("dry-enemy-"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if(GearLootProfiles.CURRENT.decide("CommonRole",ProgressionMath.Rank.COMMON,
                    "enemy-death/"+world+"/"+candidate).succeeded()==0){enemy=candidate;break;}
        }
        assertNotNull(enemy);
        var combat=new EncounterProfileResolver.Resolved(world,enemy,DifficultyId.NORMAL,"fixture","fixture","CommonRole","biome",25,100,10,1,1,
                MonsterResistanceProfile.NONE,EncounterProfileResolver.Evidence.FIXTURE_ONLY);
        var spawn=new EnemyRewardRegistry.Spawn(world,enemy,"CommonRole","common","biome",25,
                ProgressionMath.Rank.COMMON,ProgressionMath.Rarity.ORDINARY,"fixture",1000,null,combat);
        var plan=new EncounterContributions.DeathPlan(spawn,Vec3.ZERO,2000,
                List.of(new EncounterContributions.Share(player,
                        ProgressionMath.enemyReward(25,spawn.rank(),spawn.rarity(),25),spawn.rank().insight,25,1)));
        try(var store=new FileEncounterStore(root)){
            var service=new GearLootService(store,generator(),()->3000);
            service.contribute(world,enemy,player,GearClaims.Policy.solo(player),1500);
            assertTrue(service.deliverPicks(plan).isEmpty());
            assertTrue(service.deliverPicks(plan).isEmpty());
            assertTrue(service.inspect(spawn.eventId()).isEmpty());
            assertTrue(service.inspect(spawn.eventId()+"/gear-pick/1").isEmpty());
            assertTrue(service.records().isEmpty());
            assertEquals(0,store.gearRead("loot-picks",spawn.eventId(),GearLootService.DeathPicks.class)
                    .orElseThrow().decision().succeeded());
        }
        try(var store=new FileEncounterStore(root)){
            var service=new GearLootService(store,generator(),()->9000);
            assertTrue(service.deliverPicks(plan).isEmpty());
            assertTrue(service.records().isEmpty());
        }
    }
    @Test void interruptedFirstPickReplaysFrozenPlanWithoutAddingPicks() {
        UUID world=UUID.nameUUIDFromBytes("interrupted-multi-world".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID player=UUID.nameUUIDFromBytes("interrupted-multi-owner".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID enemy=null;
        for(int i=0;i<100;i++){
            UUID candidate=UUID.nameUUIDFromBytes(("interrupted-boss-"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if(GearLootProfiles.CURRENT.decide("BossRole",ProgressionMath.Rank.BOSS,
                    "enemy-death/"+world+"/"+candidate).succeeded()>=2){enemy=candidate;break;}
        }
        assertNotNull(enemy);
        var combat=new EncounterProfileResolver.Resolved(world,enemy,DifficultyId.HELL,"fixture","fixture","BossRole","biome",95,100,10,1,1,
                MonsterResistanceProfile.NONE,EncounterProfileResolver.Evidence.FIXTURE_ONLY);
        var spawn=new EnemyRewardRegistry.Spawn(world,enemy,"BossRole","boss","biome",95,
                ProgressionMath.Rank.BOSS,ProgressionMath.Rarity.ORDINARY,"fixture",1000,null,combat);
        var plan=new EncounterContributions.DeathPlan(spawn,Vec3.ZERO,2000,
                List.of(new EncounterContributions.Share(player,
                        ProgressionMath.enemyReward(95,spawn.rank(),spawn.rarity(),95),spawn.rank().insight,95,1)));
        try(var store=new FileEncounterStore(root)){
            var service=new GearLootService(store,generator(),()->3000);
            service.contribute(world,enemy,player,GearClaims.Policy.solo(player),1500);
            service.freezeMagicFind(spawn.eventId(),Map.of(player,1.60));
            store.configureGearFault(boundary->{if(boundary.equals("AFTER_loot"))throw new IllegalStateException("simulated interruption");});
            assertThrows(IllegalStateException.class,()->service.deliverPicks(plan));
        }
        try(var store=new FileEncounterStore(root)){
            var service=new GearLootService(store,generator(),()->9000);
            var committed=service.deliverPicks(plan);
            assertTrue(committed.size()>=2);
            assertEquals(committed,service.deliverPicks(plan));
            assertEquals(committed.size(),service.records().size());
            assertEquals(committed.size(),committed.stream().map(row->row.result().item().identity()).distinct().count());
            assertTrue(committed.stream().allMatch(row->row.state().equals("WORLD")&&row.magicFind()==1.60));
        }
    }
}
