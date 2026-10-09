package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.summon.IronSentinelBinding;
import com.inigmasgames.hytalerpg.gear.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class NativeSentinelItemChildrenTest {
    @Test void acceptedNativeHitEntersBoundNpcQueueAndNoProcControlDoesNot(){
        var base=GearCatalog.load().base("gm.staff_oracle.h");
        var affix=GearCatalog.load().affix("WA-145");
        var roll=new GearInstance.AffixRoll("WA-145",affix.side(),affix.exclusionGroup(),1,100,
                new GearRequirements.Gate(1,Map.of()),"Trigger",affix.name());
        var item=GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.MAGIC,List.of(roll),BigDecimal.ZERO);
        UUID world=UUID.randomUUID(),owner=UUID.randomUUID(),actor=UUID.randomUUID(),victim=UUID.randomUUID();
        var binding=new IronSentinelBinding(3,UUID.randomUUID(),owner,"event",item,
                IronSentinelBinding.State.RESTORING,40,world,Vec3.ZERO,1,0,25,1,2,List.of());
        var system=new HytaleSummonSystem(null,null,null,null,null,null);
        var lease=system.registry().restoreIronSentinel(binding,10);
        assertTrue(system.registry().activate(lease,actor,10));
        var observed=new AtomicInteger();
        var queue=new NativeSentinelItemChildren(system,(store,buffer,sentinel,source,current,child)->{
            assertEquals("fire_bolt",child.skill());assertTrue(child.noProc());
            assertEquals(item.identity(),child.item());observed.incrementAndGet();
        });
        var cleared=new AtomicInteger();
        system.configureSentinelItemChildren(queue);
        system.configureSentinelChildStatusCleanup(id->{assertEquals(actor,id);cleared.incrementAndGet();});
        // Native melee submits its damage after the summon tick has drained this actor.
        queue.drain(null,null,null,null,lease);
        var runtime=new ItemSkillTriggerRuntime(()->0);
        var valid=new ItemSkillTriggerRuntime.DirectHit(world,actor,item.identity(),victim,"root","contact",
                lease.boundEffects(),true,true,false,false,false,10,1,false);
        runtime.applied(valid,1,queue);
        assertEquals(1,queue.pending(actor));
        runtime.applied(new ItemSkillTriggerRuntime.DirectHit(world,actor,item.identity(),victim,"second","child",
                lease.boundEffects(),true,true,true,false,false,10,1,false),1,queue);
        assertEquals(1,queue.pending(actor));
        assertEquals(0,observed.get());
        queue.drain(null,null,null,null,lease);
        assertEquals(1,observed.get());
        assertTrue(queue.tracked(actor));
        system.worldUnload(world);assertFalse(queue.tracked(actor));assertEquals(1,cleared.get());
    }
    @Test void boundChildUsesRestoredActorAndSourceThenDrainsOnce(){
        var base=GearCatalog.load().base("gm.staff_oracle.h");
        var affix=GearCatalog.load().affix("WA-145");
        var roll=new GearInstance.AffixRoll("WA-145",affix.side(),affix.exclusionGroup(),1,10,
                new GearRequirements.Gate(1,Map.of()),"Trigger",affix.name());
        var item=GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.MAGIC,List.of(roll),BigDecimal.ZERO);
        UUID world=UUID.randomUUID(),owner=UUID.randomUUID(),actor=UUID.randomUUID(),victim=UUID.randomUUID();
        var binding=new IronSentinelBinding(3,UUID.randomUUID(),owner,"event",item,
                IronSentinelBinding.State.RESTORING,40,world,Vec3.ZERO,1,0,25,1,2,List.of());
        var system=new HytaleSummonSystem(null,null,null,null,null,null);
        var lease=system.registry().restoreIronSentinel(binding,10);
        assertTrue(system.registry().activate(lease,actor,10));
        var observed=new AtomicInteger();
        var queue=new NativeSentinelItemChildren(system,(store,buffer,sentinel,source,current,child)->{
            assertSame(lease,current);assertEquals(item.identity(),child.item());observed.incrementAndGet();
        });
        var child=new ItemSkillTriggerRuntime.Child(world,actor,victim,"accepted-root","fire_bolt",1,
                item.identity(),lease.boundEffects());
        assertTrue(queue.accepted(child));queue.enqueue(child);assertEquals(1,queue.pending(actor));
        queue.drain(null,null,null,null,lease);assertEquals(1,observed.get());
        queue.drain(null,null,null,null,lease);assertEquals(1,observed.get());
        assertEquals(0,queue.pending(actor));
        assertFalse(queue.accepted(new ItemSkillTriggerRuntime.Child(world,owner,victim,"root","fire_bolt",1,
                item.identity(),lease.boundEffects())));
        assertFalse(queue.accepted(new ItemSkillTriggerRuntime.Child(UUID.randomUUID(),actor,victim,"root","fire_bolt",1,
                item.identity(),lease.boundEffects())));
        assertThrows(IllegalArgumentException.class,()->queue.enqueue(new ItemSkillTriggerRuntime.Child(world,actor,
                victim,"root","minor_heal",1,item.identity(),lease.boundEffects())));
        queue.enqueue(child);queue.worldUnload(world);assertEquals(0,queue.pending(actor));
        queue.enqueue(child);queue.detach(actor);assertEquals(0,queue.pending(actor));
    }
}
