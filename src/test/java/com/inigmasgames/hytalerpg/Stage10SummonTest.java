package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.power.ItemPowerDescriptor;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.summon.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class Stage10SummonTest {
    static class Harness extends Stage09SupportRuntimeTest.Harness {
        final SummonRegistry summons=new SummonRegistry();
        List<SummonRegistry.Lease> leases=List.of();
        Harness(){this("wolf_summon");}
        Harness(String skill){super(skill);}
        private int inputs;
        @Override SkillExecutionResult cast(){return execution.request(new SkillExecutionRequest(actor,SkillSlot.SKILL01,"test",++inputs,UUID.randomUUID().toString(),Vec3.FORWARD),this);}
        @Override public Equipment equipment(){return new Equipment(new Item("fixture","SPELLBOOK",new ItemPowerDescriptor("fixture",Set.of("RPG_WEAPON_MAGIC"),20d,20d)),null);}
        @Override public Validation familyPrerequisites(Stage04SkillProfile p,CompiledSkillPlan plan){
            String result=summons.admission(actor,plan.summonModifiers().count(p.summon().count()));return result.equals("PASS")?Validation.pass():Validation.reject(result);
        }
        @Override public SkillExecutionResult executeSummon(SkillExecutionContext value){context=value;leases=summons.reserve(value,now);return SkillExecutionResult.committed("SUMMON_RESERVED",0,0);}
    }
    @Test void pilotUsesRealValidationCostCooldownAndSummonDispatch(){
        var h=new Harness();assertTrue(h.cast().committed());assertEquals(80,h.mana);
        assertFalse(h.kernel.cooldowns().canActivate(h.actor,"wolf_summon"));assertEquals(1,h.summons.size());
        assertEquals(h.context.rootCastId(),h.leases.getFirst().context().snapshot().rootCastId());
        assertEquals(.55,h.context.snapshot().skillCoefficient());
        assertEquals(60,h.context.snapshot().derivedStats().maxHealth()*.6,1e-9);
    }
    @Test void repeatCastDuringCooldownCannotMintAnotherSummon(){
        var h=new Harness();h.cast();assertFalse(h.cast().committed());assertEquals(80,h.mana);assertEquals(1,h.summons.size());
    }
    @Test void attackClaimedOnceBeforeDispatchAndNoCatchUpBurst(){
        var h=new Harness();h.cast();var lease=h.leases.getFirst();assertTrue(h.summons.activate(lease,UUID.randomUUID(),0));
        assertEquals(0,h.summons.claimAttack(lease.token(),.999));assertEquals(1,h.summons.claimAttack(lease.token(),1));
        assertEquals(0,h.summons.claimAttack(lease.token(),1));assertEquals(2,h.summons.claimAttack(lease.token(),15));
        assertEquals(0,h.summons.claimAttack(lease.token(),15));assertEquals(0,h.summons.claimAttack(lease.token(),20));
    }
    @Test void pendingReservationCountsAgainstOwnerCap(){
        var h=new Harness();h.cast();
        for(int i=1;i<8;i++)h.summons.reserve(copy(h.context,h.actor,"root"+i),0);
        assertEquals("SUMMON_OWNER_CAP",h.summons.admission(h.actor,1));
        assertEquals(8,h.summons.cancel(h.actor).size());assertEquals(0,h.summons.size());
    }
    @Test void globalCapIncludesPendingActorsAcrossWorlds(){
        var h=new Harness();h.cast();
        for(int i=1;i<256;i++)h.summons.reserve(copy(h.context,new UUID(0,i),"root"+i),0);
        assertEquals("SUMMON_GLOBAL_CAP",h.summons.admission(UUID.randomUUID(),1));
        assertThrows(IllegalStateException.class,()->h.summons.reserve(copy(h.context,UUID.randomUUID(),"overflow"),0));
        assertEquals(256,h.summons.size());
    }
    @Test void cancellationBeforeQueuedSpawnPreventsActivation(){
        var h=new Harness();h.cast();var lease=h.leases.getFirst();h.summons.cancel(h.actor);
        assertFalse(h.summons.activate(lease,UUID.randomUUID(),1));assertEquals(0,h.summons.size());
    }
    @Test void expireBeforeQueuedSpawnPreventsActivation(){
        var h=new Harness();h.cast();assertFalse(h.summons.activate(h.leases.getFirst(),UUID.randomUUID(),20));
    }
    @Test void activationAndRemovalAreIdempotent(){
        var h=new Harness();h.cast();var lease=h.leases.getFirst();var id=UUID.randomUUID();
        assertTrue(h.summons.activate(lease,id,0));assertFalse(h.summons.activate(lease,id,0));assertTrue(h.summons.owns(id));
        assertTrue(h.summons.remove(lease.token()).isPresent());assertTrue(h.summons.remove(lease.token()).isEmpty());
        assertFalse(h.summons.owns(id));assertEquals(0,h.summons.claimAttack(lease.token(),1));
    }
    @Test void duplicateRootCannotReserveEvenBeforeEntityAppears(){
        var h=new Harness();h.cast();assertThrows(IllegalStateException.class,()->h.summons.reserve(h.context,0));
    }
    @Test void noTwoReservationsCanOwnSameNativeActor(){
        var h=new Harness();h.cast();var first=h.leases.getFirst();var second=h.summons.reserve(copy(h.context,h.actor,"different"),0).getFirst();
        var id=UUID.randomUUID();assertTrue(h.summons.activate(first,id,0));assertFalse(h.summons.activate(second,id,0));
    }
    @Test void malformedProfileRejected(){
        for(double value:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->new SummonProfile("RPG_Summon_Wolf","NATURE",6,1,.6,.55,value,1,24));
        assertThrows(IllegalArgumentException.class,()->new SummonProfile("Wolf_Black","NATURE",6,1,.6,.55,20,1,24));
        assertThrows(IllegalArgumentException.class,()->new SummonProfile("RPG_Summon_Wolf","NATURE",6,9,.6,.55,20,1,24));
    }
    @Test void clockMustBeFinite(){var h=new Harness();h.cast();assertThrows(IllegalArgumentException.class,()->h.summons.claimAttack(h.leases.getFirst().token(),Double.NaN));}
    @Test void removalOfOneOwnerDoesNotFreeOthers(){
        var h=new Harness();h.cast();var other=UUID.randomUUID();h.summons.reserve(copy(h.context,other,"other"),0);
        h.summons.cancel(h.actor);assertEquals(1,h.summons.size());assertEquals(1,h.summons.cancel(other).size());
    }
    @Test void insufficientManaCannotReserveNativeActor(){var h=new Harness();h.mana=19;assertFalse(h.cast().committed());assertEquals(0,h.summons.size());assertEquals(19,h.mana);}
    @Test void echoRejectedAndGraphRolledBack(){
        var h=new Harness();assertTrue(h.bundle.service().equipPassive(h.actor,PassiveSlot.PASSIVE01,new PassiveId("echo")).success());
        assertFalse(h.bundle.service().link(h.actor,LinkNodeId.PASSIVE01,LinkNodeId.SKILL01).success());
        assertTrue(h.cast().committed());assertEquals(1,h.summons.size());
    }
    @Test void delayedSummonPaysOnceAndStartsLifetimeAtRelease(){
        var h=new Harness();h.link("skill_delay",PassiveSlot.PASSIVE01);assertTrue(h.cast().committed());
        assertEquals(80,h.mana);assertEquals(0,h.summons.size());
        h.now=2;h.execution.tickScheduled(h.actor,h);assertEquals(1,h.summons.size());assertEquals(22,h.leases.getFirst().expires());
        assertEquals(80,h.mana);assertEquals(1.35,h.context.snapshot().modifiers().factor(),1e-9);
    }
    @Test void cancelledDelayedSummonRetainsPaidCooldownAndCreatesNothing(){
        var h=new Harness();h.link("skill_delay",PassiveSlot.PASSIVE01);h.cast();h.execution.cancel(h.actor,"LOGOUT");
        h.now=2;h.execution.tickScheduled(h.actor,h);assertEquals(0,h.summons.size());assertEquals(80,h.mana);
        assertFalse(h.kernel.cooldowns().canActivate(h.actor,"wolf_summon"));
    }
    private static SkillExecutionContext copy(SkillExecutionContext original,UUID owner,String root){
        var request=new SkillExecutionRequest(owner,SkillSlot.SKILL01,"fixture",1,"test",Vec3.FORWARD);
        var old=original.snapshot();
        var snapshot=new com.inigmasgames.hytalerpg.combat.snapshot.CombatSnapshot(root,root,owner,old.rawAttributes(),old.effectiveAttributes(),
                old.derivedStats(),old.itemId(),old.weaponClass(),old.basePowerSource(),old.basePower(),old.compiledPlanHash(),old.skillCoefficient(),
                old.criticalChance(),old.criticalMultiplier(),old.modifiers(),old.resourceCost(),old.cooldownSeconds(),old.statusModifiers());
        return new SkillExecutionContext(request,root,root,original.profile(),original.compiledPlan(),snapshot,original.equipment(),original.target(),false);
    }
}
