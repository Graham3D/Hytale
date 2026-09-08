package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.summon.*;
import com.hypixel.hytale.server.npc.blackboard.view.attitude.AttitudeView;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage10ConversionTest {
    static final ConversionRegistry.Eligibility COMMON=new ConversionRegistry.Eligibility(true,ConversionRegistry.Rank.COMMON,false,false,false,false,false);
    static class Harness extends Stage10SummonTest.Harness {
        final ConversionRegistry conversions=new ConversionRegistry();
        final UUID target=UUID.randomUUID(),originalThreat=UUID.randomUUID();
        ConversionRegistry.Eligibility eligibility=COMMON;
        ConversionRegistry.Lease lease;
        int dispatches;
        Harness(){super("dominate");}
        @Override public Validation familyPrerequisites(Stage04SkillProfile p,CompiledSkillPlan plan){
            conversions.expire(now);String code=eligibility.boundary();if(code.equals("PASS"))code=conversions.admission(actor,world,target);
            return code.equals("PASS")?Validation.pass():Validation.reject(code);
        }
        @Override public CommittedTarget captureTarget(Stage04SkillProfile p,CompiledSkillPlan plan,SkillExecutionRequest request){return new CommittedTarget(world,Vec3.ZERO,Vec3.FORWARD,Vec3.FORWARD,target);}
        @Override public SkillExecutionResult executeConversion(SkillExecutionContext c){context=c;lease=conversions.begin(c,eligibility,originalThreat,now);dispatches++;return SkillExecutionResult.committed("CONVERTED",0,0);}
    }
    @Test void realOrchestratorChargesOnceAndUsesConversionNotDamageOrSummon(){
        var h=new Harness();assertEquals(SkillExecutionResult.Status.COMMITTED,h.cast().status());assertEquals(70,h.mana);assertEquals(1,h.dispatches);assertEquals(0,h.summons.size());
        assertEquals(0,h.context.profile().damageCoefficient());assertEquals(35,h.context.profile().cooldownSeconds());assertEquals(12,h.lease.expires());assertEquals(15,h.context.profile().conversion().range());
        assertEquals(h.context.rootCastId(),h.lease.context().snapshot().rootCastId());assertEquals(h.originalThreat,h.lease.originalThreat());
    }
    @Test void ownerCapRejectsBeforeSecondCharge(){var h=new Harness();h.cast();h.kernel.cooldowns().clear(h.actor);assertFalse(h.cast().committed());assertEquals(70,h.mana);assertEquals(1,h.dispatches);}
    @Test void duplicateNativeInputCannotDispatchOrChargeTwice(){var h=new Harness();h.cast();assertFalse(h.cast().committed());assertEquals(1,h.dispatches);assertEquals(70,h.mana);}
    @Test void exclusiveTargetAcrossOwners(){var h=new Harness();h.cast();assertEquals("CONVERSION_TARGET_ALREADY_OWNED",h.conversions.admission(UUID.randomUUID(),h.world,h.target));}
    @Test void sameUuidDifferentWorldIsNotTheSameTarget(){var h=new Harness();h.cast();assertEquals("PASS",h.conversions.admission(UUID.randomUUID(),UUID.randomUUID(),h.target));}
    @Test void oneOwnerCannotConvertAcrossTwoWorlds(){var h=new Harness();h.cast();assertEquals("CONVERSION_OWNER_CAP",h.conversions.admission(h.actor,UUID.randomUUID(),UUID.randomUUID()));}
    @Test void endingReturnsExactOriginalIdentityOnlyOnce(){var h=new Harness();h.cast();assertEquals(h.lease,h.conversions.end(h.lease.token()).orElseThrow());assertTrue(h.conversions.end(h.lease.token()).isEmpty());assertEquals(0,h.conversions.size());}
    @Test void expiryPrunesPendingLeaseEvenIfNativeEntityNeverAppeared(){var h=new Harness();h.cast();h.conversions.expire(11.999);assertEquals(1,h.conversions.size());h.conversions.expire(12);assertEquals(0,h.conversions.size());}
    @Test void sourceOwnershipIsNeverTransferredToAnotherRoot(){var h=new Harness();h.cast();assertThrows(IllegalStateException.class,()->h.conversions.begin(Stage10SummonTest.copy(h.context,UUID.randomUUID(),"other"),COMMON,null,1));}
    @Test void rejectsUnknownUnlessExplicitlyDominatable(){assertEquals("CONVERSION_NOT_EXPLICITLY_DOMINATABLE",new ConversionRegistry.Eligibility(false,ConversionRegistry.Rank.COMMON,false,false,false,false,false).boundary());}
    @Test void commonAndSpecialistAreDeterministic(){for(var rank:List.of(ConversionRegistry.Rank.COMMON,ConversionRegistry.Rank.SPECIALIST))assertEquals("PASS",new ConversionRegistry.Eligibility(true,rank,false,false,false,false,false).boundary());}
    @Test void eliteRequiresIndependentAuthoredOptIn(){assertEquals("CONVERSION_RANK_FORBIDDEN",new ConversionRegistry.Eligibility(true,ConversionRegistry.Rank.ELITE,false,false,false,false,false).boundary());assertEquals("PASS",new ConversionRegistry.Eligibility(true,ConversionRegistry.Rank.ELITE,true,false,false,false,false).boundary());}
    @Test void bossAlwaysRejectedEvenWithEliteOptIn(){assertEquals("CONVERSION_RANK_FORBIDDEN",new ConversionRegistry.Eligibility(true,ConversionRegistry.Rank.BOSS,true,false,false,false,false).boundary());}
    @Test void protectedTargetRejectsBeforePayment(){var h=new Harness();h.eligibility=new ConversionRegistry.Eligibility(true,ConversionRegistry.Rank.COMMON,false,false,false,true,false);assertFalse(h.cast().committed());assertEquals(100,h.mana);assertEquals(0,h.conversions.size());}
    @Test void nativePersistentThreatCannotBeOverwritten(){assertEquals("CONVERSION_PERSISTENT_THREAT_FORBIDDEN",new ConversionRegistry.Eligibility(true,ConversionRegistry.Rank.COMMON,false,false,false,false,true).boundary());}
    @Test void playersAndAlliesAlwaysReject(){assertEquals("CONVERSION_PLAYER_FORBIDDEN",new ConversionRegistry.Eligibility(true,ConversionRegistry.Rank.COMMON,false,true,false,false,false).boundary());assertEquals("CONVERSION_ALLY_FORBIDDEN",new ConversionRegistry.Eligibility(true,ConversionRegistry.Rank.COMMON,false,false,true,false,false).boundary());}
    @Test void cannotConvertSelfOrNullIdentity(){var r=new ConversionRegistry();var owner=UUID.randomUUID();assertEquals("CONVERSION_INVALID_IDENTITY",r.admission(owner,UUID.randomUUID(),owner));assertEquals("CONVERSION_INVALID_IDENTITY",r.admission(owner,null,UUID.randomUUID()));}
    @Test void registryCannotReplayOrMintStateAfterRecreation(){var h=new Harness();h.cast();var restarted=new ConversionRegistry();assertEquals(0,restarted.size());assertTrue(restarted.find(h.lease.token()).isEmpty());}
    @Test void finiteClockIsMandatory(){var h=new Harness();h.cast();assertThrows(IllegalArgumentException.class,()->h.conversions.expire(Double.NaN));assertThrows(IllegalArgumentException.class,()->new ConversionProfile(15,Double.POSITIVE_INFINITY));}
    @Test void delayedReleaseKeepsOriginalTargetAndPaysOnce(){var h=new Harness();h.link("skill_delay",PassiveSlot.PASSIVE01);assertTrue(h.cast().committed());assertEquals(70,h.mana);assertEquals(0,h.dispatches);h.now=2;h.execution.tickScheduled(h.actor,h);assertEquals(1,h.dispatches);assertEquals(h.target,h.lease.entity());assertEquals(14,h.lease.expires());assertEquals(70,h.mana);}
    @Test void cancelledDelayCannotStealAnNpc(){var h=new Harness();h.link("skill_delay",PassiveSlot.PASSIVE01);h.cast();h.execution.cancel(h.actor,"LOGOUT");h.now=2;h.execution.tickScheduled(h.actor,h);assertEquals(0,h.dispatches);assertEquals(70,h.mana);}
    @Test void genericRepeatsAndSummonOnlyModifiersRejectAndRollback(){for(String id:List.of("echo","retaliation","critical_trigger","kill_trigger","swarm","minion_empowerment","death_pact")){
        var h=new Harness();assertTrue(h.bundle.service().equipPassive(h.actor,PassiveSlot.PASSIVE01,new PassiveId(id)).success());assertFalse(h.bundle.service().link(h.actor,LinkNodeId.PASSIVE01,LinkNodeId.SKILL01).success());assertTrue(h.cast().committed());assertEquals(1,h.dispatches);}}
    @Test void actualNativeProviderHonorsPriorityAndNullFallthroughWithoutRewritingBaseProvider(){
        var view=new AttitudeView(null);var active=new java.util.concurrent.atomic.AtomicBoolean(true);
        view.registerProvider(-20,(a,r,b,s)->active.get()?Attitude.FRIENDLY:null);
        view.registerProvider(-10,(a,r,b,s)->Attitude.HOSTILE);
        assertEquals(Attitude.FRIENDLY,view.getAttitude(null,0,null,null));active.set(false);
        assertEquals(Attitude.HOSTILE,view.getAttitude(null,0,null,null)); // Native class execution, NOT an NPC/client test.
    }
    @Test void independentActorLeaseCannotBeEndedByUnknownToken(){var h=new Harness();h.cast();assertTrue(h.conversions.end(UUID.randomUUID()).isEmpty());assertEquals(1,h.conversions.size());}
    @Test void uncertainNativeDispatchDoesNotRefundAnAlreadyStartedConversion(){
        var h=new Harness(){@Override public SkillExecutionResult executeConversion(SkillExecutionContext c){super.executeConversion(c);throw new IllegalStateException("fixture post-dispatch failure");}};
        assertEquals(SkillExecutionResult.Status.TERMINATED,h.cast().status());assertEquals(70,h.mana);assertEquals(1,h.conversions.size());
        assertFalse(h.kernel.cooldowns().canActivate(h.actor,"dominate"));
    }
    @Test void globalAdmissionIsBoundedAndExpiryReclaimsIt(){
        var h=new Harness();h.cast();
        for(int i=1;i<256;i++){
            var c=Stage10SummonTest.copy(h.context,UUID.randomUUID(),"global-"+i);
            c=new SkillExecutionContext(c.request(),c.rootCastId(),c.skillInstanceId(),c.profile(),c.compiledPlan(),c.snapshot(),c.equipment(),
                    new CommittedTarget(h.world,Vec3.ZERO,Vec3.ZERO,Vec3.FORWARD,UUID.randomUUID()),false);
            h.conversions.begin(c,COMMON,null,0);
        }
        assertEquals(256,h.conversions.size());assertEquals("CONVERSION_GLOBAL_CAP",h.conversions.admission(UUID.randomUUID(),h.world,UUID.randomUUID()));
        h.conversions.expire(12);assertEquals(0,h.conversions.size());
    }
    @Test void derivedReleaseCannotConvertAnotherActor(){
        var h=new Harness();h.cast();var c=h.context;h.conversions.end(h.lease.token());
        var derived=new SkillExecutionContext(c.request(),c.rootCastId(),c.skillInstanceId(),c.profile(),c.compiledPlan(),c.snapshot(),c.equipment(),c.target(),true);
        assertThrows(IllegalArgumentException.class,()->h.conversions.begin(derived,COMMON,null,1));assertEquals(0,h.conversions.size());
    }
}
