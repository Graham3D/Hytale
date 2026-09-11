package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.cooldown.RpgCooldownService;
import com.inigmasgames.hytalerpg.combat.resource.*;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.support.*;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Backend and pinned native object tests, never connected Aura/input/animation evidence. */
class Stage09AuraRuntimeTest {
    @Test void allSixteenSupportProfilesArePresent(){
        var h=new Harness("thorns_aura");assertEquals(17,h.profiles.all().values().stream().filter(p->p.support()!=null).count());
        for(var id:List.of("thorns_aura","chilling_aura","pedanticism","reaping_storm"))assertTrue(h.profiles.require(id).support().aura());
    }
    @Test void thornsPaysFractionalFirstSliceBeforeGrantingReflection(){
        var h=new Harness("thorns_aura");assertTrue(h.cast().committed());assertEquals(81.5,h.mana);assertEquals(0,h.reserved);
        assertEquals(.2,h.runtime.thorns(h.world,h.actor,.1).orElseThrow().magnitude());assertEquals(List.of(.25),h.slices);
    }
    @Test void insufficientFirstUpkeepRejectsBeforeUpfrontCost(){
        var h=new Harness("thorns_aura");h.mana=18.25;assertEquals("AURA_INITIAL_UPKEEP_UNAFFORDABLE",h.cast().code());
        assertEquals(18.25,h.mana);assertEquals(0,h.runtime.auraCount());assertEquals(0,h.kernel.cooldowns().remaining(h.actor,"thorns_aura"));
    }
    @Test void thornsDoesNotReflectBeyondPaidSliceWithoutOwnerTick(){
        var h=new Harness("thorns_aura");h.cast();h.tick(.1);assertTrue(h.runtime.thorns(h.world,h.actor,.26).isEmpty());
    }
    @Test void thornsRecipientLeavingIsRemovedAtMembershipTick(){
        var h=new Harness("thorns_aura");var ally=UUID.randomUUID();h.members.add(ally);h.cast();assertTrue(h.runtime.thorns(h.world,ally,.1).isPresent());
        h.members.remove(ally);h.tick(.1);assertTrue(h.runtime.thorns(h.world,ally,.1).isEmpty());
    }
    @Test void thornsHasNoInitialAttackAndReflectionEpochBudgetRenews(){
        var h=new Harness("thorns_aura");h.cast();assertEquals(0,h.damagePulses.size());
        for(int i=0;i<8;i++)assertTrue(h.runtime.claimAuraSecondary(h.context,.1));assertFalse(h.runtime.claimAuraSecondary(h.context,.1));
        h.advance(1);assertTrue(h.runtime.claimAuraSecondary(h.context,1));
    }
    @Test void chillingDamageAndChillUseIndependentAuthoredClocks(){
        var h=new Harness("chilling_aura");h.cast();h.advance(.9);assertTrue(h.damagePulses.isEmpty());assertTrue(h.chillPulses.isEmpty());
        h.advance(1);assertEquals(List.of(1),h.damagePulses);assertTrue(h.chillPulses.isEmpty());
        h.advance(1.5);assertEquals(List.of(1),h.chillPulses);h.advance(3);assertEquals(List.of(1,2,3),h.damagePulses);assertEquals(List.of(1,2),h.chillPulses);
    }
    @Test void reapingPaysExactlyEightSecondsAndProducesEightPulses(){
        var h=new Harness("reaping_storm");h.cast();h.advance(8.2);
        assertEquals(List.of(1,2,3,4,5,6,7,8),h.damagePulses);assertEquals(32,h.slices.size());
        assertEquals(8,h.slices.stream().mapToDouble(Double::doubleValue).sum());assertEquals(28,h.mana,1e-8);assertEquals(0,h.runtime.auraCount());assertEquals(0,h.budget.size());
    }
    @Test void lastPaidPulseOccursBeforeRejectingNextSlice(){
        var h=new Harness("chilling_aura");h.mana=23;h.cast();h.advance(1);
        assertEquals(List.of(1),h.damagePulses);assertEquals(0,h.mana,1e-8);assertEquals(0,h.runtime.auraCount());assertEquals(4,h.slices.size());
    }
    @Test void duplicateOwnerTicksDoNotChargeOrPulseTwice(){
        var h=new Harness("reaping_storm");h.cast();h.advance(1);double paid=h.mana;h.tick(1);h.tick(1);
        assertEquals(paid,h.mana);assertEquals(List.of(1),h.damagePulses);
    }
    @Test void longUnobservedUpkeepGapTerminatesWithoutBackfilledHits(){
        var h=new Harness("reaping_storm");h.cast();h.tick(20);assertEquals(0,h.runtime.auraCount());assertTrue(h.damagePulses.isEmpty());assertEquals(59,h.mana);
    }
    @Test void toggleOffAtZeroManaIsNotASecondActivationPayment(){
        var h=new Harness("thorns_aura");h.cast();h.advance(2);h.mana=0;assertEquals("AURA_OFF",h.cast().code());
        assertEquals(0,h.mana);assertEquals(0,h.runtime.auraCount());assertTrue(h.runtime.thorns(h.world,h.actor,2).isEmpty());
    }
    @Test void earlyToggleRejectsWithoutNewPayment(){
        var h=new Harness("thorns_aura");h.cast();double mana=h.mana;assertEquals("AURA_TOGGLE_LOCK",h.cast().code());assertEquals(mana,h.mana);
    }
    @Test void finiteAuraManualStopDoesNotResetThirtyFiveSecondCooldown(){
        var h=new Harness("reaping_storm");h.cast();double cd=h.kernel.cooldowns().remaining(h.actor,"reaping_storm");
        assertEquals("AURA_OFF",h.cast().code());assertTrue(h.kernel.cooldowns().remaining(h.actor,"reaping_storm")>cd-.1);assertEquals(59,h.mana);
    }
    @Test void upkeepNativeExceptionCleansEmitterAndDoesNotRefundUpfront(){
        var h=new Harness("thorns_aura");h.failUpkeep=true;h.cast();assertEquals(0,h.runtime.auraCount());assertEquals(0,h.budget.size());assertEquals(82,h.mana);
    }
    @Test void hostileMembershipOverflowEndsWholeAuraWithoutPartialPulse(){
        var h=new Harness("reaping_storm");h.cast();for(int i=0;i<64;i++)h.hostiles.add(UUID.randomUUID());h.tick(.1);
        assertEquals(0,h.runtime.auraCount());assertTrue(h.damagePulses.isEmpty());assertEquals(59,h.mana);
    }
    @Test void ownerDeathWorldChangeAndCancelClearPaidAuras(){
        for(var reason:List.of("OWNER_DEAD","WORLD_CHANGED","LOGOUT")){
            var h=new Harness("thorns_aura");h.cast();h.runtime.cancel(h.actor,reason,h);assertEquals(0,h.budget.size());assertEquals(81.5,h.mana);
        }
    }
    @Test void pedanticismReservesTwentyPercentWithoutCreatingUpkeepTimer(){
        var h=new Harness("pedanticism");h.cast();assertEquals(80,h.mana);assertEquals(20,h.reserved);assertTrue(h.slices.isEmpty());
        assertEquals(.15,h.runtime.cooldownRecoveryIncreased(h.actor,0));h.members.clear();h.tick(.1);assertEquals(0,h.runtime.cooldownRecoveryIncreased(h.actor,.1));
    }
    @Test void cooldownRateChangePreservesElapsedWork(){
        long[] time={0};var cd=cooldowns(time);var actor=UUID.randomUUID();cd.startCooldown(actor,"skill",10,1,0,CompiledSkillPlan.KernelModifiers.NONE);
        time[0]=2_000_000_000L;cd.setAuraRate(actor,.15,1,1);assertEquals(8/1.15,cd.remaining(actor,"skill"),1e-8);
        time[0]=3_000_000_000L;cd.setAuraRate(actor,0,1,1);assertEquals(6.85,cd.remaining(actor,"skill"),1e-8);
    }
    @Test void expiredCooldownAuraLeaseDoesNotKeepAcceleratingOffline(){
        long[] time={0};var cd=cooldowns(time);var actor=UUID.randomUUID();cd.setAuraRate(actor,.15,1,.25);
        cd.startCooldown(actor,"skill",10,1,0,CompiledSkillPlan.KernelModifiers.NONE);time[0]=2_000_000_000L;
        assertEquals(10-.25*1.15-1.75,cd.remaining(actor,"skill"),1e-8);
    }
    @Test void genericExplicitEnemyCooldownPenaltyMultipliesDurationNotAttackTiming(){
        long[] time={0};var cd=cooldowns(time);var actor=UUID.randomUUID();cd.startCooldown(actor,"explicit-skill",10,1,0,CompiledSkillPlan.KernelModifiers.NONE);
        time[0]=2_000_000_000L;cd.setAuraRate(actor,0,1.15,1);assertEquals(8*1.15,cd.remaining(actor,"explicit-skill"),1e-8);
        time[0]=3_000_000_000L;cd.setAuraRate(actor,0,1,1);assertEquals(8-1/1.15,cd.remaining(actor,"explicit-skill"),1e-8);
    }
    @Test void hasteCapAndExistingBaseFormulaRemainAuthoritative(){
        long[] time={0};var cd=cooldowns(time);var actor=UUID.randomUUID();cd.setAuraRate(actor,.15,1,.25);
        var result=cd.startCooldown(actor,"skill",10,1,.7,CompiledSkillPlan.KernelModifiers.NONE);
        assertEquals(.75,result.appliedRecovery());assertEquals(10/1.75,result.finalSeconds(),1e-8);
    }
    @Test void nativeCooldownGetterReturnsMaximumNotRemainingProgress(){
        var nativeCd=new CooldownHandler.Cooldown(10,new float[]{10},false);nativeCd.deductCharge();nativeCd.tick(2);
        assertEquals(10,nativeCd.getCooldown());assertTrue(nativeCd.hasCooldown(false));
        var methods=Arrays.stream(CooldownHandler.Cooldown.class.getMethods()).map(java.lang.reflect.Method::getName).toList();
        assertFalse(methods.contains("getRemainingCooldown"));assertFalse(methods.contains("getChargeTimer"));
    }
    private static RpgCooldownService cooldowns(long[] now){return new RpgCooldownService(CombatBalanceProfile.loadCanonical(),()->now[0]);}
    static class Harness extends Stage09SupportRuntimeTest.Harness {
        final List<UUID> hostiles=new ArrayList<>(List.of(UUID.randomUUID()));
        final List<Integer> damagePulses=new ArrayList<>(),chillPulses=new ArrayList<>();final List<Double> slices=new ArrayList<>();boolean failUpkeep;
        Harness(String skill){super(skill);}
        @Override public SkillExecutionResult stopActiveSupport(Stage04SkillProfile p){return runtime.stopActive(actor,p.skillId(),this);}
        @Override public List<UUID> enemies(SkillExecutionContext c,double radius){return List.copyOf(hostiles);}
        @Override public boolean upkeep(SkillExecutionContext c,double seconds,int quantum){
            if(failUpkeep)throw new IllegalStateException("native failure");
            var cost=kernel.resources().evaluateUpkeep(new ResourceCost(ResourceType.MANA,c.profile().support().upkeepPerSecond()*seconds*c.compiledPlan().supportModifiers().commitmentFactor()),c.compiledPlan().kernelModifiers());
            if(!kernel.resources().canAfford(actor,cost,this))return false;var token=kernel.resources().reserveCost(actor,cost,this);
            try{kernel.resources().commitCost(token,this);slices.add(seconds);return true;}finally{kernel.resources().finish(token);}
        }
        @Override public void auraPulse(SkillExecutionContext c,List<UUID> targets,int tick,boolean chill){assertEquals(hostiles.size(),targets.size());(chill?chillPulses:damagePulses).add(tick);}
    }
}
