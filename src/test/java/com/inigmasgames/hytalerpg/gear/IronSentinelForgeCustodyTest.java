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

final class IronSentinelForgeCustodyTest {
    @TempDir Path directory;
    @Test void operatorGroundReceiptPreparesExactlyOnceAndAbortRestoresTheSameItem(){
        UUID owner=UUID.randomUUID(),world=UUID.randomUUID();
        var base=GearCatalog.load().base("gm.sword_iron.n");
        var item=GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.COMMON,List.of(),BigDecimal.ZERO);
        try(var store=new FileEncounterStore(directory)){
            var service=new GearLootService(store,new GearDropGenerator(GearCatalog.load(),new GearBindings(),GearAffixRuntime.ENABLED));
            var source=service.publishSentinelQa(owner,world,new Vec3(4,70,0),item);
            assertEquals("WORLD",source.state());assertEquals(item,source.result().item());
            UUID first=UUID.randomUUID();
            var binding=service.prepareSentinel(source.source().eventId(),owner,0,System.currentTimeMillis(),item.identity(),
                    first,world,source.position(),1,2);
            assertEquals(IronSentinelBinding.State.PREPARED,binding.state());
            assertEquals("FORGE_PENDING",service.inspect(source.source().eventId()).orElseThrow().state());
            assertThrows(IllegalArgumentException.class,()->service.reservePickup(source.source().eventId(),owner,1,System.currentTimeMillis(),true));
            assertThrows(IllegalArgumentException.class,()->service.prepareSentinel(source.source().eventId(),owner,1,
                    System.currentTimeMillis(),item.identity(),UUID.randomUUID(),world,source.position(),1,2));
            service.abortPreparedSentinel(owner,first);
            assertEquals("WORLD",service.inspect(source.source().eventId()).orElseThrow().state());
            assertEquals(item,service.inspect(source.source().eventId()).orElseThrow().result().item());
            var rebound=service.prepareSentinel(source.source().eventId(),owner,2,System.currentTimeMillis(),item.identity(),
                    UUID.randomUUID(),world,source.position(),1,2);
            assertEquals(item,rebound.boundItem());
            assertEquals("SENTINEL_BOUND",service.acknowledgeForge(source.source().eventId(),owner,item.identity(),rebound.instanceId()).state());
            service.sentinelState(owner,rebound.instanceId(),IronSentinelBinding.State.PREPARED,
                    IronSentinelBinding.State.ACTIVE,rebound.currentHealth(),world,source.position());
            assertThrows(IllegalArgumentException.class,()->service.releaseForge(source.source().eventId(),owner,item.identity()));
        }
    }
    @Test void exactWorldItemReservationExcludesPickupAndCannotReleaseBoundSource(){
        UUID owner=UUID.randomUUID(),itemId=UUID.randomUUID();
        var base=GearCatalog.load().base("gm.sword_iron.n");
        var item=GearInstance.authoredQa(base,itemId,base.sourceWindow().getLast(),1000,GearRarity.COMMON,List.of(),BigDecimal.ZERO);
        long now=System.currentTimeMillis();
        var allocation=new GearClaims.Allocation(GearClaims.Mode.SOLO,"solo/"+owner,0,List.of(owner),owner,owner,owner,now,now+180_000,0,null,List.of());
        var initial=new GearLootService.Loot(null,Vec3.ZERO,allocation,0,"iron-forge-test",
                new GearDropGenerator.Result(item,Map.of(),Map.of(),"GENERATED_CANDIDATE"),"WORLD","","test");
        try(var store=new FileEncounterStore(directory)){
            var service=new GearLootService(store,new GearDropGenerator(GearCatalog.load(),new GearBindings(),GearAffixRuntime.ENABLED));
            store.gearTransaction("loot","iron-forge-test",GearLootService.Loot.class,old->old.orElse(initial));
            assertThrows(IllegalArgumentException.class,()->service.reserveForge("iron-forge-test",owner,0,now,UUID.randomUUID()));
            var pending=service.reserveForge("iron-forge-test",owner,0,now,itemId);
            assertEquals("FORGE_PENDING",pending.state());
            assertThrows(IllegalArgumentException.class,()->service.reservePickup("iron-forge-test",owner,1,now,true));
            assertThrows(IllegalArgumentException.class,()->service.reserveForge("iron-forge-test",owner,1,now,itemId));
            assertEquals("WORLD",service.releaseForge("iron-forge-test",owner,itemId).state());
            assertThrows(IllegalArgumentException.class,()->service.reserveForge("iron-forge-test",owner,0,now,itemId));
            service.reserveForge("iron-forge-test",owner,2,now,itemId);
            UUID sentinel=UUID.randomUUID();
            assertThrows(IllegalArgumentException.class,()->service.acknowledgeForge("iron-forge-test",owner,itemId,sentinel));
            var binding=new IronSentinelBinding(1,sentinel,owner,"iron-forge-test",item,
                    IronSentinelBinding.State.PREPARED,150,UUID.randomUUID(),Vec3.ZERO,now,0);
            store.gearTransaction("sentinels",owner.toString(),IronSentinelBinding.class,old->old.orElse(binding));
            assertThrows(IllegalArgumentException.class,()->service.releaseForge("iron-forge-test",owner,itemId));
            assertEquals("SENTINEL_BOUND",service.acknowledgeForge("iron-forge-test",owner,itemId,sentinel).state());
            assertEquals("SENTINEL_BOUND",service.acknowledgeForge("iron-forge-test",owner,itemId,sentinel).state());
            assertThrows(IllegalArgumentException.class,()->service.releaseForge("iron-forge-test",owner,itemId));
            assertThrows(IllegalArgumentException.class,()->service.reservePickup("iron-forge-test",owner,3,now,true));
        }
        try(var recovered=new FileEncounterStore(directory)){
            var bound=recovered.gearRead("sentinels",owner.toString(),IronSentinelBinding.class).orElseThrow();
            assertEquals(item,bound.boundItem());
            assertEquals(IronSentinelBinding.State.PREPARED,bound.state());
            assertEquals("SENTINEL_BOUND",recovered.gearRead("loot","iron-forge-test",GearLootService.Loot.class)
                    .orElseThrow().state());
        }
    }
}
