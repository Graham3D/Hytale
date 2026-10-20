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

final class IronSentinelPersistenceTest {
    @TempDir Path directory;
    private final GearCatalog catalog=GearCatalog.load();
    private final UUID owner=UUID.randomUUID(),sourceWorld=UUID.randomUUID(),otherWorld=UUID.randomUUID();
    private final Vec3 point=new Vec3(4,70,0),returnPoint=new Vec3(19,80,2);
    private GearLootService service(FileEncounterStore store){
        return new GearLootService(store,new GearDropGenerator(catalog,new GearBindings(),GearAffixRuntime.ENABLED));
    }
    private GearInstance item(){var base=catalog.base("gm.sword_iron.n");return GearInstance.authoredQa(base,UUID.randomUUID(),
            base.sourceWindow().getLast(),1000,GearRarity.COMMON,List.of(),BigDecimal.ZERO);}

    @Test void interruptedPrepareReturnsTheExactSourceInsteadOfInventingACompanion(){
        var item=item();String event;UUID instance=UUID.randomUUID();
        try(var store=new FileEncounterStore(directory)){
            var loot=service(store);event=loot.publishSentinelQa(owner,sourceWorld,point,item).source().eventId();
            loot.prepareSentinel(event,owner,0,System.currentTimeMillis(),item.identity(),instance,sourceWorld,point,7,2,1.25);
            assertEquals("FORGE_PENDING",loot.inspect(event).orElseThrow().state());
        }
        try(var store=new FileEncounterStore(directory)){
            var loot=service(store);
            assertTrue(loot.claimSentinelRestore(owner,otherWorld,returnPoint).isEmpty());
            assertEquals(IronSentinelBinding.State.ABORTED,loot.sentinel(owner).orElseThrow().state());
            assertEquals("WORLD",loot.inspect(event).orElseThrow().state());
            assertEquals(item,loot.inspect(event).orElseThrow().result().item());
        }
    }

    @Test void boundAckCrashRestoreCheckpointTransferRestartAndTrueDeathNeverDuplicateTheItem(){
        var item=item();String event;UUID instance=UUID.randomUUID();
        try(var store=new FileEncounterStore(directory)){
            var loot=service(store);event=loot.publishSentinelQa(owner,sourceWorld,point,item).source().eventId();
            loot.prepareSentinel(event,owner,0,System.currentTimeMillis(),item.identity(),instance,sourceWorld,point,9,2,1.2);
            loot.acknowledgeForge(event,owner,item.identity(),instance);
            // Crash boundary: source bound, logical binding still PREPARED.
        }
        try(var store=new FileEncounterStore(directory)){
            var loot=service(store);var restoring=loot.claimSentinelRestore(owner,sourceWorld,point).orElseThrow();
            assertEquals(IronSentinelBinding.State.RESTORING,restoring.state());
            assertEquals(9,restoring.restoredLevel());assertEquals(1.2,restoring.restoredPowerFactor());
            assertEquals(item,restoring.boundItem());
            loot.finishSentinelRestore(owner,instance,restoring.currentHealth(),sourceWorld,point);
            loot.checkpointSentinel(owner,instance,40,sourceWorld,point);
            loot.endSentinel(owner,instance,false,40,sourceWorld,point);
            assertEquals("SENTINEL_BOUND",loot.inspect(event).orElseThrow().state());
        }
        try(var store=new FileEncounterStore(directory)){
            var loot=service(store);var restoring=loot.claimSentinelRestore(owner,otherWorld,returnPoint).orElseThrow();
            assertEquals(40,restoring.currentHealth(),1e-9);assertEquals(otherWorld,restoring.worldId());
            loot.finishSentinelRestore(owner,instance,40,otherWorld,returnPoint);
            loot.endSentinel(owner,instance,true,0,otherWorld,returnPoint);
            assertEquals(IronSentinelBinding.State.DEAD,loot.sentinel(owner).orElseThrow().state());
        }
        try(var store=new FileEncounterStore(directory)){
            var loot=service(store);assertTrue(loot.claimSentinelRestore(owner,sourceWorld,point).isEmpty());
            assertEquals("SENTINEL_BOUND",loot.inspect(event).orElseThrow().state());
            assertThrows(IllegalArgumentException.class,()->loot.releaseForge(event,owner,item.identity()));
        }
    }

    @Test void interruptedRestoreIsIdempotentAndDeathBeforeActivationIsTerminal(){
        var item=item();String event;UUID instance=UUID.randomUUID();
        try(var store=new FileEncounterStore(directory)){
            var loot=service(store);event=loot.publishSentinelQa(owner,sourceWorld,point,item).source().eventId();
            loot.prepareSentinel(event,owner,0,System.currentTimeMillis(),item.identity(),instance,sourceWorld,point,1,2);
            loot.acknowledgeForge(event,owner,item.identity(),instance);
            loot.claimSentinelRestore(owner,sourceWorld,point).orElseThrow();
        }
        try(var store=new FileEncounterStore(directory)){
            var loot=service(store);var recovered=loot.claimSentinelRestore(owner,sourceWorld,point).orElseThrow();
            assertEquals(IronSentinelBinding.State.RESTORING,recovered.state());
            loot.endSentinel(owner,instance,true,0,sourceWorld,point);
            assertThrows(IllegalArgumentException.class,()->loot.finishSentinelRestore(owner,instance,10,sourceWorld,point));
        }
    }
    @Test void legacyBindingWithoutProjectionFieldsUsesSafeRankOneDefaults(){
        var legacy=new IronSentinelBinding(1,UUID.randomUUID(),owner,"event",item(),
                IronSentinelBinding.State.DORMANT,50,sourceWorld,point,System.currentTimeMillis(),0);
        var json=com.google.gson.JsonParser.parseString(new com.google.gson.Gson().toJson(legacy)).getAsJsonObject();
        json.remove("effectiveLevel");json.remove("powerFactor");json.remove("nativeInterval");
        var recovered=new com.google.gson.Gson().fromJson(json,IronSentinelBinding.class);
        assertEquals(1,recovered.restoredLevel());assertEquals(1,recovered.restoredPowerFactor());
        assertEquals(2,recovered.restoredInterval());
        assertEquals(50,recovered.withState(IronSentinelBinding.State.RESTORING,50,otherWorld,returnPoint).currentHealth());
    }
}
