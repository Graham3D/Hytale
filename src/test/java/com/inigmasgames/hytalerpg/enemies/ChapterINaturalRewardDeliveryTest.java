package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.difficulty.*;
import com.inigmasgames.hytalerpg.gear.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.progress.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

/** Production death persistence, pick delivery and custody owners composed across real disk restarts. */
class ChapterINaturalRewardDeliveryTest {
    @TempDir Path root;
    private static final UUID WORLD=UUID.fromString("3d366b79-f367-419a-87b7-a8d53cde7ec0");
    private static final UUID OWNER=new UUID(9,1),OTHER=new UUID(9,2);
    private static GearDropGenerator generator(){return new GearDropGenerator(GearCatalog.load(),new GearBindings(),GearAffixRuntime.ENABLED);}
    private record Birth(EnemyBirthPlan plan,EnemyRewardRegistry.Spawn spawn){}
    private static Birth birth(EnemyRarity rarity,boolean opportunity){
        var fixture=new EnemyBirthPlannerTest();var gson=new com.google.gson.Gson();
        for(int i=0;i<10000;i++){
            String seed="stage4-natural/"+i;
            if(fixture.planner.rarity(seed,DifficultyId.NORMAL)!=rarity)continue;
            var original=fixture.request(seed,DifficultyId.NORMAL);final int attempt=i;
            java.util.function.Function<EnemyBirthPlanner.Candidate,EnemyBirthPlanner.Candidate> reidentify=c->{
                var json=gson.toJsonTree(c.nativeBaseline()).getAsJsonObject();json.addProperty("worldId",WORLD.toString());
                json.addProperty("entityId",EnemyBirthPlannerTest.id(c.nativeBaseline().entityId()+"/"+attempt).toString());
                json.addProperty("combatLevel",25);
                return new EnemyBirthPlanner.Candidate(gson.fromJson(json,EnemyDescriptor.class),c.binding(),1);
            };
            var request=new EnemyBirthPlanner.Request(seed,original.packId(),Vec3.ZERO,original.nativePlanReceipt(),
                    original.originals().stream().map(reidentify).toList(),original.additionalSlots().stream().map(reidentify).toList(),true,6,true);
            var result=fixture.planner.plan(request);var plan=result.plan();var actor=plan.actors().getFirst();
            if(actor.enemyRarity()!=rarity||actor.own(EnemyAffixRegistry.Operator.PACKBOUND).isPresent())continue;
            var candidate=spawn(actor,rarity!=EnemyRarity.NORMAL);
            if((GearLootProfiles.CURRENT.decide(candidate.roleId(),candidate.rank(),candidate.eventId()).succeeded()>0)==opportunity)return new Birth(plan,candidate);
        }
        throw new AssertionError("Existing profile must admit both deterministic opportunity outcomes");
    }
    private static EnemyRewardRegistry.Spawn spawn(EnemyDescriptor actor,boolean frozen){
        var combat=new EncounterProfileResolver.Resolved(WORLD,actor.entityId(),actor.difficulty(),actor.sourceValidationId(),"fixture",actor.nativeRoleId(),"Default/Zone1_Tier1/Forest_Birch",actor.combatLevel(),100,10,1,1,
                MonsterResistanceProfile.NONE,EncounterProfileResolver.Evidence.FIXTURE_ONLY);
        return new EnemyRewardRegistry.Spawn(WORLD,actor.entityId(),actor.nativeRoleId(),"trork_warrior",combat.biomeKey(),actor.combatLevel(),
                actor.encounterRank(),ProgressionMath.Rarity.ORDINARY,actor.sourceValidationId(),1000,null,combat,frozen?actor.immutableRewardContext():null);
    }
    private static EncounterContributions.DeathPlan death(FileEncounterStore disk,GearLootService loot,Birth birth){
        var spawn=birth.spawn();var plan=birth.plan();
        if(plan.pack()!=null){
            var sources=plan.actors().stream().map(actor->spawn(actor,false)).toList();
            disk.reserveEnemyBirthRoot(new EnemyBirthRoot(plan,sources.stream().filter(s->plan.originalNativeEntities().contains(s.enemy())).toList(),sources));
            disk.transitionEnemyPack(WORLD,plan.pack().packId(),EnemyPackRecord::staged);
            disk.transitionEnemyPack(WORLD,plan.pack().packId(),EnemyPackRecord::publish);
        }
        try(var encounters=new PersistentEncounterRuntime(disk,(owner,reward)->fail("Rewards must use the durable drain"))){
            assertTrue(encounters.attach(WORLD,spawn.enemy(),spawn.roleId(),Optional.of(spawn)));
            assertTrue(encounters.damage(WORLD,spawn.enemy(),OWNER,100,0,100,true,1500));
            loot.contribute(WORLD,spawn.enemy(),OWNER,GearClaims.Policy.solo(OWNER),1500);
            var prepared=encounters.prepareDeath(WORLD,spawn.enemy(),Vec3.ZERO,2000).orElseThrow();
            loot.freezeMagicFind(spawn.eventId(),Map.of(OWNER,1.25));
            return encounters.finishDeath(prepared,List.of(new EncounterContributions.Participant(OWNER,WORLD,Vec3.ZERO,spawn.level(),true,null)));
        }
    }

    @ParameterizedTest @EnumSource(value=EnemyRarity.class,names={"NORMAL","CHAMPION","UNIQUE"})
    void naturalDeathRewardCursorAndFrozenItemsRecoverAfterEveryGearPublicationBoundary(EnemyRarity rarity){
        for(String boundary:List.of("AFTER_loot-picks","AFTER_loot","AFTER_cursors")){
            Path folder=root.resolve(boundary);var birth=birth(rarity,true);var spawn=birth.spawn();var awards=new AtomicInteger();
            EncounterContributions.DeathPlan plan;
            try(var disk=new FileEncounterStore(folder)){
                var loot=new GearLootService(disk,generator(),()->3000);plan=death(disk,loot,birth);
                assertEquals(1,plan.shares().size());assertTrue(plan.shares().getFirst().xp()>0);
                disk.configureGearDelivery(loot::deliverPicks);
                disk.configureGearFault(at->{if(at.equals(boundary))throw new IllegalStateException("Interrupted "+at);});
                assertThrows(IllegalStateException.class,()->disk.drain(256,(owner,reward)->{assertEquals(OWNER,owner);awards.incrementAndGet();}));
                assertEquals(1,awards.get());
                assertThrows(IllegalStateException.class,disk::pendingCount,"An interrupted disk transaction must stay fail-closed until restart");
            }
            List<GearLootService.Loot> frozen;
            try(var disk=new FileEncounterStore(folder)){
                var loot=new GearLootService(disk,generator(),()->9000);disk.configureGearDelivery(loot::deliverPicks);
                assertEquals(1,disk.pendingCount());
                assertEquals(0,disk.drain(256,(owner,reward)->fail("Frozen XP cursor must not replay on gear retry")));
                assertEquals(0,disk.pendingCount());assertEquals(plan,disk.death(WORLD,spawn.enemy()).orElseThrow());
                frozen=loot.deliverPicks(plan);assertFalse(frozen.isEmpty());
                assertEquals(frozen,loot.deliverPicks(plan));assertEquals(frozen.size(),loot.records().size());
                assertTrue(frozen.stream().allMatch(row->row.state().equals("WORLD")&&row.magicFind()==1.25&&!row.result().item().qaOnly()));
                assertEquals(frozen.size(),frozen.stream().map(row->row.result().item().identity()).distinct().count());
                for(var row:frozen){
                    String event=row.source().eventId();long revision=row.allocation().revision();
                    assertEquals(spawn.level(),row.result().item().itemLevel());
                    assertTrue(new GearBindings().require(row.result().item().baseId()).mapped());
                    assertThrows(IllegalArgumentException.class,()->loot.reservePickup(event,OWNER,revision,9100,false));
                    assertThrows(IllegalArgumentException.class,()->loot.reservePickup(event,OTHER,revision,9100,true));
                    assertEquals(row,loot.inspect(event).orElseThrow());
                    loot.reservePickup(event,OWNER,revision,9100,true);
                }
            }
            // Simulate disconnect before inventory publication: explicit recovery returns the same items to WORLD.
            try(var disk=new FileEncounterStore(folder)){
                var loot=new GearLootService(disk,generator(),()->10000);
                for(var row:frozen){
                    String event=row.source().eventId();assertEquals("PICKUP_PENDING_NATIVE_SAVE",loot.inspect(event).orElseThrow().state());
                    var released=loot.releaseFullPickup(event,OWNER);assertEquals(row.result(),released.result());
                    var pending=loot.reservePickup(event,OWNER,released.allocation().revision(),10000,true);
                    assertEquals("PICKUP_PENDING_NATIVE_SAVE",pending.state());
                    assertThrows(IllegalArgumentException.class,()->loot.acknowledgePickup(event,OWNER,UUID.randomUUID()));
                    // Adapter acknowledgement is called only after its player save; no native save is claimed by this fixture.
                    var saved=loot.acknowledgePickup(event,OWNER,row.result().item().identity());
                    assertEquals(saved,loot.acknowledgePickup(event,OWNER,row.result().item().identity()));
                }
            }
            try(var disk=new FileEncounterStore(folder)){
                var loot=new GearLootService(disk,generator(),()->12000);var replay=loot.deliverPicks(plan);
                assertEquals(frozen.stream().map(GearLootService.Loot::result).toList(),replay.stream().map(GearLootService.Loot::result).toList());
                assertTrue(replay.stream().allMatch(row->row.state().equals("INVENTORY")));
                assertEquals(frozen.size(),loot.records().size());assertEquals(1,awards.get());
            }
        }
    }

    @Test void legitimateNoDropCompletesXpAndItsEmptyManifestSurvivesRestart(){
        var birth=birth(EnemyRarity.NORMAL,false);var spawn=birth.spawn();EncounterContributions.DeathPlan plan;
        try(var disk=new FileEncounterStore(root)){
            var loot=new GearLootService(disk,generator(),()->3000);plan=death(disk,loot,birth);
            disk.configureGearDelivery(loot::deliverPicks);var awards=new AtomicInteger();
            assertEquals(1,disk.drain(256,(owner,reward)->awards.incrementAndGet()));
            assertEquals(1,awards.get());assertEquals(0,disk.pendingCount());assertTrue(loot.records().isEmpty());
        }
        try(var disk=new FileEncounterStore(root)){
            var loot=new GearLootService(disk,generator(),()->9000);
            assertTrue(loot.deliverPicks(plan).isEmpty());assertTrue(loot.records().isEmpty());
            assertEquals(0,disk.gearRead("loot-picks",spawn.eventId(),GearLootService.DeathPicks.class).orElseThrow().decision().succeeded());
            assertEquals(0,disk.drain(256,(owner,reward)->fail("NO_DROP must not hold XP pending")));
        }
    }
}
