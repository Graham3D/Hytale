package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.summon.IronSentinelBinding;
import com.inigmasgames.hytalerpg.progress.FileEncounterStore;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class IronSentinelReplacementCustodyTest {
    @TempDir Path directory;
    private final UUID owner=UUID.randomUUID(),world=UUID.randomUUID();
    private final Vec3 point=new Vec3(4,70,0);
    private GearLootService service(FileEncounterStore store){return new GearLootService(store,
            new GearDropGenerator(GearCatalog.load(),new GearBindings(),GearAffixRuntime.ENABLED));}
    private GearInstance item(){var base=GearCatalog.load().base("gm.sword_iron.n");
        return GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.COMMON,List.of(),BigDecimal.ZERO);}
    private record First(String event,UUID instance,GearInstance item){}
    private First active(GearLootService loot){
        var item=item();var event=loot.publishSentinelQa(owner,world,point,item).source().eventId();
        var instance=UUID.randomUUID();var bound=loot.prepareSentinel(event,owner,0,System.currentTimeMillis(),
                item.identity(),instance,world,point,4,2);
        loot.acknowledgeForge(event,owner,item.identity(),instance);
        loot.sentinelState(owner,instance,IronSentinelBinding.State.PREPARED,IronSentinelBinding.State.ACTIVE,
                bound.currentHealth(),world,point);
        return new First(event,instance,item);
    }
    @Test void successfulReplacementKeepsBothExactSourcesBoundAndOnlyTheNewBindingActive(){
        try(var store=new FileEncounterStore(directory)){
            var loot=service(store);var old=active(loot);var next=item();
            var event=loot.publishSentinelQa(owner,world,point,next).source().eventId();var instance=UUID.randomUUID();
            var prepared=loot.prepareReplacingSentinel(event,owner,0,System.currentTimeMillis(),next.identity(),
                    instance,world,point,8,2,1.2);
            assertEquals(old.instance(),loot.replacementBackup(instance).orElseThrow().instanceId());
            assertEquals("SENTINEL_BOUND",loot.inspect(old.event()).orElseThrow().state());
            assertEquals("FORGE_PENDING",loot.inspect(event).orElseThrow().state());
            loot.acknowledgeForge(event,owner,next.identity(),instance);
            loot.sentinelState(owner,instance,IronSentinelBinding.State.PREPARED,IronSentinelBinding.State.ACTIVE,
                    prepared.currentHealth(),world,point);
            assertEquals(instance,loot.sentinel(owner).orElseThrow().instanceId());
            assertEquals("SENTINEL_BOUND",loot.inspect(old.event()).orElseThrow().state());
            assertEquals("SENTINEL_BOUND",loot.inspect(event).orElseThrow().state());
        }
    }
    @Test void failedReplacementReturnsOnlyTheNewSourceAndPreservesTheOldBinding(){
        try(var store=new FileEncounterStore(directory)){
            var loot=service(store);var old=active(loot);var next=item();
            var event=loot.publishSentinelQa(owner,world,point,next).source().eventId();var instance=UUID.randomUUID();
            loot.prepareReplacingSentinel(event,owner,0,System.currentTimeMillis(),next.identity(),instance,world,point,8,2,1);
            loot.abortPreparedReplacement(owner,instance,true);
            assertEquals(old.instance(),loot.sentinel(owner).orElseThrow().instanceId());
            assertEquals(IronSentinelBinding.State.ACTIVE,loot.sentinel(owner).orElseThrow().state());
            assertEquals("WORLD",loot.inspect(event).orElseThrow().state());
            assertEquals("SENTINEL_BOUND",loot.inspect(old.event()).orElseThrow().state());
        }
    }
    @Test void restartDuringPreparationRestoresOldBindingAndReturnsTheNewSource(){
        String event;UUID oldInstance;
        try(var store=new FileEncounterStore(directory)){
            var loot=service(store);var old=active(loot);oldInstance=old.instance();var next=item();
            event=loot.publishSentinelQa(owner,world,point,next).source().eventId();
            loot.prepareReplacingSentinel(event,owner,0,System.currentTimeMillis(),next.identity(),UUID.randomUUID(),world,point,8,2,1);
        }
        try(var store=new FileEncounterStore(directory)){
            var loot=service(store);var restored=loot.claimSentinelRestore(owner,world,point).orElseThrow();
            assertEquals(oldInstance,restored.instanceId());
            assertEquals(IronSentinelBinding.State.RESTORING,restored.state());
            assertEquals("WORLD",loot.inspect(event).orElseThrow().state());
        }
    }
    @Test void restartAfterNewSourceIsBoundRestoresOnlyTheNewCompanion(){
        String oldEvent,newEvent;UUID incoming;
        try(var store=new FileEncounterStore(directory)){
            var loot=service(store);var old=active(loot);oldEvent=old.event();var next=item();
            newEvent=loot.publishSentinelQa(owner,world,point,next).source().eventId();incoming=UUID.randomUUID();
            loot.prepareReplacingSentinel(newEvent,owner,0,System.currentTimeMillis(),next.identity(),incoming,world,point,8,2,1);
            loot.acknowledgeForge(newEvent,owner,next.identity(),incoming);
        }
        try(var store=new FileEncounterStore(directory)){
            var loot=service(store);var restored=loot.claimSentinelRestore(owner,world,point).orElseThrow();
            assertEquals(incoming,restored.instanceId());
            assertEquals(IronSentinelBinding.State.RESTORING,restored.state());
            assertEquals("SENTINEL_BOUND",loot.inspect(oldEvent).orElseThrow().state());
            assertEquals("SENTINEL_BOUND",loot.inspect(newEvent).orElseThrow().state());
        }
    }
    @Test void strandedActiveBindingCanBeReplacedWithoutAProjectedActor(){
        try(var store=new FileEncounterStore(directory)){
            var loot=service(store);var old=active(loot);var next=item();
            var event=loot.publishSentinelQa(owner,world,point,next).source().eventId();var instance=UUID.randomUUID();
            var prepared=loot.prepareReplacingSentinel(event,owner,0,System.currentTimeMillis(),next.identity(),
                    instance,world,point,8,2,1);
            loot.acknowledgeForge(event,owner,next.identity(),instance);
            loot.sentinelState(owner,instance,IronSentinelBinding.State.PREPARED,IronSentinelBinding.State.ACTIVE,
                    prepared.currentHealth(),world,point);
            assertEquals(instance,loot.sentinel(owner).orElseThrow().instanceId());
            assertEquals("SENTINEL_BOUND",loot.inspect(old.event()).orElseThrow().state());
            assertEquals("SENTINEL_BOUND",loot.inspect(event).orElseThrow().state());
        }
    }
    @Test void voluntaryDismissalPreventsRestoreAndNeverReturnsTheForgedItem(){
        try(var store=new FileEncounterStore(directory)){
            var loot=service(store);var old=active(loot);
            loot.endSentinel(owner,old.instance(),true,loot.sentinel(owner).orElseThrow().currentHealth(),world,point);
            assertTrue(loot.claimSentinelRestore(owner,world,point).isEmpty());
            assertEquals("SENTINEL_BOUND",loot.inspect(old.event()).orElseThrow().state());
            assertEquals(IronSentinelBinding.State.DEAD,loot.sentinel(owner).orElseThrow().state());
        }
    }
}
