package com.inigmasgames.hytalerpg.gear;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ItemSkillTriggerRuntimeTest {
    private static GearInstance item(String id){
        var catalog=GearCatalog.load();var affix=catalog.affix(id);
        var base=catalog.base(id.equals("WA-147")?"gm.staff_prismatic.h":"gm.sword_mithril.h");
        var tier=GearAffixTiers.compile(affix).getFirst();
        var roll=new GearInstance.AffixRoll(id,affix.side(),affix.exclusionGroup(),tier.tier(),12,
                new GearRequirements.Gate(tier.requiredLevel(),Map.of()),affix.name(),affix.name());
        return GearInstance.authoredQa(base,UUID.randomUUID(),95,1000,GearRarity.MAGIC,List.of(roll),BigDecimal.ZERO);
    }
    private static ItemSkillTriggerRuntime.DirectHit hit(UUID world,UUID owner,UUID item,UUID target,
                                                          String contact,GearEffectSnapshot snapshot,boolean noProc){
        return new ItemSkillTriggerRuntime.DirectHit(world,owner,item,target,"root",contact,snapshot,
                true,true,noProc,false,false,5,1,false);
    }
    @Test void completedItemHitsQueueFixedRankNoProcBoltWithSharedOwnerLock(){
        var fire=item("WA-145");var frost=item("WA-146");
        var snapshot=new GearEffectSnapshot(List.of(fire,frost));
        UUID world=UUID.randomUUID(),actor=UUID.randomUUID(),target=UUID.randomUUID();
        var children=new ArrayList<ItemSkillTriggerRuntime.Child>();
        var runtime=new ItemSkillTriggerRuntime(()->0);
        runtime.applied(hit(world,actor,fire.identity(),target,"1",snapshot,false),10,children::add);
        assertEquals(1,children.size());assertEquals("fire_bolt",children.getFirst().skill());
        assertEquals(1,children.getFirst().rank());assertTrue(children.getFirst().noProc());
        assertEquals(snapshot,children.getFirst().source());
        assertEquals(12,children.getFirst().source().forItem(fire.identity()).value("WA-145"));
        assertEquals(0,children.getFirst().source().forItem(frost.identity()).value("WA-145"));
        assertTrue(children.getFirst().noLinkedPassives());assertEquals(0,children.getFirst().additionalMana());
        runtime.applied(hit(world,actor,fire.identity(),target,"1",snapshot,false),10.1,children::add);
        runtime.applied(hit(world,actor,frost.identity(),target,"2",snapshot,false),12.9,children::add);
        runtime.applied(hit(world,actor,frost.identity(),target,"3",snapshot,true),13.1,children::add);
        assertEquals(1,children.size());
        runtime.applied(hit(world,actor,frost.identity(),target,"4",snapshot,false),13.1,children::add);
        assertEquals(1,children.size(),"An old root cannot gain another opportunity after the cooldown");
        runtime.applied(new ItemSkillTriggerRuntime.DirectHit(world,actor,frost.identity(),target,
                "next-cast","1",snapshot,true,true,false,false,false,5,1,false),13.1,children::add);
        assertEquals(2,children.size());assertEquals("frost_bolt",children.get(1).skill());
    }
    @Test void blockOnlySuccorQueuesSelfHealAndNeverRespondsToItemHit(){
        var guard=item("WA-147");var snapshot=new GearEffectSnapshot(List.of(guard));
        UUID world=UUID.randomUUID(),actor=UUID.randomUUID(),target=UUID.randomUUID();
        var children=new ArrayList<ItemSkillTriggerRuntime.Child>();var runtime=new ItemSkillTriggerRuntime(()->0);
        runtime.applied(hit(world,actor,guard.identity(),target,"attack",snapshot,false),1,children::add);
        runtime.blocked(new ItemSkillTriggerRuntime.Block(world,actor,guard.identity(),"root","failed",snapshot,
                false,false,false),1,children::add);
        assertTrue(children.isEmpty());
        runtime.blocked(new ItemSkillTriggerRuntime.Block(world,actor,guard.identity(),"root","failed",snapshot,
                true,false,false),1,children::add);
        assertEquals(1,children.size());assertEquals("minor_heal",children.getFirst().skill());
        assertEquals(actor,children.getFirst().target());
        runtime.blocked(new ItemSkillTriggerRuntime.Block(world,actor,guard.identity(),"root","repeat",snapshot,
                true,false,false),2,children::add);
        assertEquals(1,children.size());
        runtime.blocked(new ItemSkillTriggerRuntime.Block(world,actor,guard.identity(),"root","later",snapshot,
                true,false,false),9,children::add);
        assertEquals(2,children.size());
    }
    @Test void chanceMissAndExcludedChildrenDoNotQueueSkills(){
        var fire=item("WA-145");var guard=item("WA-147");
        var snapshot=new GearEffectSnapshot(List.of(fire,guard));
        UUID world=UUID.randomUUID(),actor=UUID.randomUUID(),target=UUID.randomUUID();
        var children=new ArrayList<ItemSkillTriggerRuntime.Child>();var misses=new ItemSkillTriggerRuntime(()->.99);
        misses.applied(hit(world,actor,fire.identity(),target,"miss",snapshot,false),1,children::add);
        misses.blocked(new ItemSkillTriggerRuntime.Block(world,actor,guard.identity(),"root","miss",snapshot,
                true,false,false),1,children::add);
        assertTrue(children.isEmpty());
        var success=new ItemSkillTriggerRuntime(()->0);
        success.applied(hit(world,actor,fire.identity(),target,"no-proc",snapshot,true),1,children::add);
        success.blocked(new ItemSkillTriggerRuntime.Block(world,actor,guard.identity(),"root","no-proc",snapshot,
                true,true,false),1,children::add);
        assertTrue(children.isEmpty());
    }
    @Test void absorbedOrImmuneFirstChannelDoesNotConsumeLogicalContact(){
        var fire=item("WA-145");var snapshot=new GearEffectSnapshot(List.of(fire));
        UUID world=UUID.randomUUID(),actor=UUID.randomUUID(),target=UUID.randomUUID();
        var children=new ArrayList<ItemSkillTriggerRuntime.Child>();var runtime=new ItemSkillTriggerRuntime(()->0);
        runtime.applied(new ItemSkillTriggerRuntime.DirectHit(world,actor,fire.identity(),target,"root","contact",snapshot,
                true,true,false,false,false,0,1,false),1,children::add);
        runtime.applied(hit(world,actor,fire.identity(),target,"contact",snapshot,false),1,children::add);
        assertEquals(1,children.size());
    }
}
