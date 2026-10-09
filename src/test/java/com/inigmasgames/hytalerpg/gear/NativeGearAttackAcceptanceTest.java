package com.inigmasgames.hytalerpg.gear;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NativeGearAttackAcceptanceTest {
    private final UUID world=UUID.fromString("00000000-0000-0000-0000-000000000011");
    private final UUID actor=UUID.fromString("00000000-0000-0000-0000-000000000012");
    private GearInstance item(){
        var suite=new GearAffixQaSuite(GearCatalog.load());
        return suite.preview(suite.fixtures().stream()
                .filter(f->f.fixtureId().equals("ab-wa-136-affixed")).findFirst().orElseThrow());
    }
    @Test void acceptedNoncriticalStrikeFreezesDeadlyAndRejectsSourceDrift(){
        var item=item();var snapshot=new GearEffectSnapshot(List.of(item));
        var original=GearCombatEffects.attack(snapshot,item.identity(),"native/root",100,1,
                true,false,0,null,0,1.5,false);
        var runtime=new GearSignatureProcRuntime(()->0);
        var accepted=NativeGearAttackAcceptance.commit(world,actor,original,1,true,"native/primary",runtime);
        try {
            assertEquals(200,accepted.amount(GearCombatEffects.Channel.PHYSICAL),1e-9);
            var facts=NativeGearAttackAcceptance.find(world,actor,accepted);
            assertSame(accepted,facts.hit());assertEquals(100,facts.noncriticalPhysical(),1e-9);
            assertEquals(1,facts.procCoefficient(),1e-9);assertTrue(facts.melee());
            assertThrows(IllegalStateException.class,()->NativeGearAttackAcceptance.find(world,actor,original));
        } finally {NativeGearAttackAcceptance.clearWorld(world);}
        assertNull(NativeGearAttackAcceptance.find(world,actor,accepted));
    }
    @Test void criticalAndZeroCoefficientControlsDoNotBecomeDeadly(){
        var item=item();var snapshot=new GearEffectSnapshot(List.of(item));
        var critical=GearCombatEffects.attack(snapshot,item.identity(),"native/critical",100,1,
                true,false,0,null,1,1.5,true,null,
                new com.inigmasgames.hytalerpg.combat.damage.CriticalRoller(()->0));
        var runtime=new GearSignatureProcRuntime(()->0);
        var result=NativeGearAttackAcceptance.commit(world,actor,critical,1,true,"native/primary",runtime);
        try {
            assertSame(critical,result);assertTrue(result.critical());
            assertEquals(100,NativeGearAttackAcceptance.find(world,actor,result).noncriticalPhysical(),1e-9);
            var zero=GearCombatEffects.attack(snapshot,item.identity(),"native/zero",100,1,
                    true,false,0,null,0,1.5,false);
            assertSame(zero,NativeGearAttackAcceptance.commit(world,actor,zero,0,true,"native/primary",runtime));
            assertThrows(IllegalArgumentException.class,()->NativeGearAttackAcceptance.commit(world,actor,
                    critical,1.1,true,"native/primary",runtime));
        } finally {NativeGearAttackAcceptance.clearWorld(world);}
    }
    @Test void prechainFreezesValidItemAndBaselineUntilWorldCleanup(){
        var item=item();var snapshot=new GearEffectSnapshot(List.of(item));
        var chain=new NativeGearAttackAcceptance.Chain(world,actor,item.identity(),snapshot,.12,1.8,"native/primary");
        NativeGearAttackAcceptance.captureChain("42",chain);
        try {
            assertSame(chain,NativeGearAttackAcceptance.chain(world,actor,"42"));
            assertEquals(.12,NativeGearAttackAcceptance.chain(world,actor,"42").baselineCritChance(),1e-9);
            assertThrows(IllegalStateException.class,()->NativeGearAttackAcceptance.captureChain("42",
                    new NativeGearAttackAcceptance.Chain(world,actor,item.identity(),snapshot,.5,2,"native/primary")));
            assertNull(NativeGearAttackAcceptance.chain(world,actor,"different"));
        } finally {NativeGearAttackAcceptance.clearWorld(world);}
        assertNull(NativeGearAttackAcceptance.chain(world,actor,"42"));
    }
    @Test void nativeSelectorProtocolRequiresAuthoredCoefficientPerStrike(){
        assertEquals(.25,NativeGearAttackAcceptance.coefficient("pellet/1",
                Map.of("RpgProcSelector","pellet/1","RpgProcCoefficient","0.25")),1e-9);
        assertThrows(IllegalArgumentException.class,()->NativeGearAttackAcceptance.coefficient("pellet/2",
                Map.of("RpgProcSelector","pellet/1","RpgProcCoefficient","0.25")));
        assertThrows(IllegalArgumentException.class,()->NativeGearAttackAcceptance.coefficient("pellet/1",
                Map.of("RpgProcSelector","pellet/1")));
        assertThrows(IllegalArgumentException.class,()->NativeGearAttackAcceptance.coefficient("pellet/1",
                Map.of("RpgProcSelector","pellet/1","RpgProcCoefficient","1.1")));
    }
    @Test void oneProcRootRetainsDistinctCommittedStrikesAndRejectsAReplacementSource(){
        var item=item();var snapshot=new GearEffectSnapshot(List.of(item));
        var runtime=new GearSignatureProcRuntime(()->.99);
        var first=GearCombatEffects.attack(snapshot,item.identity(),"multi/root",100,1,
                true,false,0,null,0,1.5,false);
        var second=GearCombatEffects.attack(snapshot,item.identity(),"multi/root",50,.5,
                true,false,0,null,0,1.5,false);
        try {
            first=NativeGearAttackAcceptance.commit(world,actor,first,.5,true,"strike/first",runtime);
            second=NativeGearAttackAcceptance.commit(world,actor,second,.25,true,"strike/second",runtime);
            assertSame(first,NativeGearAttackAcceptance.find(world,actor,first).hit());
            assertSame(second,NativeGearAttackAcceptance.find(world,actor,second).hit());
            assertEquals(.25,NativeGearAttackAcceptance.find(world,actor,second).procCoefficient());
            var forged=new GearCombatEffects.Hit(first.itemId(),first.revision(),first.rootId(),first.amounts(),
                    first.penetration(),first.increased(),first.critical(),first.criticalMultiplier(),first.snapshot(),first.origin());
            assertThrows(IllegalStateException.class,()->NativeGearAttackAcceptance.find(world,actor,forged));
            var other=GearInstance.authoredQa(GearCatalog.load().base(item.baseId()),UUID.randomUUID(),99,1000,
                    GearRarity.COMMON,List.of(),java.math.BigDecimal.ZERO);
            var swapped=GearCombatEffects.attack(new GearEffectSnapshot(List.of(other)),other.identity(),"multi/root",50,1,
                    true,false,0,null,0,1.5,false);
            assertThrows(IllegalStateException.class,()->NativeGearAttackAcceptance.commit(world,actor,swapped,1,true,"strike/third",runtime));
        } finally {NativeGearAttackAcceptance.clearWorld(world);}
    }
    @Test void creditedDeathEmitsChildOnceAndUncreditedControlEmitsNone(){
        var suite=new GearAffixQaSuite(GearCatalog.load());
        var burst=suite.preview(suite.fixtures().stream().filter(f->f.fixtureId().equals("ab-wa-143-affixed"))
                .findFirst().orElseThrow());
        var snapshot=new GearEffectSnapshot(List.of(burst));
        var victim=UUID.fromString("00000000-0000-0000-0000-000000000013");
        var neighbor=UUID.fromString("00000000-0000-0000-0000-000000000014");
        var contact=new GearSignatureProcRuntime.Contact(world,actor,burst.identity(),victim,"native/root","contact",
                snapshot,true,true,true,false,false,false,25,0,1000,GearSignatureProcRuntime.Kind.COMMON,
                100,Map.of(GearCombatEffects.Channel.PHYSICAL,100d),1,false,false,true);
        var emitted=new java.util.ArrayList<GearSignatureProcRuntime.Child>();
        GearSignatureProcRuntime.Port port=new GearSignatureProcRuntime.Port(){
            @Override public void enqueue(GearSignatureProcRuntime.Child child){emitted.add(child);}
            @Override public boolean execute(GearSignatureProcRuntime.Contact ignored){return false;}
            @Override public List<UUID> burstTargets(GearSignatureProcRuntime.Contact ignored,double radius,int maximum){
                return List.of(victim,neighbor);
            }
        };
        var runtime=new GearSignatureProcRuntime(()->0);
        runtime.applied(contact,1,port);assertTrue(emitted.isEmpty());
        runtime.creditedKill(contact,1,port);runtime.creditedKill(contact,1,port);
        assertEquals(1,emitted.size());assertEquals(neighbor,emitted.getFirst().target());
        assertTrue(emitted.getFirst().noProc());assertTrue(emitted.getFirst().noLeech());
        var uncredited=new GearSignatureProcRuntime.Contact(contact.world(),contact.owner(),contact.item(),
                contact.target(),"native/other","other",contact.snapshot(),contact.direct(),contact.melee(),
                contact.hostile(),contact.noProc(),contact.reflected(),contact.blocked(),contact.actualHpLoss(),
                0,contact.targetNormalMaximum(),contact.targetKind(),contact.noncriticalPhysical(),
                contact.preMitigation(),contact.procCoefficient(),contact.protectedTarget(),contact.scriptedVeto(),false);
        runtime.creditedKill(uncredited,2,port);
        assertEquals(1,emitted.size());
    }
    @Test void ordinaryManagedGearNeedsNoSignatureSelector(){
        var rolled=item();var base=GearCatalog.load().base(rolled.baseId());
        var ordinary=new GearInstance(rolled.schemaVersion(),UUID.randomUUID(),rolled.definitionRevision(),
                rolled.baseId(),rolled.baseName(),rolled.category(),rolled.sourceEra(),rolled.itemLevel(),
                GearRarity.COMMON,rolled.intrinsicThousandths(),rolled.intrinsicStats(),
                new GearRequirements.Gate(base.requiredLevel(),base.requiredAttributes()),
                List.of(),rolled.rngVersion(),true);
        var hit=GearCombatEffects.attack(new GearEffectSnapshot(List.of(ordinary)),ordinary.identity(),
                "ordinary/root",100,1,true,false,0,null,0,1.5,false);
        assertSame(hit,NativeGearAttackAcceptance.commit(world,actor,hit,true,"native/ordinary",Map.of()));
    }
}
