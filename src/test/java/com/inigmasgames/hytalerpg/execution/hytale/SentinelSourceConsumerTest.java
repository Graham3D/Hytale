package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.summon.IronSentinelAffixes;
import com.inigmasgames.hytalerpg.execution.summon.IronSentinelBinding;
import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.status.*;
import com.inigmasgames.hytalerpg.gear.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SentinelSourceConsumerTest {
    private final GearCatalog catalog=GearCatalog.load();

    @Test void missingNativeRecipientCannotAcquireBoundGear(){
        assertTrue(GearNativeItems.recipientEffects(null,
                (com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>)null).empty());
    }

    @Test void sentinelReachChangesActualMeleeClaimDistanceAndNoOtherSourceDoes(){
        var bonus=lease(item("WA-014",.30));
        var control=lease(item(null,0));
        var ownerOnly=lease(item("WA-151",25));
        assertTrue(IronSentinelAffixes.classify("WA-014").adapted());
        IronSentinelAffixes.requireAdapted(bonus.boundItem());
        assertEquals(2.8,HytaleSummonSystem.sentinelMeleeReach(bonus),1e-9);
        assertEquals(2.5,HytaleSummonSystem.sentinelMeleeReach(control),1e-9);
        assertEquals(2.5,HytaleSummonSystem.sentinelMeleeReach(ownerOnly),1e-9);
        assertTrue(2.7<=HytaleSummonSystem.sentinelMeleeReach(bonus));
        assertFalse(2.7<=HytaleSummonSystem.sentinelMeleeReach(control));
    }

    @Test void eachSentinelConditionalUsesFrozenOriginAndLiveVictimFacts(){
        var origin=new Vec3(0,0,0);
        for(int id=41;id<=52;id++){
            String affix=String.format(Locale.ROOT,"WA-%03d",id);
            var source=item(affix,20);
            IronSentinelAffixes.requireAdapted(source);
            var hit=GearCombatEffects.attack(new GearEffectSnapshot(List.of(source)),source.identity(),
                    "sentinel/attack/"+id,10,1,true,false,0,null,0,1.5,false,origin);
            var plain=item(null,0);
            var control=GearCombatEffects.attack(new GearEffectSnapshot(List.of(plain)),plain.identity(),
                    "sentinel/control/"+id,10,1,true,false,0,null,0,1.5,false,origin);
            var positive=facts(id,true);
            var negative=facts(id,false);
            assertEquals(.20,GearHitConditions.increased(hit,positive),1e-9,affix);
            assertEquals(0,GearHitConditions.increased(control,positive),1e-9,affix+" control");
            assertEquals(0,GearHitConditions.increased(hit,negative),1e-9,affix+" negative");
            assertEquals(origin,hit.origin(),affix+" source origin");
        }
    }

    @Test void boundItemProjectileChildrenUseExactSourceTravelAndRejectInstantLines(){
        for(String id:List.of("WA-015","WA-016")){
            var source=item(id,20);var lease=lease(source);
            IronSentinelAffixes.requireAdapted(source);
            var launch=NativeAffixProjectileTravel.launch(lease.boundEffects(),source.identity(),20,40,3,false);
            assertNotNull(launch,id);
            assertEquals(source.identity(),launch.sourceItem());
            assertEquals(lease.boundEffects().revision(),launch.equipmentRevision());
            assertEquals(id.equals("WA-015")?1.2:1,launch.speedMultiplier(),1e-6,id);
            assertEquals(id.equals("WA-016")?48:40,launch.maximumTravel(),1e-9,id);
            var control=item(null,0);
            assertNull(NativeAffixProjectileTravel.launch(lease(control).boundEffects(),control.identity(),20,40,3,false));
            assertNull(NativeAffixProjectileTravel.launch(lease.boundEffects(),source.identity(),20,40,3,true));
            assertNull(NativeAffixProjectileTravel.launch(lease.boundEffects(),UUID.randomUUID(),20,40,3,false));
        }
    }
    @Test void boundRecipientIncreasesActualMinorHealRequestOnce(){
        var source=lease(item("WA-111",20));var control=lease(item(null,0));
        assertEquals(.20,source.boundEffects().percent(GearEffectSnapshot.Operator.HEALING_RECEIVED),1e-9);
        assertEquals(0,control.boundEffects().percent(GearEffectSnapshot.Operator.HEALING_RECEIVED),1e-9);
        assertEquals(120,HytaleSummonSystem.sentinelReceivedHealing(source,100),1e-9);
        assertEquals(100,HytaleSummonSystem.sentinelReceivedHealing(control,100),1e-9);
        assertEquals(0,HytaleSummonSystem.sentinelReceivedHealing(source,0),1e-9);
        assertThrows(IllegalArgumentException.class,()->HytaleSummonSystem.sentinelReceivedHealing(source,-1));
    }
    @Test void boundRecipientReducesOnlyCriticalExcess(){
        var source=lease(item("WA-084",20));var control=lease(item(null,0));
        assertEquals(140,GearDefenseEffects.criticalAmount(source.boundEffects(),100,150),1e-9);
        assertEquals(150,GearDefenseEffects.criticalAmount(control.boundEffects(),100,150),1e-9);
        assertEquals(140,HytaleSummonSystem.sentinelCriticalTaken(source,100,150),1e-9);
        assertEquals(150,HytaleSummonSystem.sentinelCriticalTaken(control,100,150),1e-9);
        assertEquals(100,HytaleSummonSystem.sentinelCriticalTaken(source,100,100),1e-9);
        assertThrows(IllegalArgumentException.class,()->HytaleSummonSystem.sentinelCriticalTaken(source,151,150));
    }
    @Test void boundRecipientUsesCanonicalStatusAndDisplacementOwners(){
        var empty=GearEffectSnapshot.EMPTY;
        var resistance=lease(item("WA-079",20)).boundEffects();
        assertEquals(.2,GearStatusRuntime.chance(0,.5,1,0,empty,resistance).effectiveResistance(),1e-9);
        assertEquals(0,GearStatusRuntime.chance(0,.5,1,0,empty,empty).effectiveResistance(),1e-9);
        var duration=lease(item("WA-080",20)).boundEffects();
        var statuses=new StatusService(CombatBalanceProfile.loadCanonical(),()->0);
        var target=UUID.randomUUID();
        assertEquals(1.6,statuses.apply(target,RpgStatusType.STUN,ControlProfile.NORMAL,2,duration).remainingSeconds(),1e-9);
        assertEquals(2,new StatusService(CombatBalanceProfile.loadCanonical(),()->0)
                .apply(UUID.randomUUID(),RpgStatusType.STUN,ControlProfile.NORMAL,2,empty).remainingSeconds(),1e-9);
        var slow=lease(item("WA-081",20)).boundEffects();
        statuses.applySlow(target,"source",.5,5);
        assertTrue(statuses.strongestSlow(target,slow).magnitude()<statuses.strongestSlow(target,empty).magnitude());
        var anchor=lease(item("WA-082",20)).boundEffects();
        var from=new Vec3(0,0,0);var center=new Vec3(10,0,0);
        var protectedPlan=NativeAffixHostileDisplacement.plan(from,center,false,5,ControlProfile.NORMAL,true,
                anchor,(point,segment)->1,point->true);
        var ordinaryPlan=NativeAffixHostileDisplacement.plan(from,center,false,5,ControlProfile.NORMAL,true,
                empty,(point,segment)->1,point->true);
        assertEquals(ordinaryPlan.distance()*.8,protectedPlan.distance(),1e-9);
        assertEquals(0,NativeAffixHostileDisplacement.plan(from,center,false,5,
                new ControlProfile(true,false,false),true,anchor,(point,segment)->1,point->true).distance(),1e-9);
    }

    @Test void eachBoundStatusChanceRequiresAnAcceptedMatchingNativeChannel(){
        for(int number=53;number<=63;number++){
            String id=String.format(Locale.ROOT,"WA-%03d",number);
            RpgStatusType type=switch(number){
                case 53->RpgStatusType.BLEED;case 54->RpgStatusType.BURN;
                case 55->RpgStatusType.POISON;case 56->RpgStatusType.CHILL;
                case 57->RpgStatusType.ELECTRIFIED;case 58->RpgStatusType.SLOW;
                case 59->RpgStatusType.STUN;case 60->RpgStatusType.SILENCE;
                case 61->RpgStatusType.BLIND;case 62->RpgStatusType.FEAR;
                default->RpgStatusType.ROOT;
            };
            String channelRoll=switch(number){case 54->"WA-019";case 56->"WA-018";
                case 57->"WA-021";default->null;};
            var item=channelRoll==null?item(id,100):itemWith(id,channelRoll);
            var lease=lease(item);
            IronSentinelAffixes.requireAdapted(item);
            var hit=GearCombatEffects.attack(lease.boundEffects(),item.identity(),
                    lease.rootCastId()+"/attack/1",10,1,true,false,0,null,0,1.5,false,Vec3.ZERO);
            assertTrue(GearStatusBindings.requires(hit),id);
            var channels=hit.amounts();
            var actor=UUID.randomUUID();var target=UUID.randomUUID();
            var accepted=new GearStatusRuntime.AppliedHit(actor,hit.rootId(),"contact",target,
                    true,true,true,false,10,1,channels);
            var blocked=new GearStatusRuntime.AppliedHit(actor,hit.rootId()+"/blocked","blocked",target,
                    true,true,false,false,10,1,channels);
            var statuses=new StatusService(CombatBalanceProfile.loadCanonical(),()->0);
            var source=GearStatusRuntime.sourceScoped(lease.boundEffects(),item.identity());
            assertEquals("ADMITTED",GearStatusRuntime.admit(statuses,new GearStatusRuntime.Contacts(),
                    accepted,type,ControlProfile.NORMAL,source,GearEffectSnapshot.EMPTY,0,0,0,0,
                    (kind,applied,gear)->"APPLIED").gate(),id+" positive");
            assertEquals("CHANCE_MISS",GearStatusRuntime.admit(statuses,new GearStatusRuntime.Contacts(),
                    accepted,type,ControlProfile.NORMAL,GearEffectSnapshot.EMPTY,GearEffectSnapshot.EMPTY,
                    0,0,0,0,(kind,applied,gear)->"APPLIED").gate(),id+" control");
            assertEquals("INELIGIBLE_HIT",GearStatusRuntime.admit(statuses,new GearStatusRuntime.Contacts(),
                    blocked,type,ControlProfile.NORMAL,source,GearEffectSnapshot.EMPTY,0,0,0,0,
                    (kind,applied,gear)->"APPLIED").gate(),id+" no proc");
        }
    }

    private static GearHitConditions.Context facts(int id,boolean positive){
        Vec3 target=id==42?(positive?new Vec3(13,0,0):new Vec3(2,0,0)):
                id==41?(positive?new Vec3(2,0,0):new Vec3(6,0,0)):new Vec3(2,0,0);
        Vec3 forward=id==43&&positive?new Vec3(1,0,0):new Vec3(-1,0,0);
        double hp=id==44?(positive?29:31):id==45?(positive?80:79):50;
        Set<String> statuses=positive?switch(id){
            case 46->Set.of("STUN");case 47->Set.of("BURN");case 48->Set.of("CHILL");
            case 49->Set.of("ELECTRIFIED");case 50->Set.of("POISON");case 51->Set.of("BLEED");
            default->Set.of();}:Set.of();
        return new GearHitConditions.Context(Vec3.ZERO,target,forward,hp,100,statuses,id==52&&positive);
    }

    private com.inigmasgames.hytalerpg.execution.summon.SummonRegistry.Lease lease(GearInstance item){
        UUID world=UUID.randomUUID(),owner=UUID.randomUUID();
        var binding=new IronSentinelBinding(3,UUID.randomUUID(),owner,"event",item,
                IronSentinelBinding.State.RESTORING,40,world,Vec3.ZERO,1,0,25,1,2,List.of());
        var registry=new com.inigmasgames.hytalerpg.execution.summon.SummonRegistry();
        return registry.restoreIronSentinel(binding,10);
    }

    private GearInstance item(String id,double value){
        var base=catalog.base("gm.sword_iron.n");
        List<GearInstance.AffixRoll> rolls;
        if(id==null)rolls=List.of();
        else{
            var affix=catalog.affix(id);
            rolls=List.of(new GearInstance.AffixRoll(id,affix.side(),affix.exclusionGroup(),1,value,
                    new GearRequirements.Gate(1,Map.of()),"Sentinel source consumer",affix.name()));
        }
        return GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.UNCOMMON,rolls,BigDecimal.ZERO);
    }
    private GearInstance itemWith(String... ids){
        var base=catalog.base("gm.sword_iron.n");
        var rolls=new ArrayList<GearInstance.AffixRoll>();
        for(var id:ids){var affix=catalog.affix(id);rolls.add(new GearInstance.AffixRoll(id,affix.side(),
                affix.exclusionGroup(),1,100,new GearRequirements.Gate(1,Map.of()),
                "Sentinel source consumer",affix.name()));}
        return GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.MAGIC,rolls,BigDecimal.ZERO);
    }
}
