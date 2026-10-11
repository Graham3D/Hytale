package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.gear.*;
import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.execution.GearResourceModifiers;
import com.inigmasgames.hytalerpg.execution.GearSupportModifiers;
import com.inigmasgames.hytalerpg.combat.resource.ResourceCost;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Full skill commit and canonical support executor with controlled valid-slot gear values. */
class GearSupportConsumerTest {
    private static GearInstance item(String baseId,String affixId,double value){
        var catalog=GearCatalog.load();var base=catalog.base(baseId);var a=catalog.affix(affixId);
        var tier=GearAffixTiers.compile(a).getLast();
        double armorFactor=base.category()==GearCatalog.Category.ARMOR?.5:1;
        assertTrue(value>=tier.low()*armorFactor&&value<=tier.high()*armorFactor);
        var roll=new GearInstance.AffixRoll(affixId,a.side(),a.exclusionGroup(),5,value,
                new GearRequirements.Gate(tier.requiredLevel(),Map.of(a.requirementAttribute(base),
                        a.attributeFloor(5-tier.tier(),tier.minimumItemLevel()))),a.name(),a.name());
        return GearInstance.authoredQa(base,UUID.randomUUID(),99,1000,GearRarity.MAGIC,
                List.of(roll),BigDecimal.ZERO);
    }
    @Test void committedMinorHealUsesFrozenHealingPowerAndOutgoingIncrease(){
        var ordinary=new Stage09SupportRuntimeTest.Harness("minor_heal");
        assertTrue(ordinary.cast().committed());double ordinaryHealth=ordinary.health;
        var geared=new Stage09SupportRuntimeTest.Harness("minor_heal"){
            @Override public GearAffixRuntime.Effects gearEffects(){return new GearAffixRuntime.Effects(Map.of(),0,0,0,0,0,0,
                    new GearEffectSnapshot(List.of(item("gm.staff_prismatic.h","WA-103",8),
                            item("gm.robes_pilgrim.head.h","WA-104",10))));}
        };
        assertTrue(geared.cast().committed());
        assertTrue(geared.health>ordinaryHealth);
        assertEquals(ordinary.mana,geared.mana,1e-9);
        assertEquals(8,geared.context.gearSnapshot().total(GearEffectSnapshot.Operator.HEALING_POWER),1e-9);
        assertEquals(.1,geared.context.gearSnapshot().percent(GearEffectSnapshot.Operator.HEALING_DONE),1e-9);
        assertEquals(0,ordinary.context.gearSnapshot().total(GearEffectSnapshot.Operator.HEALING_POWER));
    }
    @Test void namedRankChangesCommittedSkillWithoutPermanentLearning(){
        var catalog=GearCatalog.load();var a=catalog.affix("WA-122");
        var baseItem=catalog.base("gm.staff_prismatic.h");var tier=GearAffixTiers.compile(a).getFirst();
        var roll=new GearInstance.AffixRoll(a.id(),a.side(),a.exclusionGroup(),tier.tier(),1,
                new GearRequirements.Gate(tier.requiredLevel(),Map.of(a.requirementAttribute(baseItem),
                        a.attributeFloor(0,tier.minimumItemLevel()))),a.name(),a.name(),"minor_heal");
        var gear=GearInstance.authoredQa(baseItem,UUID.randomUUID(),99,1000,
                GearRarity.MAGIC,List.of(roll),BigDecimal.ZERO);
        var h=new Stage09SupportRuntimeTest.Harness("minor_heal"){
            @Override public GearAffixRuntime.Effects gearEffects(){return new GearAffixRuntime.Effects(Map.of(),0,0,0,0,0,0,
                    new GearEffectSnapshot(List.of(gear)));}
            @Override public int itemGrantedSkillLevels(com.inigmasgames.hytalerpg.execution.Stage04SkillProfile p,Equipment equipped){
                return com.inigmasgames.hytalerpg.execution.GearSkillRanks.bonus(gearEffects().snapshot(),p);
            }
        };
        int base=h.bundle.service().baseSkillRank(h.actor,"minor_heal");
        assertTrue(h.cast().committed());
        assertEquals(base+1,h.context.effectiveSkillLevel());
        assertEquals(base,h.bundle.service().baseSkillRank(h.actor,"minor_heal"));
    }
    @Test void liveBarrierCapacityReprojectsWithoutRestoringAbsorbedPoints(){
        var shield=item("gm.staff_prismatic.h","WA-105",20);
        var h=new Stage09SupportRuntimeTest.Harness("spirit_shield"){
            @Override public GearAffixRuntime.Effects gearEffects(){return new GearAffixRuntime.Effects(Map.of(),0,0,0,0,0,0,
                    new GearEffectSnapshot(List.of(shield)));}
            @Override public void finiteEffect(com.inigmasgames.hytalerpg.execution.SkillExecutionContext context,
                    com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects effects,double now){
                effects.applyShield(context,List.of(actor),context.profile().support().durationSeconds(),
                        com.inigmasgames.hytalerpg.execution.support.SupportMagnitude.shield(context,1),now);
            }
        };
        assertTrue(h.cast().committed());
        var effects=h.runtime.finite();double created=effects.forTarget(h.world,h.actor,0).getFirst().magnitude();
        effects.shieldHit(h.world,h.actor,created*.6,false,0,(effect,amount)->false);
        double spent=effects.forTarget(h.world,h.actor,0).getFirst().shieldRemaining();
        effects.reprojectShieldCapacity(h.actor,GearEffectSnapshot.EMPTY,0);
        var reduced=effects.forTarget(h.world,h.actor,0).getFirst();
        assertEquals(created,reduced.magnitude(),1e-9);
        assertEquals(spent,reduced.shieldRemaining(),1e-9);
        effects.reprojectShieldCapacity(h.actor,new GearEffectSnapshot(List.of(shield)),0);
        assertEquals(created,effects.forTarget(h.world,h.actor,0).getFirst().magnitude(),1e-9);
        assertEquals(spent,effects.forTarget(h.world,h.actor,0).getFirst().shieldRemaining(),1e-9);
        for(int cycle=0;cycle<20;cycle++){
            effects.reprojectShieldCapacity(h.actor,GearEffectSnapshot.EMPTY,0);
            effects.reprojectShieldCapacity(h.actor,GearEffectSnapshot.EMPTY,0);
            assertEquals(created,effects.forTarget(h.world,h.actor,0).getFirst().magnitude(),1e-9);
            effects.reprojectShieldCapacity(h.actor,new GearEffectSnapshot(List.of(shield)),0);
            effects.reprojectShieldCapacity(h.actor,new GearEffectSnapshot(List.of(shield)),0);
            var current=effects.forTarget(h.world,h.actor,0).getFirst();
            assertEquals(created,current.magnitude(),1e-9);
            assertEquals(spent,current.shieldRemaining(),1e-9);
            assertEquals(h.context.skillInstanceId(),current.skillInstanceId());
        }
    }
    @Test void genericStatusOwnerCleanseEmitsOnlyAfterActualRemovalAndGrantsRespite(){
        var clock=new java.util.concurrent.atomic.AtomicLong();
        var statuses=new com.inigmasgames.hytalerpg.combat.status.StatusService(
                com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile.loadCanonical(),clock::get);
        var finite=new com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects();
        var world=UUID.randomUUID();var caster=UUID.randomUUID();var recipient=UUID.randomUUID();
        var type=com.inigmasgames.hytalerpg.combat.status.RpgStatusType.BLIND;
        var gear=new GearEffectSnapshot(List.of(item("gm.staff_prismatic.h","WA-112",4)));
        var source=new com.inigmasgames.hytalerpg.combat.status.StatusService.CleanseSource(
                world,"generic/root","generic/instance","generic/correlation",caster,recipient,gear);
        var projected=new java.util.concurrent.atomic.AtomicInteger();
        var received=new java.util.concurrent.atomic.AtomicInteger();
        var created=new java.util.concurrent.atomic.AtomicReference<java.util.Optional<
                com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects.Effect>>(java.util.Optional.empty());
        java.util.function.Consumer<com.inigmasgames.hytalerpg.combat.status.StatusService.CleanseReceipt> consume=receipt->{
            received.incrementAndGet();
            assertEquals(List.of(type),receipt.removed());
            assertFalse(statuses.inspect(recipient).active().containsKey(type));
            assertEquals(received.get(),projected.get());
            created.set(com.inigmasgames.hytalerpg.execution.support.SupportCleanse.consume(finite,receipt,1000));
        };
        Runnable project=projected::incrementAndGet;
        statuses.cleanse(source,recipient,List.of(type),project,consume);
        assertEquals(0,received.get());assertEquals(0,projected.get());
        assertTrue(finite.forTarget(world,recipient,0).isEmpty());
        assertThrows(IllegalArgumentException.class,()->statuses.cleanse(source,UUID.randomUUID(),List.of(type),project,consume));
        assertThrows(IllegalArgumentException.class,()->statuses.cleanse(source,recipient,
                List.of(com.inigmasgames.hytalerpg.combat.status.RpgStatusType.BURN),project,consume));
        assertThrows(IllegalArgumentException.class,()->statuses.cleanse(source,recipient,
                List.of(com.inigmasgames.hytalerpg.combat.status.RpgStatusType.SLOW),project,consume));
        statuses.apply(recipient,type,com.inigmasgames.hytalerpg.combat.status.ControlProfile.NORMAL,30);
        statuses.cleanse(source,recipient,List.of(type),project,consume);
        var first=created.get().orElseThrow();
        assertEquals(40,first.magnitude(),1e-9);assertEquals(3,first.ends(),1e-9);
        assertNull(first.context());assertEquals("generic/root",first.rootCastId());
        assertEquals("generic/instance",first.skillInstanceId());
        assertSame(gear,first.admittedGear());
        assertEquals(1,first.admittedGear().sources(GearEffectSnapshot.Operator.CLEANSE_RESPITE).size());
        assertEquals(0,finite.shieldHit(world,recipient,15,false,0,(effect,amount)->false).remainder(),1e-9);
        assertEquals(25,finite.forTarget(world,recipient,0).getFirst().shieldRemaining(),1e-9);
        assertSame(gear,finite.forTarget(world,recipient,0).getFirst().admittedGear());
        clock.set(5_000_000_000L);
        statuses.apply(recipient,type,com.inigmasgames.hytalerpg.combat.status.ControlProfile.NORMAL,30);
        statuses.cleanse(source,recipient,List.of(type),project,consume);
        assertTrue(created.get().isEmpty());assertTrue(finite.forTarget(world,recipient,5).isEmpty());
        clock.set(10_000_000_000L);
        statuses.apply(recipient,type,com.inigmasgames.hytalerpg.combat.status.ControlProfile.NORMAL,30);
        statuses.cleanse(source,recipient,List.of(type),project,consume);
        assertEquals(40,created.get().orElseThrow().shieldRemaining(),1e-9);
        assertEquals(0,finite.shieldHit(world,recipient,40,false,10,(effect,amount)->false).remainder(),1e-9);
        assertTrue(finite.forTarget(world,recipient,10).isEmpty());
        clock.set(20_000_000_000L);
        statuses.apply(recipient,type,com.inigmasgames.hytalerpg.combat.status.ControlProfile.NORMAL,30);
        int before=received.get();
        assertThrows(IllegalStateException.class,()->statuses.cleanse(source,recipient,List.of(type),
                ()->{throw new IllegalStateException("cancelled projection");},consume));
        assertEquals(before,received.get());assertTrue(finite.forTarget(world,recipient,20).isEmpty());
        statuses.apply(recipient,type,com.inigmasgames.hytalerpg.combat.status.ControlProfile.NORMAL,1);
        clock.set(22_000_000_000L);statuses.cleanse(source,recipient,List.of(type),project,consume);
        assertEquals(before,received.get());
        statuses.apply(recipient,type,com.inigmasgames.hytalerpg.combat.status.ControlProfile.NORMAL,30);
        assertTrue(statuses.remove(recipient,type));assertEquals(before,received.get());
        var plain=new com.inigmasgames.hytalerpg.combat.status.StatusService.CleanseSource(
                world,"plain/root","plain/instance","plain/correlation",caster,recipient,GearEffectSnapshot.EMPTY);
        statuses.apply(recipient,type,com.inigmasgames.hytalerpg.combat.status.ControlProfile.NORMAL,30);
        statuses.cleanse(plain,recipient,List.of(type),project,receipt->
                assertTrue(com.inigmasgames.hytalerpg.execution.support.SupportCleanse.consume(finite,receipt,1000).isEmpty()));
        statuses.apply(recipient,type,com.inigmasgames.hytalerpg.combat.status.ControlProfile.NORMAL,30);
        statuses.cleanse(source,recipient,List.of(type),project,receipt->{
            assertEquals(40,com.inigmasgames.hytalerpg.execution.support.SupportCleanse.consume(finite,receipt,1000)
                    .orElseThrow().magnitude(),1e-9);
            assertTrue(com.inigmasgames.hytalerpg.execution.support.SupportCleanse.consume(finite,receipt,1000).isEmpty());
        });
        finite.forget(recipient);assertTrue(finite.forTarget(world,recipient,22).isEmpty());
        statuses.apply(recipient,type,com.inigmasgames.hytalerpg.combat.status.ControlProfile.NORMAL,30);
        statuses.cleanse(source,recipient,List.of(type),project,receipt->
                assertEquals(40,com.inigmasgames.hytalerpg.execution.support.SupportCleanse.consume(finite,receipt,1000)
                        .orElseThrow().magnitude(),1e-9));
        finite.clearWorld(world);assertEquals(0,finite.size());
        statuses.apply(recipient,type,com.inigmasgames.hytalerpg.combat.status.ControlProfile.NORMAL,30);
        statuses.cleanse(source,recipient,List.of(type),project,receipt->
                assertEquals(40,com.inigmasgames.hytalerpg.execution.support.SupportCleanse.consume(finite,receipt,1000)
                        .orElseThrow().magnitude(),1e-9));
        finite.clearWorld(world);assertEquals(0,finite.size());
        statuses.apply(recipient,type,com.inigmasgames.hytalerpg.combat.status.ControlProfile.NORMAL,30);
        statuses.forget(recipient);statuses.cleanse(source,recipient,List.of(type),project,consume);
        assertEquals(before,received.get());
    }
    @Test void channelRampUsesOnlyPaidNativeConnectionPulses(){
        var source=item("gm.staff_prismatic.h","WA-108",4);
        var h=new Stage08ConnectionTest.Harness("void_beam"){
            @Override public GearAffixRuntime.Effects gearEffects(){return new GearAffixRuntime.Effects(Map.of(),0,0,0,0,0,0,
                    new GearEffectSnapshot(List.of(source)));}
        };
        h.targets.add(Stage08ConnectionTest.target("victim",0,1.35,5));
        assertTrue(h.cast().committed());
        for(int n=1;n<=100;n++)h.advance(n*.05);
        assertEquals(20,h.hits.size());
        assertEquals(.45*.25,h.hits.getFirst().coefficient(),1e-9);
        assertEquals(.45*.25,h.hits.getLast().coefficient(),1e-9);
        assertEquals(1.01,h.hitContexts.getFirst().snapshot().modifiers().factor(),1e-9);
        assertEquals(1.12,h.hitContexts.getLast().snapshot().modifiers().factor(),1e-9);
        assertEquals(180,h.mana,1e-9);
        var zero=h.contexts.getFirst().withSnapshot(h.contexts.getFirst().snapshot().withModifiers(
                new com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets(List.of(),List.of(1d),List.of(),List.of())));
        assertEquals(0,zero.snapshot().modifiers().factor());
        assertEquals(.12,GearSupportModifiers.channelPayload(zero,3).snapshot().modifiers().factor(),1e-9);
        var unpaid=new Stage08ConnectionTest.Harness("void_beam"){
            @Override public GearAffixRuntime.Effects gearEffects(){return new GearAffixRuntime.Effects(Map.of(),0,0,0,0,0,0,
                    new GearEffectSnapshot(List.of(source)));}
        };
        unpaid.targets.add(Stage08ConnectionTest.target("victim",0,1.35,5));unpaid.mana=.5;
        assertTrue(unpaid.cast().committed());unpaid.advance(.25);
        assertTrue(unpaid.hits.isEmpty());assertEquals(.5,unpaid.mana,1e-9);
    }
    @Test void paidHealingTetherRampEntersTheRealHealingBucket(){
        var ordinary=new Stage13SupportTetherTest.Healing();
        assertTrue(ordinary.cast().committed());ordinary.stepTo(1);
        var source=item("gm.staff_prismatic.h","WA-108",4);
        var geared=new Stage13SupportTetherTest.Healing(){
            @Override public GearAffixRuntime.Effects gearEffects(){return new GearAffixRuntime.Effects(Map.of(),0,0,0,0,0,0,
                    new GearEffectSnapshot(List.of(source)));}
        };
        assertTrue(geared.cast().committed());geared.stepTo(1);
        assertEquals(ordinary.coefficients,geared.coefficients);
        assertEquals(ordinary.mana,geared.mana,1e-9);
        double wisdom=geared.contexts.getFirst().snapshot().derivedStats().healingMultiplier();
        assertEquals(ordinary.hp.get(Stage08ConnectionCohortBTest.id(1))
                +20*.1125*wisdom*(.01+.02+.03+.04),
                geared.hp.get(Stage08ConnectionCohortBTest.id(1)),1e-8);
        var unpaid=new Stage13SupportTetherTest.Healing(){
            @Override public GearAffixRuntime.Effects gearEffects(){return new GearAffixRuntime.Effects(Map.of(),0,0,0,0,0,0,
                    new GearEffectSnapshot(List.of(source)));}
        };
        unpaid.mana=.5;assertTrue(unpaid.cast().committed());unpaid.advance(.25);
        assertTrue(unpaid.coefficients.isEmpty());
    }
    @Test void channelCostReductionPaysThroughTheExistingConnectionResourceOwner(){
        var source=item("gm.staff_prismatic.h","WA-107",12);
        var h=new Stage08ConnectionTest.Harness("void_beam"){
            @Override public GearAffixRuntime.Effects gearEffects(){return new GearAffixRuntime.Effects(Map.of(),0,0,0,0,0,0,
                    new GearEffectSnapshot(List.of(source)));}
            @Override public boolean payUpkeep(SkillExecutionContext context,int tick,double seconds){
                var cost=kernel.resources().evaluateUpkeep(
                        new ResourceCost(ResourceType.MANA,context.profile().connection().upkeepPerSecond()*seconds),
                        context.compiledPlan().kernelModifiers(),
                        GearResourceModifiers.factor(context.gearSnapshot(),ResourceType.MANA,true,false));
                if(!kernel.resources().canAfford(owner,cost,this))return false;
                var token=kernel.resources().reserveCost(owner,cost,this);
                try{return kernel.resources().commitCost(token,this);}finally{kernel.resources().finish(token);}
            }
        };
        h.targets.add(Stage08ConnectionTest.target("victim",0,1.35,5));
        assertTrue(h.cast().committed());for(int n=1;n<=100;n++)h.advance(n*.05);
        assertEquals(20,h.hits.size());assertEquals(182.4,h.mana,1e-8);
        assertEquals(.45*.25,h.hits.getFirst().coefficient(),1e-9);
    }
}
