package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.execution.hytale.NativeSentinelItemAuras;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.summon.IronSentinelBinding;
import com.inigmasgames.hytalerpg.execution.summon.SummonRegistry;
import com.inigmasgames.hytalerpg.gear.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SentinelBoundAuraContextTest {
    @Test void allThreeNativeItemAurasUseExactBoundSourceAndRankOneFactory(){
        var h=new Stage09SupportRuntimeTest.Harness("emanatism");
        var catalog=GearCatalog.load();
        for(var row:Map.of("WA-148","emanatism","WA-149","thorns_aura","WA-150","pedanticism").entrySet()){
            var affix=catalog.affix(row.getKey());var base=catalog.base("gm.staff_prismatic.h");
            var roll=new GearInstance.AffixRoll(row.getKey(),affix.side(),affix.exclusionGroup(),1,1,
                    new GearRequirements.Gate(1,Map.of()),"Aura",affix.name());
            var item=GearInstance.authoredQa(base,UUID.randomUUID(),95,1000,GearRarity.MAGIC,
                    List.of(roll),BigDecimal.ZERO);
            var binding=new IronSentinelBinding(3,UUID.randomUUID(),h.actor,"event",item,
                    IronSentinelBinding.State.RESTORING,40,h.world,Vec3.ZERO,1,0,25,1,2,List.of());
            var registry=new SummonRegistry();var lease=registry.restoreIronSentinel(binding,10);
            UUID npc=UUID.randomUUID();assertTrue(registry.activate(lease,npc,10));
            var context=NativeSentinelItemAuras.boundContext(h.execution,h.kernel,lease,Vec3.ZERO,row.getValue());
            assertEquals(npc,context.request().actorId());
            assertEquals(item.identity(),context.gearSourceItemId());
            assertEquals(lease.boundEffects(),context.gearSnapshot());
            assertEquals(1,context.effectiveSkillLevel());
            assertEquals(0,context.snapshot().resourceCost().amount(),1e-9);
            assertTrue(context.compiledPlan().passiveOrder().isEmpty());
            assertEquals(h.world,context.target().worldId());
        }
    }
}
