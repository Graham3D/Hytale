package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.summon.IronSentinelBinding;
import com.inigmasgames.hytalerpg.gear.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

final class MasterAffixSentinelItemTriggerTest {
    @ParameterizedTest(name="boundNpcAcceptedHitExecutesCanonicalRankOneChild[{0}]")
    @ValueSource(strings={"WA-145","WA-146"})
    void boundNpcAcceptedHitExecutesCanonicalRankOneChild(String id){
        var catalog=GearCatalog.load();var base=catalog.base("gm.staff_oracle.h");var affix=catalog.affix(id);
        var roll=new GearInstance.AffixRoll(id,affix.side(),affix.exclusionGroup(),1,100,
                new GearRequirements.Gate(1,Map.of()),"Bound trigger",affix.name());
        var item=GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.MAGIC,List.of(roll),BigDecimal.ZERO);
        UUID world=UUID.randomUUID(),owner=UUID.randomUUID(),npc=UUID.randomUUID(),victim=UUID.randomUUID();
        var binding=new IronSentinelBinding(3,UUID.randomUUID(),owner,"event",item,
                IronSentinelBinding.State.RESTORING,40,world,Vec3.ZERO,1,0,25,1,2,List.of());
        var summons=new HytaleSummonSystem(null,null,null,null,null,null);
        var lease=summons.registry().restoreIronSentinel(binding,10);
        assertTrue(summons.registry().activate(lease,npc,10));
        var kernel=RpgCombatKernel.createProduction();
        var executed=new ArrayList<ItemSkillTriggerRuntime.Child>();
        var queue=new NativeSentinelItemChildren(summons,(store,buffer,sentinel,source,current,child)->{
            assertSame(lease,current);assertEquals(npc,child.actor());assertEquals(item.identity(),child.item());
            assertEquals(id.equals("WA-145")?"fire_bolt":"frost_bolt",child.skill());
            assertTrue(child.noProc());assertTrue(child.noLeech());assertTrue(child.noLinkedPassives());
            var context=HytaleSkillExecutionSystem.sentinelItemContext(kernel,current,child,
                    Vec3.ZERO,new Vec3(3,0,0));
            assertEquals(npc,context.request().actorId());
            assertEquals(item.identity(),context.gearSourceItemId());
            assertEquals(lease.boundEffects(),context.gearSnapshot());
            assertEquals(1,context.effectiveSkillLevel());
            assertEquals(0,context.snapshot().resourceCost().amount(),1e-9);
            assertTrue(context.compiledPlan().passiveOrder().isEmpty());
            executed.add(child);
        });
        var runtime=new ItemSkillTriggerRuntime(()->0);
        var accepted=new ItemSkillTriggerRuntime.DirectHit(world,npc,item.identity(),victim,"root","contact",
                lease.boundEffects(),true,true,false,false,false,10,1,false);
        runtime.applied(accepted,1,queue);
        assertEquals(1,queue.pending(npc));assertEquals(0,queue.pending(owner));
        runtime.applied(new ItemSkillTriggerRuntime.DirectHit(world,npc,item.identity(),victim,
                "child","child-contact",lease.boundEffects(),true,true,true,false,false,10,1,false),1,queue);
        assertEquals(1,queue.pending(npc),id+" NoProc child cannot enqueue again");
        queue.drain(null,null,null,null,lease);
        assertEquals(1,executed.size(),id);
        queue.drain(null,null,null,null,lease);
        assertEquals(1,executed.size(),id+" one world-step execution");
        assertFalse(queue.accepted(new ItemSkillTriggerRuntime.Child(world,owner,victim,"root",
                id.equals("WA-145")?"fire_bolt":"frost_bolt",1,item.identity(),lease.boundEffects())));
    }
}
