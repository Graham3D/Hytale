package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.inigmasgames.hytalerpg.execution.summon.IronSentinelAffixes;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Current Sentinel keeps its existing single-item, unguarded chassis. */
class SentinelConditionalActionTest {
    private GearInstance item(String id) {
        var catalog=GearCatalog.load(); var affix=catalog.affix(id);
        var base=catalog.base(id.equals("WA-141")?"gm.daggers_iron.h":"gm.shield_iron.h");
        var roll=new GearInstance.AffixRoll(id,affix.side(),affix.exclusionGroup(),1,10,
                new GearRequirements.Gate(1,Map.of()),affix.name(),affix.name());
        return GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,GearRarity.MAGIC,List.of(roll),BigDecimal.ZERO);
    }
    @Test void efficientGuardingDoesNotInventASentinelBlock() {
        var item=item("WA-083"); var snapshot=new GearEffectSnapshot(List.of(item));
        IronSentinelAffixes.requireAdapted(item);
        assertTrue(IronSentinelAffixes.classify("WA-083").reason().startsWith("INAPPLICABLE"));
        var ordinaryHit=new Damage(Damage.NULL_SOURCE,0,10);
        assertFalse(NativeAffixBlockCostSystem.apply(ordinaryHit,snapshot));
        assertNull(ordinaryHit.getIfPresentMetaObject(Damage.STAMINA_DRAIN_MULTIPLIER));
        assertFalse(NativeAffixBlockCostSystem.apply(ordinaryHit,GearEffectSnapshot.EMPTY));
    }
    @Test void succorDoesNotInventABlockOrHealTheOwner() {
        var item=item("WA-147");var snapshot=new GearEffectSnapshot(List.of(item));
        IronSentinelAffixes.requireAdapted(item);
        var children=new ArrayList<ItemSkillTriggerRuntime.Child>();
        var runtime=new ItemSkillTriggerRuntime(()->0);
        UUID actor=UUID.randomUUID(),world=UUID.randomUUID();
        runtime.blocked(new ItemSkillTriggerRuntime.Block(world,actor,item.identity(),"sentinel","ordinary",snapshot,false,false,false),1,children::add);
        runtime.applied(new ItemSkillTriggerRuntime.DirectHit(world,actor,item.identity(),UUID.randomUUID(),
                "sentinel","hit",snapshot,true,true,false,false,false,10,1,false),1,children::add);
        assertTrue(children.isEmpty());
        assertTrue(IronSentinelAffixes.classify("WA-147").reason().startsWith("INAPPLICABLE"));
    }
    @Test void twinAssaultDoesNotFabricateAnOffhandForOneBoundItem() {
        var item=item("WA-141");var snapshot=new GearEffectSnapshot(List.of(item));
        IronSentinelAffixes.requireAdapted(item);
        assertFalse(NativeTwinAssaultEligibility.accepts(snapshot,item,null,0));
        assertFalse(NativeTwinAssaultEligibility.accepts(snapshot,item,item,0));
        assertFalse(NativeTwinAssaultEligibility.accepts(GearEffectSnapshot.EMPTY,item,null,0));
        assertEquals(1,snapshot.items().size());
        assertTrue(IronSentinelAffixes.classify("WA-141").reason().startsWith("INAPPLICABLE"));
    }
}
