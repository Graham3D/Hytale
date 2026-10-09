package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.gear.GearEffectSnapshot;
import com.inigmasgames.hytalerpg.gear.*;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import com.inigmasgames.hytalerpg.combat.hytale.GearCreditedDeathRecovery;
import com.inigmasgames.hytalerpg.combat.resource.*;
import com.inigmasgames.hytalerpg.progress.EncounterContributions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GearRecoveryRewardDispatchTest {
    private static GearEffectSnapshot admitted(boolean rolled){
        var qa=new GearAffixQaSuite(GearCatalog.load());
        var item=qa.preview(qa.fixtures().stream().filter(f->f.fixtureId().equals("ab-wa-098-affixed"))
                .findFirst().orElseThrow());
        if(!rolled)item=new GearInstance(item.schemaVersion(),UUID.randomUUID(),item.definitionRevision(),item.baseId(),
                item.baseName(),item.category(),item.sourceEra(),item.itemLevel(),item.rarity(),
                item.intrinsicThousandths(),item.intrinsicStats(),item.requirements(),List.of(),item.rngVersion(),true);
        var attributes=new EnumMap<RpgAttribute,Integer>(RpgAttribute.class);
        for(var attribute:RpgAttribute.values())attributes.put(attribute,500);
        var resolved=GearEquipmentResolution.resolve(99,attributes,List.of(new GearEquipmentResolution.Candidate(item,true,true,true)));
        assertEquals(List.of(item),resolved.validItems());
        return resolved.effects().snapshot();
    }
    private static final class HealthPort implements NativeResourcePort {
        double health=900;
        public double current(ResourceType type){return health;}
        public double maximum(ResourceType type){return 1000;}
        public void setCurrent(ResourceType type,double value){health=value;}
    }
    @Test void wa098AcceptedSharePaysDefaultOwnerOnceWithinLiveCreditWindow(){
        UUID world=UUID.randomUUID(),recipient=UUID.randomUUID(),uncredited=UUID.randomUUID();
        var rolled=admitted(true);var control=admitted(false);
        var runtime=new GearRecoveryRuntime();var callback=new GearCreditedDeathRecovery(runtime);
        var shares=List.of(new EncounterContributions.Share(recipient,10,1,20,1));
        var captured=Map.of(recipient,new HytaleEncounterRewards.RecoveryCapture(rolled,1000),
                uncredited,new HytaleEncounterRewards.RecoveryCapture(rolled,1000));
        HytaleEncounterRewards.dispatchRecovery(shares,world,"death/accepted",captured,callback::accepted);
        HytaleEncounterRewards.dispatchRecovery(shares,world,"death/accepted",captured,callback::accepted);
        HytaleEncounterRewards.dispatchRecovery(shares,world,"death/control",
                Map.of(recipient,new HytaleEncounterRewards.RecoveryCapture(control,1000)),callback::accepted);
        var port=new HealthPort();double now=System.nanoTime()/1e9;
        var pool=GearRecoveryRuntime.Pool.KILL_HEALTH;
        double expected=Math.min(40,1000*rolled.percent("WA-098"));
        assertTrue(expected>0);
        assertEquals(expected,runtime.pay(recipient,world.toString(),pool,1000,now,
                (ignored,requested,cap)->port.restoreResourceAtMost(ResourceType.HEALTH,requested,cap)).amount(),1e-8);
        assertEquals(900+expected,port.health,1e-8);
        assertEquals(0,runtime.pay(recipient,world.toString(),pool,1000,now+1.1,
                (ignored,requested,cap)->port.restoreResourceAtMost(ResourceType.HEALTH,requested,cap)).amount());
        assertFalse(runtime.hasPending(recipient,world.toString(),pool));
        assertFalse(runtime.hasPending(uncredited,world.toString(),pool));
    }
    @Test void acceptedShareUsesFrozenDeathFactsOnly() {
        UUID world=UUID.randomUUID(),recipient=UUID.randomUUID(),uncredited=UUID.randomUUID();
        var captured=new HytaleEncounterRewards.RecoveryCapture(GearEffectSnapshot.EMPTY,850);
        var delivered=new ArrayList<com.inigmasgames.hytalerpg.combat.hytale.GearCreditedDeathRecovery.Credit>();
        HytaleEncounterRewards.dispatchRecovery(
                List.of(new EncounterContributions.Share(recipient,10,1,20,1)),world,"death/17",
                Map.of(recipient,captured,uncredited,new HytaleEncounterRewards.RecoveryCapture(GearEffectSnapshot.EMPTY,1000)),
                delivered::add);
        assertEquals(1,delivered.size());
        var credit=delivered.getFirst();
        assertEquals(recipient,credit.recipient());
        assertEquals(world.toString(),credit.world());
        assertEquals("death/17",credit.rewardEventId());
        assertSame(captured.snapshot(),credit.validEquipmentAtDeath());
        assertEquals(850,credit.normalHealthMaximumAtDeath());
    }
    @Test void absentCaptureAndNoSharesPayNothing() {
        UUID world=UUID.randomUUID(),recipient=UUID.randomUUID();
        var delivered=new ArrayList<com.inigmasgames.hytalerpg.combat.hytale.GearCreditedDeathRecovery.Credit>();
        HytaleEncounterRewards.dispatchRecovery(List.of(new EncounterContributions.Share(recipient,1,0,1,1)),
                world,"death/18",Map.of(),delivered::add);
        HytaleEncounterRewards.dispatchRecovery(List.of(),world,"death/19",
                Map.of(recipient,new HytaleEncounterRewards.RecoveryCapture(GearEffectSnapshot.EMPTY,1000)),delivered::add);
        assertTrue(delivered.isEmpty());
    }
}
