package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets;
import com.inigmasgames.hytalerpg.combat.power.ItemPowerDescriptor;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.summon.*;
import com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects;
import com.inigmasgames.hytalerpg.links.CompatibilityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class Stage10ConsumptionTest {
    @TempDir Path directory;
    class Harness extends Stage09SupportRuntimeTest.Harness {
        final SummonRegistry summons=new SummonRegistry();final CorpseLedger corpses=new CorpseLedger(new FileCorpseConsumptionStore(directory));
        final CorpseLedger.Source corpse=Stage10CorpseTest.source(world,CorpseLedger.Rank.COMMON);
        final FiniteSupportEffects effects=new FiniteSupportEffects();final UUID entity=UUID.randomUUID();
        SummonRegistry.Lease lease;int burstCalls,inputs;boolean failBenefit,failCommit;
        Harness(String skill){super(skill);corpses.observe(corpse);
            var donor=new Stage10SummonTest.Harness();donor.link("death_pact",PassiveSlot.PASSIVE01);donor.cast();
            var copied=Stage10SummonTest.copy(donor.context,actor,"owned-root");
            var summonContext=new SkillExecutionContext(copied.request(),copied.rootCastId(),copied.skillInstanceId(),copied.profile(),copied.compiledPlan(),copied.snapshot(),copied.equipment(),new CommittedTarget(world,Vec3.ZERO,Vec3.ZERO,Vec3.FORWARD,null),false);
            lease=summons.reserve(summonContext,0).getFirst();assertTrue(summons.activate(lease,entity,0));
        }
        @Override SkillExecutionResult cast(){return execution.request(new SkillExecutionRequest(actor,SkillSlot.SKILL01,"fixture",++inputs,UUID.randomUUID().toString(),Vec3.FORWARD),this);}
        public Equipment equipment(){return new Equipment(new Item("fixture","SPELLBOOK",new ItemPowerDescriptor("fixture",Set.of("RPG_WEAPON_MAGIC"),20d,20d)),null);}
        public Validation familyPrerequisites(Stage04SkillProfile p,CompiledSkillPlan plan){boolean found=p.summonAction().kind()==SummonActionProfile.Kind.CORPSE_BURST?corpses.available(corpse.entity(),world).isPresent():summons.owned(actor,world,entity).isPresent();return found?Validation.pass():Validation.reject("NO_CONSUMABLE");}
        public CommittedTarget captureTarget(Stage04SkillProfile p,CompiledSkillPlan plan,SkillExecutionRequest request){return new CommittedTarget(world,Vec3.ZERO,Vec3.ZERO,Vec3.FORWARD,p.summonAction().kind()==SummonActionProfile.Kind.CORPSE_BURST?corpse.entity():entity);}
        public void commitConsumable(SkillExecutionContext c){
            if(c.profile().summonAction().kind()!=SummonActionProfile.Kind.CORPSE_BURST)return;
            if(failCommit)throw new IllegalStateException("fixture commit failure");
            var claim=corpses.reserve(corpse.entity(),world,actor,c.rootCastId());assertTrue(corpses.commit(claim,c.skillInstanceId()));
        }
        public Validation validateRelease(SkillExecutionContext c){return c.profile().summonAction().kind()==SummonActionProfile.Kind.CORPSE_BURST?
                (corpses.committed(actor,world,c.rootCastId(),c.skillInstanceId()).isPresent()?Validation.pass():Validation.reject("NO_COMMITTED_CORPSE")):
                (summons.owned(actor,world,entity).isPresent()?Validation.pass():Validation.reject("NO_OWNED_SUMMON"));}
        public void abandonRelease(SkillExecutionContext c){corpses.abandon(actor,c.skillInstanceId());}
        public SkillExecutionResult executeSummon(SkillExecutionContext c){context=c;
            if(c.profile().summonAction().kind()==SummonActionProfile.Kind.CONSUME_MINION){
                assertTrue(summons.consume(actor,world,entity,now,()->{if(failBenefit)throw new IllegalStateException("fixture publication failure");effects.consumeMinion(c,now);}).isPresent());
            }else{assertTrue(corpses.takeCommitted(actor,world,c.rootCastId(),c.skillInstanceId()).isPresent());burstCalls++;}
            return SkillExecutionResult.committed("CONSUMED",1,0);
        }
        double factor(){return effects.outgoingModifiers(world,actor,ModifierBuckets.NONE,now).factor();}
    }
    @Test void consumePaysOnceAndPublishesEightSecondBenefit(){var h=new Harness("consume_minion");assertEquals(SkillExecutionResult.Status.COMMITTED,h.cast().status());assertEquals(85,h.mana);assertFalse(h.kernel.cooldowns().canActivate(h.actor,"consume_minion"));assertEquals(0,h.summons.size());assertEquals(1.2,h.factor());var effect=h.effects.forTarget(h.world,h.actor,0).getFirst();assertEquals(10,effect.shieldRemaining());assertEquals(8,effect.ends());assertEquals(h.context.rootCastId(),effect.rootCastId());}
    @Test void depletedShieldDoesNotEraseRemainingDamageBonus(){var h=new Harness("consume_minion");h.cast();var hit=h.effects.shieldHit(h.world,h.actor,15,false,1,(e,a)->false);assertEquals(10,hit.absorbed());assertEquals(5,hit.remainder());h.now=2;assertEquals(1.2,h.factor());assertEquals(0,h.effects.forTarget(h.world,h.actor,2).getFirst().shieldRemaining());}
    @Test void bothBenefitsExpireAtEightNotEighteen(){var h=new Harness("consume_minion");h.cast();h.now=7.999;assertEquals(1.2,h.factor());h.now=8;assertEquals(1,h.factor());assertEquals(0,h.effects.forTarget(h.world,h.actor,8).size());}
    @Test void consumeBonusDoesNotModifyNativeBasicAttackFactor(){var h=new Harness("consume_minion");h.cast();assertEquals(1,h.effects.nativeOutgoingFactor(h.world,h.actor,1));assertEquals(1.2,h.factor());}
    @Test void consumeCannotUseSameOwnedActorTwice(){var h=new Harness("consume_minion");h.cast();h.kernel.cooldowns().clear(h.actor,"consume_minion");assertFalse(h.cast().committed());assertEquals(85,h.mana);assertEquals(1,h.effects.size());}
    @Test void voluntaryConsumeCannotTriggerDeathPact(){var h=new Harness("consume_minion");assertTrue(h.lease.context().compiledPlan().summonModifiers().deathPact());h.cast();assertTrue(h.summons.end(h.lease.token(),SummonRegistry.EndReason.ENEMY_KILL).isEmpty());}
    @Test void benefitFailureLeavesOwnedActorButRetainsPaidActivation(){var h=new Harness("consume_minion");h.failBenefit=true;assertEquals(SkillExecutionResult.Status.TERMINATED,h.cast().status());assertEquals(1,h.summons.size());assertEquals(0,h.effects.size());assertEquals(85,h.mana);assertFalse(h.kernel.cooldowns().canActivate(h.actor,"consume_minion"));}
    @Test void wrongOwnerCannotConsumeOrPublishBenefit(){var h=new Harness("consume_minion");assertTrue(h.summons.consume(UUID.randomUUID(),h.world,h.entity,0,()->fail("benefit ran")).isEmpty());assertEquals(1,h.summons.size());}
    @Test void wrongWorldCannotConsumeOrPublishBenefit(){var h=new Harness("consume_minion");assertTrue(h.summons.consume(h.actor,UUID.randomUUID(),h.entity,0,()->fail("benefit ran")).isEmpty());}
    @Test void expiredOwnedActorCannotBeConsumed(){var h=new Harness("consume_minion");assertTrue(h.summons.consume(h.actor,h.world,h.entity,20,()->fail("benefit ran")).isEmpty());}
    @Test void corpseBurstUsesRealCastCostAndCapturedCoefficient(){var h=new Harness("corpse_burst");assertEquals(SkillExecutionResult.Status.COMMITTED,h.cast().status());assertEquals(82,h.mana);assertEquals(1,h.burstCalls);assertEquals(1.6,h.context.snapshot().skillCoefficient());assertEquals(h.corpse.entity(),h.context.target().entityId());assertFalse(h.kernel.cooldowns().canActivate(h.actor,"corpse_burst"));}
    @Test void duplicateCorpseDoesNotChargeOrDispatchAgain(){var h=new Harness("corpse_burst");h.cast();h.kernel.cooldowns().clear(h.actor,"corpse_burst");assertFalse(h.cast().committed());assertEquals(82,h.mana);assertEquals(1,h.burstCalls);}
    @Test void burstConsumptionAlsoRejectsFutureReviveClaim(){var h=new Harness("corpse_burst");h.cast();assertThrows(IllegalStateException.class,()->h.corpses.reserve(h.corpse.entity(),h.world,h.actor,"revive-root"));assertFalse(new CorpseLedger(new FileCorpseConsumptionStore(directory)).observe(h.corpse));}
    @Test void noCorpseRejectsBeforeCostAndCooldown(){var h=new Harness("corpse_burst");h.corpses.remove(h.corpse.entity());assertFalse(h.cast().committed());assertEquals(100,h.mana);assertEquals(0,h.burstCalls);assertTrue(h.kernel.cooldowns().canActivate(h.actor,"corpse_burst"));}
    @Test void genericRepeatAndTriggerConsumersAreRejected(){var catalog=Stage01BTestSupport.bundle().catalog();var compatibility=new CompatibilityService();for(var skill:List.of("corpse_burst","revive_fallen","consume_minion"))for(var passive:List.of("echo","retaliation","critical_trigger","kill_trigger"))assertFalse(compatibility.assess(catalog.skill(new SkillId(skill)).orElseThrow(),catalog.passive(new PassiveId(passive)).orElseThrow()).accepted(),skill+"/"+passive);}
    @Test void sacrificeIsNotAStaticSummonPassiveTarget(){var catalog=Stage01BTestSupport.bundle().catalog();for(var passive:List.of("swarm","minion_empowerment","death_pact"))assertFalse(new CompatibilityService().assess(catalog.skill(new SkillId("consume_minion")).orElseThrow(),catalog.passive(new PassiveId(passive)).orElseThrow()).accepted());}
    @Test void deathPactOnlyNaturalExpiryOrEnemyKill(){for(var reason:SummonRegistry.EndReason.values()){var h=new Stage10SummonTest.Harness();h.link("death_pact",PassiveSlot.PASSIVE01);h.cast();var lease=h.leases.getFirst();h.summons.activate(lease,UUID.randomUUID(),0);var end=h.summons.end(lease.token(),reason).orElseThrow();assertEquals(reason==SummonRegistry.EndReason.NATURAL_EXPIRY||reason==SummonRegistry.EndReason.ENEMY_KILL,end.deathPact(),reason.name());assertTrue(h.summons.end(lease.token(),reason).isEmpty());}}
    @Test void pendingActorNeverExplodes(){var h=new Stage10SummonTest.Harness();h.link("death_pact",PassiveSlot.PASSIVE01);h.cast();assertFalse(h.summons.end(h.leases.getFirst().token(),SummonRegistry.EndReason.NATURAL_EXPIRY).orElseThrow().deathPact());}
    @Test void ownerCleanupCannotLeaveDeathPactTrigger(){var h=new Stage10SummonTest.Harness();h.link("death_pact",PassiveSlot.PASSIVE01);h.cast();var lease=h.leases.getFirst();h.summons.activate(lease,UUID.randomUUID(),0);h.summons.cancel(h.actor);assertTrue(h.summons.end(lease.token(),SummonRegistry.EndReason.ENEMY_KILL).isEmpty());}
    @Test void unlinkedSummonDoesNotExplode(){var h=new Stage10SummonTest.Harness();h.cast();var lease=h.leases.getFirst();h.summons.activate(lease,UUID.randomUUID(),0);assertFalse(h.summons.end(lease.token(),SummonRegistry.EndReason.ENEMY_KILL).orElseThrow().deathPact());}
    @Test void allSwarmMembersHaveIndependentSingleTerminalClaim(){var h=new Stage10SummonTest.Harness("brood_call");h.link("swarm",PassiveSlot.PASSIVE01);h.link("death_pact",PassiveSlot.PASSIVE02);h.cast();int bursts=0;for(var lease:h.leases){h.summons.activate(lease,UUID.randomUUID(),0);if(h.summons.end(lease.token(),SummonRegistry.EndReason.NATURAL_EXPIRY).orElseThrow().deathPact())bursts++;}assertEquals(5,bursts);assertEquals(0,h.summons.size());}
    @Test void summonOutgoingBonusCapturedOnce(){double[] bonus={.2};var h=new Stage10SummonTest.Harness(){@Override public ModifierBuckets captureSummonModifiers(ModifierBuckets authored){return new ModifierBuckets(List.of(bonus[0]),authored.reduced(),authored.more(),authored.less());}};h.link("potency",PassiveSlot.PASSIVE01);h.cast();bonus[0]=0;assertEquals(1.35,h.leases.getFirst().context().snapshot().modifiers().factor(),1e-9);}
    @Test void logoutAndWorldCleanupRemoveConsumeBenefits(){var h=new Harness("consume_minion");h.cast();h.effects.forget(h.actor);assertEquals(1,h.factor());h.effects.consumeMinion(h.context,0);h.effects.clearWorld(h.world);assertEquals(0,h.effects.size());}
    @Test void expandedRadiusTargetsOnlyBurstRadius(){var h=new Harness("corpse_burst");h.link("expanded_radius",PassiveSlot.PASSIVE01);h.cast();assertEquals(5,h.profile.summonAction().radius()*h.context.compiledPlan().executionModifiers().radiusFactor());assertEquals(.9,h.context.snapshot().modifiers().factor(),1e-9);assertEquals(1,h.burstCalls);}
    @Test void delayedBurstConsumesAtPaidCommitNotAtRelease(){var h=new Harness("corpse_burst");h.link("skill_delay",PassiveSlot.PASSIVE01);assertEquals(SkillExecutionResult.Status.COMMITTED,h.cast().status());assertEquals(82,h.mana);assertTrue(h.corpses.available(h.corpse.entity(),h.world).isEmpty());assertEquals(1,h.corpses.pendingReleases());assertEquals(0,h.burstCalls);h.corpses.remove(h.corpse.entity());h.now=2;h.execution.tickScheduled(h.actor,h);assertEquals(1,h.burstCalls);assertEquals(0,h.corpses.pendingReleases());assertEquals(82,h.mana);}
    @Test void abandonedDelayedBurstDoesNotRestoreCorpse(){var h=new Harness("corpse_burst");h.link("skill_delay",PassiveSlot.PASSIVE01);h.cast();h.corpses.cancelUncommitted(h.actor);h.now=2;h.execution.tickScheduled(h.actor,h);assertEquals(0,h.burstCalls);assertEquals(0,h.corpses.pendingReleases());assertFalse(new CorpseLedger(new FileCorpseConsumptionStore(directory)).observe(h.corpse));assertEquals(82,h.mana);}
    @Test void familyCommitFailureCannotArmDelayedDamageOrRefundPaidCost(){var h=new Harness("corpse_burst");h.link("skill_delay",PassiveSlot.PASSIVE01);h.failCommit=true;assertEquals(SkillExecutionResult.Status.TERMINATED,h.cast().status());h.now=2;h.execution.tickScheduled(h.actor,h);assertEquals(0,h.burstCalls);assertEquals(82,h.mana);assertFalse(h.kernel.cooldowns().canActivate(h.actor,"corpse_burst"));}
    @Test void delayedConsumeLostActorNeverPublishesBenefit(){var h=new Harness("consume_minion");h.link("skill_delay",PassiveSlot.PASSIVE01);h.cast();h.summons.cancel(h.actor);h.now=2;h.execution.tickScheduled(h.actor,h);assertEquals(0,h.effects.size());assertEquals(85,h.mana);}
}
