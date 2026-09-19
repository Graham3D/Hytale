package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.combat.damage.*;
import com.inigmasgames.hytalerpg.combat.damage.WeaponDamageExecution.*;
import com.inigmasgames.hytalerpg.combat.resource.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

/** Production coordinator with real resource service; fixture producers are NOT connected native proof. */
class WeaponFireDecisionTest {
    static final UUID ACTOR=UUID.randomUUID(),WORLD=UUID.randomUUID();
    static Component fire(String id,double amount,Provenance p){return new Component(id,"FIRE",amount,p,true,true);}
    static WeaponDamageExecution execution(Delivery delivery,boolean derived,List<Component> parts,String tick){
        return new WeaponDamageExecution(new Identity(WORLD,ACTOR,"root","execution",tick),"audited-item",delivery,derived,true,true,parts);
    }
    static WeaponDamageExecution execution(){return execution(Delivery.RPG_WEAPON,false,List.of(
            new Component("physical","PHYSICAL",400,Provenance.WEAPON,true,true),fire("fire",100,Provenance.WEAPON)),"hit0");}
    static List<WeaponFireDecision.Recipient> recipients(int count,double damage){
        var out=new ArrayList<WeaponFireDecision.Recipient>();
        for(int i=0;i<count;i++)out.add(new WeaponFireDecision.Recipient(new UUID(0,i+1),damage));return out;
    }
    static class Port implements NativeResourcePort {
        double maximum=100,current=100;int writes;boolean failAfterWrite;
        public double current(ResourceType type){return current;}
        public double maximum(ResourceType type){return maximum;}
        public void setCurrent(ResourceType type,double value){assertEquals(ResourceType.MANA,type);current=value;writes++;if(failAfterWrite)throw new IllegalStateException("fixture write uncertainty");}
    }
    @ParameterizedTest @EnumSource(value=Delivery.class,names={"NATIVE_MELEE","NATIVE_RANGED","RPG_WEAPON"})
    void completeProducerEnvelopesHaveTheSameSourceContract(Delivery delivery){
        var e=execution(delivery,false,List.of(fire("weapon",60,Provenance.WEAPON),fire("affix",30,Provenance.WEAPON_AFFIX),fire("imbue",10,Provenance.FLAME_WEAPON)),"tick1");
        assertEquals(100,e.sourceFire());assertEquals(3,e.components().size());
    }
    @ParameterizedTest @EnumSource(value=Delivery.class,names={"SPELL","PERIODIC","SUMMON","TRAP","ENVIRONMENT","REFLECTION","AURA"})
    void fireChannelAndWeaponFlagAloneCannotOverrideDelivery(Delivery delivery){
        assertEquals(0,execution(delivery,false,List.of(fire("fire",100,Provenance.WEAPON)),"tick").sourceFire());
    }
    @ParameterizedTest @EnumSource(value=Provenance.class,names={"SPELL","BURN","PERIODIC","SUMMON","TRAP","ENVIRONMENT","REFLECTION","DERIVED"})
    void nonweaponProvenanceCannotClaimDirectWeaponHit(Provenance p){
        assertThrows(IllegalArgumentException.class,()->fire("fire",100,p));
        var e=execution(Delivery.RPG_WEAPON,false,List.of(new Component("fire","FIRE",100,p,false,true)),"tick");assertEquals(0,e.sourceFire());
    }
    @Test void derivedMantleCannotConvertAgain(){assertEquals(0,execution(Delivery.AURA,true,List.of(fire("fire",100,Provenance.WEAPON)),"tick").sourceFire());}
    @Test void componentIdentityDeduplicatesIdenticalInputsButRejectsConflicts(){
        var c=fire("fire",100,Provenance.WEAPON);var e=execution(Delivery.RPG_WEAPON,false,List.of(c,c),"tick");assertEquals(100,e.sourceFire());
        assertThrows(IllegalArgumentException.class,()->execution(Delivery.RPG_WEAPON,false,List.of(c,fire("fire",99,Provenance.WEAPON)),"tick"));
        assertThrows(UnsupportedOperationException.class,()->e.components().clear());
    }
    @Test void directDamageDoesNotDependOnEchoPreflight(){assertEquals(100,new WeaponFireDecision(execution()).directAmount("fire",100));}
    @Test void paidEchoAddsTwentyFivePercentWithoutOwningOrRollingBackOriginalDamage(){
        var d=new WeaponFireDecision(execution());var p=new Port();
        double original=d.directAmount("fire",100);
        var r=d.decideProportional(true,()->recipients(1,25),1,RpgCombatKernel.createProduction().resources(),p,()->fail());
        assertEquals(WeaponFireDecision.Status.ECHO_COMMITTED,r.status());
        assertEquals(125,original+d.claimPulse().getFirst().generatedFire());
        assertEquals(100,d.directAmount("fire",100));assertEquals(400,d.directAmount("physical",400));
        assertEquals(.75,r.manaCost());assertEquals(1,p.writes);assertTrue(d.claimPulse().isEmpty());
        d.close();assertEquals(100,d.directAmount("fire",100));assertEquals(99.25,p.current);
    }
    @Test void oneSourceFiveVictimsOneQueryReceiptAndPulse(){
        var d=new WeaponFireDecision(execution());var p=new Port();var resource=RpgCombatKernel.createProduction().resources();int[] queries={0};
        var r=d.decide(true,()->{queries[0]++;return recipients(8,25);},1,resource,p,()->fail("not unaffordable"));
        assertEquals(100,r.sourceFire());assertEquals(200,r.generatedFire());assertEquals(6,r.manaCost());assertNotNull(r.resourceReceipt());
        for(int victim=0;victim<5;victim++){assertEquals(10+victim,d.directAmount("fire",10+victim));assertEquals(400,d.directAmount("physical",400));
            assertSame(r,d.decide(true,()->{fail("second query");return List.of();},1,resource,p,()->fail("deactivated")));}
        assertEquals(1,queries[0]);assertEquals(1,p.writes);assertEquals(8,d.claimPulse().size());assertTrue(d.claimPulse().isEmpty());assertEquals(94,p.current);
    }
    @ParameterizedTest @CsvSource({"1,.75","2,1.5","4,3","8,6","12,9","20,15","30,22.5","64,48"})
    void allCostAnchorsAndMultipleMaxima(int count,double percentage){
        for(double max:new double[]{100,200,123.456,10000}){
            assertEquals(max*percentage/100,WeaponFireDecision.eventCost(max,100,count*25,1),1e-10);
            assertEquals(max*percentage/100,WeaponFireDecision.proportionalEventCost(max,count,1),1e-10);
        }
    }
    @ParameterizedTest @CsvSource({"1,.85,.6375","1.15,1,.8625","1.25,1.2,1.125","1.30,1,.975",".90,1,.675","1.38,1,1.035"})
    void magnitudeAndExplicitCostFactorsAreAppliedOnce(double magnitude,double costFactor,double expected){
        assertEquals(expected,WeaponFireDecision.eventCost(100,100,25*magnitude,costFactor),1e-12);
    }
    @Test void zeroRecipientsPreservesRemoteFireWithoutPaymentOrPulse(){
        var p=new Port();p.current=0;var d=new WeaponFireDecision(execution());
        var r=d.decide(true,List::of,1,RpgCombatKernel.createProduction().resources(),p,()->fail("deactivated"));
        assertEquals(0,r.manaCost());assertEquals(0,p.writes);assertEquals(100,d.directAmount("fire",100));assertEquals(400,d.directAmount("physical",400));assertTrue(d.claimPulse().isEmpty());
    }
    @Test void insufficientManaKeepsEveryOriginalComponentAndDeactivatesOnce(){
        var p=new Port();p.current=5.99;var d=new WeaponFireDecision(execution());int[] off={0};var resource=RpgCombatKernel.createProduction().resources();
        var r=d.decide(true,()->recipients(8,25),1,resource,p,()->off[0]++);
        assertEquals(WeaponFireDecision.Status.INSUFFICIENT_MANA,r.status());assertEquals(0,p.writes);assertEquals(1,off[0]);
        for(int victim=0;victim<5;victim++)assertEquals(100,d.directAmount("fire",100));
        assertEquals(400,d.directAmount("physical",400));assertTrue(d.claimPulse().isEmpty());
        d.decide(true,()->recipients(1,25),1,resource,p,()->off[0]++);assertEquals(1,off[0]);
    }
    @Test void exactFractionalManaSucceeds(){var p=new Port();p.maximum=123.456;p.current=123.456*.0075;var d=new WeaponFireDecision(execution());
        assertEquals(WeaponFireDecision.Status.ECHO_COMMITTED,d.decide(true,()->recipients(1,25),1,RpgCombatKernel.createProduction().resources(),p,()->fail()).status());assertEquals(0,p.current,1e-12);}
    @Test void sourceIndependentOfVictimResistanceAndOverkill(){
        var d=new WeaponFireDecision(execution());var p=new Port();var r=d.decide(true,()->recipients(3,25),1,RpgCombatKernel.createProduction().resources(),p,()->fail());
        for(double factor:new double[]{1,.75,.25})assertEquals(100*factor,d.directAmount("fire",100*factor));
        assertEquals(100,r.sourceFire());assertEquals(2.25,r.manaCost());assertEquals(List.of(25d,25d,25d),r.recipients().stream().map(WeaponFireDecision.Recipient::generatedFire).toList());
    }
    @Test void distinctAuthoredTicksNeverUseATimeWindowDedup(){var p=new Port();var resource=RpgCombatKernel.createProduction().resources();
        for(String tick:List.of("flurry/0","flurry/1","whirlwind/0","whirlwind/1")){
            var d=new WeaponFireDecision(execution(Delivery.RPG_WEAPON,false,execution().components(),tick));
            d.decide(true,()->recipients(1,25),1,resource,p,()->fail());assertEquals(1,d.claimPulse().size());}
        assertEquals(4,p.writes);assertEquals(97,p.current);
    }
    @Test void inactiveDoesNotQueryOrSpend(){var d=new WeaponFireDecision(execution());var p=new Port();d.decide(false,()->{fail();return List.of();},1,RpgCombatKernel.createProduction().resources(),p,()->fail());assertEquals(100,d.directAmount("fire",100));assertEquals(0,p.writes);}
    @Test void proportionalProductionRouteRejectsCompletePulseAtNewCostWithoutPartialWork(){
        var p=new Port();p.current=5.99;var d=new WeaponFireDecision(execution());int[] off={0};
        var result=d.decideProportional(true,()->recipients(8,25),1,RpgCombatKernel.createProduction().resources(),p,()->off[0]++);
        assertEquals(WeaponFireDecision.Status.INSUFFICIENT_MANA,result.status());
        assertEquals(5.99,p.current);assertEquals(0,p.writes);assertEquals(1,off[0]);
        assertEquals(100,d.directAmount("fire",100));assertEquals(400,d.directAmount("physical",400));
        assertTrue(d.claimPulse().isEmpty());assertEquals(0,result.manaCost());
    }
    @Test void proportionalProductionRouteChargesFractionalAggregateOnce(){
        for(double max:new double[]{100,200})for(int count:new int[]{1,8,30}){
            var p=new Port();p.maximum=max;p.current=max;var d=new WeaponFireDecision(execution());
            var r=d.decideProportional(true,()->recipients(count,25),1,RpgCombatKernel.createProduction().resources(),p,()->fail());
            assertEquals(max*.0075*count,r.manaCost(),1e-10);assertEquals(max-r.manaCost(),p.current,1e-10);
            assertEquals(1,p.writes);assertEquals(count,d.claimPulse().size());assertTrue(d.claimPulse().isEmpty());
        }
    }
    @Test void zeroSourceNeverDividesQueriesOrSpends(){
        var e=execution(Delivery.NATIVE_MELEE,false,List.of(fire("fire",0,Provenance.WEAPON)),"zero");
        var d=new WeaponFireDecision(e);var p=new Port();
        var r=d.decideProportional(true,()->{fail("no target query");return List.of();},1,RpgCombatKernel.createProduction().resources(),p,()->fail());
        assertEquals(WeaponFireDecision.Status.PASS_THROUGH,r.status());assertEquals(0,p.writes);assertTrue(d.claimPulse().isEmpty());
    }
    @Test void budgetAndDuplicateRecipientRejectionPrecedeMutation(){
        for(var list:List.of(recipients(65,25),List.of(new WeaponFireDecision.Recipient(new UUID(0,1),25),new WeaponFireDecision.Recipient(new UUID(0,1),25)))){
            var d=new WeaponFireDecision(execution());var p=new Port();assertThrows(RuntimeException.class,()->d.decide(true,()->list,1,RpgCombatKernel.createProduction().resources(),p,()->fail()));assertEquals(0,p.writes);assertEquals(100,d.directAmount("fire",100));}
    }
    @Test void uncertainNativeWriterNeverRetriesOrSuppresses(){var d=new WeaponFireDecision(execution());var p=new Port();p.failAfterWrite=true;var resource=RpgCombatKernel.createProduction().resources();
        assertThrows(IllegalStateException.class,()->d.decide(true,()->recipients(1,25),1,resource,p,()->fail()));
        p.failAfterWrite=false;assertThrows(IllegalStateException.class,()->d.decide(true,()->recipients(1,25),1,resource,p,()->fail()));
        assertEquals(1,p.writes);assertEquals(100,d.directAmount("fire",100));assertThrows(IllegalStateException.class,d::claimPulse);
    }
    @Test void existingResourceHoldsAreRespected(){var p=new Port();var resource=RpgCombatKernel.createProduction().resources();var held=resource.reserveCost(ACTOR,new ResourceCost(ResourceType.MANA,99.9),p);
        var d=new WeaponFireDecision(execution());assertEquals(WeaponFireDecision.Status.INSUFFICIENT_MANA,d.decide(true,()->recipients(1,25),1,resource,p,()->{}).status());assertEquals(0,p.writes);resource.finish(held);}
    @Test void terminalCleanupCannotReopenADecision(){var d=new WeaponFireDecision(execution());d.close();assertThrows(IllegalStateException.class,()->d.decide(true,List::of,1,RpgCombatKernel.createProduction().resources(),new Port(),()->fail()));}
    @Test void existingRpgRootOwnsOneDecisionAcrossEveryVictim(){
        var root=new com.inigmasgames.hytalerpg.execution.RootEffectBudget(ACTOR,"root");
        var first=root.weaponExecution(execution());for(int i=0;i<64;i++)assertSame(first,root.weaponExecution(execution()));
        assertThrows(IllegalArgumentException.class,()->root.weaponExecution(execution(Delivery.RPG_WEAPON,false,List.of(fire("fire",90,Provenance.WEAPON)),"hit0")));
    }
    @Test void ledgerNeverEvictsAnExecutionToPermitReplay(){
        var ledger=new WeaponExecutionLedger(ACTOR,"root");var first=ledger.acquire(execution());
        for(int i=1;i<WeaponExecutionLedger.MAX_EXECUTIONS;i++)ledger.acquire(execution(Delivery.RPG_WEAPON,false,execution().components(),"hit"+i));
        assertThrows(IllegalStateException.class,()->ledger.acquire(execution(Delivery.RPG_WEAPON,false,execution().components(),"overflow")));
        assertSame(first,ledger.acquire(execution()));ledger.close();assertThrows(IllegalStateException.class,()->ledger.acquire(execution()));assertThrows(IllegalStateException.class,first::claimPulse);
    }
    @Test void worldActorAndRootCannotBeMixed(){
        for(var id:List.of(new Identity(UUID.randomUUID(),ACTOR,"root","execution","hit1"),new Identity(WORLD,UUID.randomUUID(),"root","execution","hit1"),new Identity(WORLD,ACTOR,"other","execution","hit1"))){
            var ledger=new WeaponExecutionLedger(ACTOR,"root");ledger.acquire(execution());
            assertThrows(IllegalArgumentException.class,()->ledger.acquire(new WeaponDamageExecution(id,"audited-item",Delivery.RPG_WEAPON,false,true,true,execution().components())));
        }
    }
}
