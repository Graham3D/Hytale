package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.execution.hytale.HytaleSkillExecutionSystem;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.summon.IronSentinelBinding;
import com.inigmasgames.hytalerpg.execution.summon.SummonRegistry;
import com.inigmasgames.hytalerpg.gear.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SentinelItemChildContextTest {
    @Test void nativeNpcChildrenKeepExactItemRankAndZeroMana(){
        var harness=new Stage09SupportRuntimeTest.Harness("minor_heal");
        var catalog=GearCatalog.load();
        var base=catalog.base("gm.staff_oracle.h");
        for(var row:Map.of("WA-145","fire_bolt","WA-146","frost_bolt","WA-147","minor_heal").entrySet()){
            var affix=catalog.affix(row.getKey());
            var roll=new GearInstance.AffixRoll(row.getKey(),affix.side(),affix.exclusionGroup(),1,100,
                    new GearRequirements.Gate(1,Map.of()),"Child",affix.name());
            var item=GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                    GearRarity.MAGIC,List.of(roll),BigDecimal.ZERO);
            var binding=new IronSentinelBinding(3,UUID.randomUUID(),harness.actor,"event",item,
                    IronSentinelBinding.State.RESTORING,40,harness.world,Vec3.ZERO,1,0,25,1,2,List.of());
            var registry=new SummonRegistry();var lease=registry.restoreIronSentinel(binding,10);
            UUID npc=UUID.randomUUID();assertTrue(registry.activate(lease,npc,10));
            UUID target=row.getValue().equals("minor_heal")?npc:UUID.randomUUID();
            var child=new ItemSkillTriggerRuntime.Child(harness.world,npc,target,"root",row.getValue(),1,
                    item.identity(),lease.boundEffects());
            var context=HytaleSkillExecutionSystem.sentinelItemContext(harness.kernel,lease,child,
                    Vec3.ZERO,new Vec3(3,0,0));
            assertEquals(npc,context.request().actorId());
            assertEquals(item.identity(),context.gearSourceItemId());
            assertEquals(lease.boundEffects(),context.gearSnapshot());
            assertEquals(1,context.effectiveSkillLevel());
            assertEquals(0,context.snapshot().resourceCost().amount(),1e-9);
            assertTrue(context.compiledPlan().passiveOrder().isEmpty());
            assertEquals(harness.world,context.target().worldId());
            var wrong=new ItemSkillTriggerRuntime.Child(harness.world,UUID.randomUUID(),target,"root",
                    row.getValue(),1,item.identity(),lease.boundEffects());
            assertThrows(IllegalArgumentException.class,()->HytaleSkillExecutionSystem.sentinelItemContext(
                    harness.kernel,lease,wrong,Vec3.ZERO,new Vec3(3,0,0)));
        }
    }
}
