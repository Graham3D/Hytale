package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry;
import com.inigmasgames.hytalerpg.progress.FileEncounterStore;
import com.inigmasgames.hytalerpg.progress.ProgressionMath;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class IronSentinelPlayerDropCustodyTest {
    @TempDir Path root;
    private static GearLootService service(FileEncounterStore store){
        return new GearLootService(store,new GearDropGenerator(GearCatalog.load(),new GearBindings(),GearAffixRuntime.ENABLED));
    }
    private static GearInstance iron(boolean qaOnly){
        var base=GearCatalog.load().base("gm.sword_iron.n");
        var qa=GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.COMMON,List.of(),BigDecimal.ZERO);
        if(qaOnly)return qa;
        return new GearInstance(qa.schemaVersion(),qa.identity(),qa.definitionRevision(),qa.baseId(),qa.baseName(),
                qa.category(),qa.sourceEra(),qa.itemLevel(),qa.rarity(),qa.intrinsicThousandths(),qa.intrinsicStats(),
                qa.requirements(),qa.affixes(),"natural-drop-test",false);
    }
    private static GearInstance qa(String baseId){
        var base=GearCatalog.load().base(baseId);
        return GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.COMMON,List.of(),BigDecimal.ZERO);
    }
    @Test void spatialQaDropReceiptCanBeReleasedOrAcknowledged() {
        UUID owner=UUID.randomUUID(),world=UUID.randomUUID();var item=iron(true);
        try(var store=new FileEncounterStore(root.resolve("spatial-qa"))){
            var loot=service(store);
            var first=loot.beginIronDrop(null,owner,world,new Vec3(3,70,0),item,true);
            assertEquals("PLAYER_SPATIAL_DROP_QA",first.reason());
            assertEquals("DROP_ABORTED",loot.releaseIronDrop(first.source().eventId(),owner,item.identity()).state());
            var second=loot.beginIronDrop(null,owner,world,new Vec3(3,70,0),item,true);
            assertEquals("WORLD",loot.acknowledgeIronDrop(second.source().eventId(),owner,item.identity()).state());
        }
    }
    @Test void naturalSpatialDropRollbackReturnsToSpatialBag() {
        UUID owner=UUID.randomUUID(),world=UUID.randomUUID();var item=iron(false);
        String event="spatial-source/"+UUID.randomUUID();long now=System.currentTimeMillis();
        var source=new EnemyRewardRegistry.LootSource(event,world,UUID.randomUUID(),item.sourceEra(),
                item.itemLevel(),"test",ProgressionMath.Rank.COMMON,ProgressionMath.Rarity.ORDINARY,"test");
        var allocation=new GearClaims.Allocation(GearClaims.Mode.SOLO,"solo/"+owner,0,List.of(owner),owner,owner,owner,
                now,now+300_000,1,owner,List.of());
        var initial=new GearLootService.Loot(source,Vec3.ZERO,allocation,0,"natural",
                new GearDropGenerator.Result(item,Map.of(),Map.of(),"GENERATED_CANDIDATE"),
                "SPATIAL_BAG","natural","test");
        try(var store=new FileEncounterStore(root.resolve("spatial-natural"))){
            store.gearTransaction("loot",event,GearLootService.Loot.class,old->old.orElse(initial));
            var loot=service(store);
            assertEquals("DROP_PENDING_NATIVE_SAVE",loot.beginIronDrop(event,owner,world,new Vec3(3,70,0),item,true).state());
            assertEquals("SPATIAL_BAG",loot.releaseIronDrop(event,owner,item.identity()).state());
        }
    }
    @Test void cobaltDaggersAndArmorRetainIdentityAcrossDropAndForge(){
        for(String baseId:List.of("gm.daggers_cobalt.n","gm.plate_iron.head.n")){
            UUID owner=UUID.randomUUID(),world=UUID.randomUUID();var item=qa(baseId);
            try(var store=new FileEncounterStore(root.resolve(baseId.replace('.','-')))){
                var loot=service(store);var point=new Vec3(3,70,0);
                var pending=loot.beginIronDrop(null,owner,world,point,item);
                String event=pending.source().eventId();
                assertEquals(item,loot.acknowledgeIronDrop(event,owner,item.identity()).result().item());
                var binding=loot.prepareSentinel(event,owner,0,System.currentTimeMillis(),item.identity(),
                        UUID.randomUUID(),world,point,1,2);
                assertEquals(item,binding.boundItem());
                assertEquals("SENTINEL_BOUND",loot.acknowledgeForge(event,owner,item.identity(),binding.instanceId()).state());
                assertThrows(IllegalArgumentException.class,()->loot.reservePickup(event,owner,1,System.currentTimeMillis(),true));
            }
        }
    }
    @Test void qaInventoryDropReservesBeforeRemovalAndForgesExactlyOnceAcrossRestart(){
        UUID owner=UUID.randomUUID(),world=UUID.randomUUID();var item=iron(true);var point=new Vec3(3,70,0);
        String event;
        try(var store=new FileEncounterStore(root)){
            var loot=service(store);var pending=loot.beginIronDrop(null,owner,world,point,item);
            event=pending.source().eventId();
            assertEquals("DROP_PENDING_NATIVE_SAVE",pending.state());
            assertThrows(IllegalArgumentException.class,()->loot.reservePickup(event,owner,0,System.currentTimeMillis(),true));
            assertThrows(IllegalArgumentException.class,()->loot.prepareSentinel(event,owner,0,System.currentTimeMillis(),
                    item.identity(),UUID.randomUUID(),world,point,1,2));
        }
        try(var store=new FileEncounterStore(root)){
            var loot=service(store);
            assertEquals("DROP_PENDING_NATIVE_SAVE",loot.inspect(event).orElseThrow().state());
            var ground=loot.acknowledgeIronDrop(event,owner,item.identity());
            assertEquals("WORLD",ground.state());
            assertThrows(IllegalArgumentException.class,()->loot.acknowledgeIronDrop(event,owner,item.identity()));
            var binding=loot.prepareSentinel(event,owner,0,System.currentTimeMillis(),item.identity(),
                    UUID.randomUUID(),world,point,1,2);
            assertEquals(item,binding.boundItem());
            assertEquals("SENTINEL_BOUND",loot.acknowledgeForge(event,owner,item.identity(),binding.instanceId()).state());
            assertThrows(IllegalArgumentException.class,()->loot.reservePickup(event,owner,1,System.currentTimeMillis(),true));
            assertThrows(IllegalArgumentException.class,()->loot.beginIronDrop(event,owner,world,point,item));
        }
    }
    @Test void savedInventoryRollsBackPendingDropWithoutMintingGroundItem(){
        UUID owner=UUID.randomUUID(),world=UUID.randomUUID();var item=iron(true);
        String event;
        try(var store=new FileEncounterStore(root)){
            event=service(store).beginIronDrop(null,owner,world,new Vec3(3,70,0),item).source().eventId();
        }
        try(var store=new FileEncounterStore(root)){
            var loot=service(store);
            assertEquals("DROP_ABORTED",loot.releaseIronDrop(event,owner,item.identity()).state());
            assertThrows(IllegalArgumentException.class,()->loot.acknowledgeIronDrop(event,owner,item.identity()));
            assertThrows(IllegalArgumentException.class,()->loot.reserveForge(event,owner,0,System.currentTimeMillis(),item.identity()));
        }
    }
    @Test void naturalGearRetainsItsExactSourceReceiptThroughDropAndRollback(){
        UUID owner=UUID.randomUUID(),world=UUID.randomUUID(),enemy=UUID.randomUUID();var item=iron(false);
        String event="enemy-death/"+world+"/"+enemy;var point=new Vec3(3,70,0);long now=System.currentTimeMillis();
        var source=new EnemyRewardRegistry.LootSource(event,world,enemy,item.sourceEra(),item.itemLevel(),
                "test",ProgressionMath.Rank.COMMON,ProgressionMath.Rarity.ORDINARY,"test");
        var allocation=new GearClaims.Allocation(GearClaims.Mode.SOLO,"solo/"+owner,0,List.of(owner),owner,owner,owner,
                now,now+300_000,1,owner,List.of());
        var initial=new GearLootService.Loot(source,Vec3.ZERO,allocation,0,"natural",
                new GearDropGenerator.Result(item,Map.of(),Map.of(),"GENERATED_CANDIDATE"),"INVENTORY","natural","test");
        try(var store=new FileEncounterStore(root)){
            store.gearTransaction("loot",event,GearLootService.Loot.class,old->old.orElse(initial));
            var loot=service(store);
            assertEquals("DROP_PENDING_NATIVE_SAVE",loot.beginIronDrop(event,owner,world,point,item).state());
            assertThrows(IllegalArgumentException.class,()->loot.beginIronDrop(event,owner,world,point,item));
            assertThrows(IllegalArgumentException.class,()->loot.releaseIronDrop(event,UUID.randomUUID(),item.identity()));
        }
        try(var store=new FileEncounterStore(root)){
            var loot=service(store);
            assertEquals("INVENTORY",loot.releaseIronDrop(event,owner,item.identity()).state());
            assertEquals(event,loot.inspect(event).orElseThrow().source().eventId());
            assertEquals("DROP_PENDING_NATIVE_SAVE",loot.beginIronDrop(event,owner,world,point,item).state());
            assertEquals("WORLD",loot.acknowledgeIronDrop(event,owner,item.identity()).state());
            assertEquals(item,loot.inspect(event).orElseThrow().result().item());
        }
    }
}
