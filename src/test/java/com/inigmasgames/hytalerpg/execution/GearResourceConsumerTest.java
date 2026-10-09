package com.inigmasgames.hytalerpg.execution;

import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.resource.*;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import com.inigmasgames.hytalerpg.gear.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Controlled valid-carrier inputs call the same resource owner used by native casts/ticks. */
class GearResourceConsumerTest {
    private static GearEffectSnapshot gear(String id,double value){
        var catalog=GearCatalog.load();var base=catalog.base(id.equals("WA-102")?"gm.sword_adamantite.h":"gm.staff_prismatic.h");var a=catalog.affix(id);
        var tier=GearAffixTiers.compile(a).getLast();
        assertTrue(value>=tier.low()&&value<=tier.high());
        var roll=new GearInstance.AffixRoll(id,a.side(),a.exclusionGroup(),5,value,
                new GearRequirements.Gate(tier.requiredLevel(),Map.of(a.requirementAttribute(base),
                        a.attributeFloor(5-tier.tier(),tier.minimumItemLevel()))),a.name(),a.name());
        return new GearEffectSnapshot(List.of(GearInstance.authoredQa(base,UUID.randomUUID(),99,1000,
                GearRarity.MAGIC,List.of(roll),BigDecimal.ZERO)));
    }
    private static final class Native implements NativeResourcePort {
        double mana=100,stamina=100;
        public double current(ResourceType type){return type==ResourceType.MANA?mana:stamina;}
        public double maximum(ResourceType type){return 1000;}
        public void setCurrent(ResourceType type,double amount){if(type==ResourceType.MANA)mana=amount;else stamina=amount;}
    }
    @Test void regenerationUsesProductionResourceAdmissionAndDoesNotAlterRefunds(){
        var service=new RpgResourceService(CombatBalanceProfile.loadCanonical(),new ReservationService());
        var port=new Native();var actor=UUID.randomUUID();
        assertEquals(15,service.regenerate(actor,ResourceType.MANA,1,port),1e-9);
        port.mana=100;
        assertEquals(18,service.regenerate(actor,ResourceType.MANA,1,port,gear("WA-099",20)
                .percent(GearEffectSnapshot.Operator.MANA_REGEN)),1e-9);
        port.mana=100;
        assertEquals(15,service.regenerate(actor,ResourceType.MANA,1,port),1e-9);
        port.stamina=100;
        assertEquals(18,service.regenerate(actor,ResourceType.STAMINA,1,port,gear("WA-100",20)
                .percent(GearEffectSnapshot.Operator.STAMINA_REGEN)),1e-9);
        assertEquals(0,service.regenerate(actor,ResourceType.HEALTH,1,port,1));
    }
    @Test void finiteAndUpkeepCostsReachTheRealReservationAndPaymentOwner(){
        var service=new RpgResourceService(CombatBalanceProfile.loadCanonical(),new ReservationService());
        var port=new Native();var actor=UUID.randomUUID();
        var mana=gear("WA-101",10);var channel=gear("WA-107",12);
        var finite=new ResourceCost(ResourceType.MANA,20);
        assertEquals(.9,GearResourceModifiers.factor(mana,ResourceType.MANA,false,false),1e-9);
        var quoted=finite.modified(GearResourceModifiers.factor(mana,ResourceType.MANA,false,false));
        var token=service.reserveCost(actor,quoted,port);
        assertTrue(service.commitCost(token,port));assertFalse(service.commitCost(token,port));service.finish(token);
        assertEquals(82,port.mana,1e-9);
        var upkeep=service.evaluateUpkeep(new ResourceCost(ResourceType.MANA,10),CompiledSkillPlan.KernelModifiers.NONE,
                GearResourceModifiers.factor(channel,ResourceType.MANA,true,false));
        token=service.reserveCost(actor,upkeep,port);assertTrue(service.commitCost(token,port));service.finish(token);
        assertEquals(73.2,port.mana,1e-9);
        assertEquals(1,GearResourceModifiers.factor(mana,ResourceType.STAMINA,false,false),1e-9);
        assertEquals(1,GearResourceModifiers.factor(channel,ResourceType.MANA,false,false),1e-9);
        assertEquals(1,finite.modified(0).amount());
    }
    @Test void staminaAndSummonReductionsPayOnceAndRejectUnaffordableCosts(){
        var service=new RpgResourceService(CombatBalanceProfile.loadCanonical(),new ReservationService());
        var port=new Native();var actor=UUID.randomUUID();
        var stamina=gear("WA-102",10);
        var staminaCost=new ResourceCost(ResourceType.STAMINA,20).modified(
                GearResourceModifiers.factor(stamina,ResourceType.STAMINA,false,false));
        assertEquals(18,staminaCost.amount(),1e-9);
        var token=service.reserveCost(actor,staminaCost,port);
        assertTrue(service.commitCost(token,port));assertFalse(service.commitCost(token,port));service.finish(token);
        assertEquals(82,port.stamina,1e-9);
        assertEquals(1,GearResourceModifiers.factor(stamina,ResourceType.MANA,false,false));
        var summon=new GearEffectSnapshot(List.of(gear("WA-101",10).items().getFirst(),gear("WA-120",16).items().getFirst()));
        var summonCost=new ResourceCost(ResourceType.MANA,40).modified(
                GearResourceModifiers.factor(summon,ResourceType.MANA,false,true));
        assertEquals(30,summonCost.amount(),1e-9);
        assertEquals(36,new ResourceCost(ResourceType.MANA,40).modified(
                GearResourceModifiers.factor(summon,ResourceType.MANA,false,false)).amount(),1e-9);
        port.mana=29;assertFalse(service.canAfford(actor,summonCost,port));
        assertThrows(IllegalStateException.class,()->service.reserveCost(actor,summonCost,port));
        assertEquals(29,port.mana,1e-9);
        port.mana=30;token=service.reserveCost(actor,summonCost,port);
        assertTrue(service.commitCost(token,port));service.finish(token);assertEquals(0,port.mana,1e-9);
    }
}
