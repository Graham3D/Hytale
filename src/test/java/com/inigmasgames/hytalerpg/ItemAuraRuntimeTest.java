package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.execution.CommittedTarget;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.gear.*;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ItemAuraRuntimeTest {
    private static GearInstance source(String affix){
        var catalog=GearCatalog.load();var definition=catalog.affix(affix);
        var tier=GearAffixTiers.compile(definition).getFirst();
        var roll=new GearInstance.AffixRoll(affix,definition.side(),definition.exclusionGroup(),tier.tier(),1,
                new GearRequirements.Gate(tier.requiredLevel(),Map.of()),definition.name(),definition.name());
        return GearInstance.authoredQa(catalog.base("gm.staff_prismatic.h"),UUID.randomUUID(),95,1000,
                GearRarity.MAGIC,List.of(roll),BigDecimal.ZERO);
    }
    private static com.inigmasgames.hytalerpg.execution.SkillExecutionContext context(
            Stage09SupportRuntimeTest.Harness h,GearInstance item,String skill){
        return h.execution.itemAura(h.world,h.actor,item.identity(),skill,new GearEffectSnapshot(List.of(item)),h,
                new CommittedTarget(h.world,Vec3.ZERO,Vec3.ZERO,Vec3.FORWARD,h.actor));
    }
    @Test void itemAndManualEmanatismHaveSeparateTokensAndStrongestRecipient(){
        var h=new Stage09SupportRuntimeTest.Harness("emanatism");var item=source("WA-148");
        var aura=context(h,item,"emanatism");
        assertEquals("ACTIVE",h.runtime.activateItemAura(aura,item.identity(),false,0,h));
        assertEquals(90,h.mana,1e-9);assertEquals(10,h.reserved,1e-9);
        assertEquals(.25,h.bonus(h.actor),1e-9);
        assertTrue(h.cast().committed());assertEquals(2,h.runtime.auraCount());
        assertEquals(.25,h.bonus(h.actor),1e-9);
        h.runtime.endItemAura(h.actor,item.identity(),"emanatism","UNEQUIP",h);
        assertEquals(1,h.runtime.auraCount());assertEquals(.25,h.bonus(h.actor),1e-9);
        assertEquals(80,h.mana,1e-9);
        h.runtime.cancel(h.actor,"DISCONNECT",h);
        assertEquals(0,h.runtime.auraCount());assertEquals(0,h.reserved,1e-9);
        assertEquals(80,h.mana,1e-9);
    }
    @Test void invalidItemSourceAndD08FreeSustainCannotChargeMana(){
        var h=new Stage09SupportRuntimeTest.Harness("emanatism");var item=source("WA-148");
        var aura=context(h,item,"emanatism");
        assertEquals("ACTIVE",h.runtime.activateItemAura(aura,item.identity(),true,0,h));
        assertEquals(100,h.mana,1e-9);assertEquals(0,h.reserved,1e-9);
        h.valid="WORLD_CHANGED";h.tick(.1);
        assertEquals(0,h.runtime.auraCount());assertEquals(0,h.bonus(h.actor),1e-9);
        assertEquals("INVALID_ITEM_AURA",h.runtime.activateItemAura(aura,UUID.randomUUID(),true,.2,h));
    }
    @Test void itemThornsPaysActivationAndQuarterSecondUpkeep(){
        var h=new Stage09SupportRuntimeTest.Harness("thorns_aura"){
            @Override public double itemAuraActivationCost(com.inigmasgames.hytalerpg.execution.SkillExecutionContext c){return c.profile().resourceCost();}
            @Override public boolean payItemAuraActivation(com.inigmasgames.hytalerpg.execution.SkillExecutionContext c){
                if(mana<c.profile().resourceCost())return false;mana-=c.profile().resourceCost();return true;
            }
            @Override public boolean upkeep(com.inigmasgames.hytalerpg.execution.SkillExecutionContext c,double seconds,int quantum){
                double cost=c.profile().support().upkeepPerSecond()*seconds;
                if(mana<cost)return false;mana-=cost;return true;
            }
        };
        var item=source("WA-149");var aura=context(h,item,"thorns_aura");
        assertEquals("ACTIVE",h.runtime.activateItemAura(aura,item.identity(),false,0,h));
        assertEquals(81.5,h.mana,1e-9);
        h.tick(.1);h.tick(.3);assertEquals(81,h.mana,1e-9);
        h.runtime.endItemAura(h.actor,item.identity(),"thorns_aura","UNEQUIP",h);
        assertEquals(0,h.runtime.auraCount());assertEquals(81,h.mana,1e-9);
    }
    @Test void duplicateItemThornsSourcesKeepOneStrongestReflection(){
        var h=new Stage09SupportRuntimeTest.Harness("thorns_aura"){
            @Override public double itemAuraActivationCost(com.inigmasgames.hytalerpg.execution.SkillExecutionContext c){return c.profile().resourceCost();}
            @Override public boolean payItemAuraActivation(com.inigmasgames.hytalerpg.execution.SkillExecutionContext c){
                if(mana<c.profile().resourceCost())return false;mana-=c.profile().resourceCost();return true;
            }
            @Override public boolean upkeep(com.inigmasgames.hytalerpg.execution.SkillExecutionContext c,double seconds,int quantum){
                double cost=c.profile().support().upkeepPerSecond()*seconds;
                if(mana<cost)return false;mana-=cost;return true;
            }
        };
        var first=source("WA-149");var second=source("WA-149");
        assertEquals("ACTIVE",h.runtime.activateItemAura(context(h,first,"thorns_aura"),first.identity(),false,0,h));
        double magnitude=h.runtime.thorns(h.world,h.actor,0).orElseThrow().magnitude();
        assertEquals("ACTIVE",h.runtime.activateItemAura(context(h,second,"thorns_aura"),second.identity(),false,0,h));
        assertEquals(2,h.runtime.auraCount());
        assertEquals(magnitude,h.runtime.thorns(h.world,h.actor,0).orElseThrow().magnitude(),1e-9);
        h.runtime.endItemAura(h.actor,first.identity(),"thorns_aura","SOURCE_REMOVED",h);
        assertEquals(magnitude,h.runtime.thorns(h.world,h.actor,0).orElseThrow().magnitude(),1e-9);
    }
    @Test void itemPedanticismReservesManaAndWithdrawsCooldownRecovery(){
        var h=new Stage09SupportRuntimeTest.Harness("pedanticism"){
            @Override public List<UUID> enemies(com.inigmasgames.hytalerpg.execution.SkillExecutionContext c,double radius){return List.of();}
        };var item=source("WA-150");
        var aura=context(h,item,"pedanticism");
        assertEquals("ACTIVE",h.runtime.activateItemAura(aura,item.identity(),false,0,h));
        assertEquals(20,h.reserved,1e-9);assertEquals(80,h.mana,1e-9);
        assertEquals(.15,h.runtime.cooldownRecoveryIncreased(h.actor,0),1e-9);
        h.runtime.endItemAura(h.actor,item.identity(),"pedanticism","SOURCE_REMOVED",h);
        assertEquals(0,h.runtime.cooldownRecoveryIncreased(h.actor,0),1e-9);
        assertEquals(0,h.reserved,1e-9);assertEquals(80,h.mana,1e-9);
    }
    @Test void sentinelBoundAuraContextDoesNotReadSentinelPlayerLoadout(){
        var h=new Stage09SupportRuntimeTest.Harness("emanatism");assertTrue(h.cast().committed());
        var item=source("WA-148");UUID sentinel=UUID.randomUUID();
        var context=h.execution.boundItemAura(h.world,sentinel,item.identity(),"emanatism",
                new GearEffectSnapshot(List.of(item)),h.context.snapshot().derivedStats(),null,
                new CommittedTarget(h.world,Vec3.ZERO,Vec3.ZERO,Vec3.FORWARD,sentinel));
        assertEquals(sentinel,context.request().actorId());assertEquals(1,context.effectiveSkillLevel());
        assertEquals(0,context.snapshot().resourceCost().amount(),1e-9);
        assertTrue(context.compiledPlan().passiveOrder().isEmpty());
    }
    private static GearEffectSnapshot equipped(GearInstance item,boolean worn,boolean intact,boolean slot){
        var resolved=GearEquipmentResolution.resolve(99,
                com.inigmasgames.hytalerpg.gear.MasterAffixTestEquipment.BASELINE,
                List.of(new GearEquipmentResolution.Candidate(item,worn,intact,slot)));
        if(worn&&intact&&slot)assertEquals(List.of(item),resolved.validItems(),resolved.rejected().toString());
        return resolved.effects().snapshot();
    }
    private static void sync(Stage09SupportRuntimeTest.Harness h,GearEffectSnapshot accepted,String id,String skill){
        var desired=accepted.sources(GearEffectSnapshot.Operator.ITEM_AURA).stream()
                .filter(source->source.affixId().equals(id))
                .map(GearEffectSnapshot.Source::itemId).collect(java.util.stream.Collectors.toSet());
        for(var active:h.runtime.itemAuras(h.actor))if(!desired.contains(active.item()))
            h.runtime.endItemAura(h.actor,active.item(),active.skill(),"SOURCE_REMOVED",h);
        for(var source:desired)if(!h.runtime.itemAuraActive(h.actor,source,skill)){
            var item=accepted.forItem(source).items().getFirst();
            assertEquals("ACTIVE",h.runtime.activateItemAura(context(h,item,skill),source,false,0,h));
        }
    }
    private static double effect(Stage09SupportRuntimeTest.Harness h,String id){
        return switch(id){
            case "WA-148"->h.runtime.manaRegenerationIncreased(h.actor,0);
            case "WA-149"->h.runtime.thorns(h.world,h.actor,0).map(e->e.magnitude()).orElse(0d);
            case "WA-150"->h.runtime.cooldownRecoveryIncreased(h.actor,0);
            default->throw new AssertionError(id);
        };
    }
    @Test void wa148To150EquipmentResolutionActivatesWithdrawsAndNeverLayersRecipientEffects(){
        for(var row:Map.of("WA-148","emanatism","WA-149","thorns_aura","WA-150","pedanticism").entrySet()){
            String id=row.getKey(),skill=row.getValue();
            var source=com.inigmasgames.hytalerpg.gear.MasterAffixTestEquipment.fixture(id,false);
            var h=new Stage09SupportRuntimeTest.Harness(skill){
                @Override public List<UUID> enemies(com.inigmasgames.hytalerpg.execution.SkillExecutionContext c,double radius){return List.of();}
                @Override public double itemAuraActivationCost(com.inigmasgames.hytalerpg.execution.SkillExecutionContext c){return c.profile().resourceCost();}
                @Override public boolean payItemAuraActivation(com.inigmasgames.hytalerpg.execution.SkillExecutionContext c){
                    if(mana<c.profile().resourceCost())return false;mana-=c.profile().resourceCost();return true;
                }
                @Override public boolean upkeep(com.inigmasgames.hytalerpg.execution.SkillExecutionContext c,double seconds,int quantum){
                    double cost=c.profile().support().upkeepPerSecond()*seconds;
                    if(mana<cost)return false;mana-=cost;return true;
                }
            };
            var accepted=equipped(source,true,true,true);
            var unrolled=equipped(com.inigmasgames.hytalerpg.gear.MasterAffixTestEquipment.fixture(id,true),true,true,true);
            sync(h,unrolled,id,skill);
            assertEquals(0,effect(h,id),1e-9,id+" unrolled control");
            sync(h,accepted,id,skill);double magnitude=effect(h,id);
            assertTrue(magnitude>0,id);assertEquals(1,h.runtime.auraCount(),id);
            sync(h,accepted,id,skill);assertEquals(1,h.runtime.auraCount(),id+" duplicate tick");
            assertEquals(magnitude,effect(h,id),1e-9,id+" no second layer");
            for(var invalid:List.of(equipped(source,false,true,true),equipped(source,true,false,true),
                    equipped(source,true,true,false))){
                sync(h,invalid,id,skill);
                assertEquals(0,effect(h,id),1e-9,id+" withdrawn recipient");
                assertEquals(0,h.runtime.auraCount(),id);
                sync(h,accepted,id,skill);
                assertEquals(magnitude,effect(h,id),1e-9,id+" restored once");
            }
            sync(h,GearEffectSnapshot.EMPTY,id,skill);
            assertEquals(0,effect(h,id),1e-9,id);
        }
    }
}
