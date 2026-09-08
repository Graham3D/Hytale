package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.support.*;
import com.inigmasgames.hytalerpg.links.CompatibilityService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Deterministic component contracts; not native/client casting, Health or visual evidence. */
class Stage09SupportPassivesTest {
    @Test void canonicalSupportCompatibilityHasExplicitPositiveAndNegativeCases(){
        for(String[] pair:new String[][]{{"emanatism","selflessness"},{"thorns_aura","selflessness"},{"pedanticism","selflessness"},
                {"managuard","conservation"},{"reaping_storm","conservation"},{"pedanticism","resonance"},
                {"minor_heal","overflow"},{"life_drain","overflow"}})assertTrue(compatible(pair[0],pair[1]),Arrays.toString(pair));
        for(String[] pair:new String[][]{{"managuard","selflessness"},{"chilling_aura","selflessness"},{"reaping_storm","selflessness"},
                {"quick_slash","conservation"},{"managuard","resonance"},{"spirit_shield","overflow"},
                {"emanatism","overflow"},{"minor_heal","fork"}})assertFalse(compatible(pair[0],pair[1]),Arrays.toString(pair));
    }
    @Test void selflessnessRemovesCasterAndStrengthensOnlyAllies(){
        var h=aura("emanatism","selflessness");var ally=UUID.randomUUID();h.members.add(ally);assertTrue(h.cast().committed());
        assertEquals(0,h.bonus(h.actor));assertEquals(.25*1.35,h.bonus(ally),1e-9);assertEquals(90,h.mana);
        h.members.remove(ally);h.tick(.1);assertEquals(0,h.bonus(ally));
    }
    @Test void selflessnessThornsDoesNotKeepOwnerReflection(){
        var h=aura("thorns_aura","selflessness");var ally=UUID.randomUUID();h.members.add(ally);h.cast();
        assertTrue(h.runtime.thorns(h.world,h.actor,0).isEmpty());assertEquals(.27,h.runtime.thorns(h.world,ally,0).orElseThrow().magnitude(),1e-9);
    }
    @Test void conservationUsesActualReservedManaForManaguardCapacity(){
        var h=aura("managuard","conservation");h.cast();assertEquals(60,h.mana);assertEquals(40,h.reserved);
        assertEquals(14,h.hit(50),1e-9);assertEquals(36,h.saved.managuard().deficit());
    }
    @Test void conservationResizePreservesDeficitAndDebitsOnlyAdditionalActualReservation(){
        var h=aura("managuard","conservation");h.cast();h.hit(10);h.advance(3);h.runtime.allocateManaguard(h.actor,25,h);
        assertEquals(20,h.reserved);assertEquals(60,h.mana);assertEquals(10,h.saved.managuard().deficit());
        assertEquals(2,h.hit(10),1e-9);
    }
    @Test void conservationReducesPassiveRegenAndReservationNotNativeBaseline(){
        var h=aura("emanatism","conservation");h.cast();assertEquals(92,h.mana);assertEquals(.225,h.bonus(h.actor),1e-9);
    }
    @Test void upkeepModifiersDoNotDiscountTheOneTimeActivationCost(){
        var h=aura("thorns_aura","conservation");h.cast();assertEquals(100-18-.4,h.mana,1e-9);
        assertEquals(.18,h.runtime.thorns(h.world,h.actor,0).orElseThrow().magnitude(),1e-9);h.advance(1);assertEquals(80,h.mana,1e-9);
    }
    @Test void resonanceChangesRadiusAndReservationBeforeAffordability(){
        var h=aura("pedanticism","resonance");h.mana=22;assertFalse(h.cast().committed());assertEquals(22,h.mana);assertEquals(0,h.budget.size());
        var enough=aura("pedanticism","resonance");enough.cast();assertEquals(23,enough.reserved,1e-9);assertEquals(11.2,enough.lastRadius,1e-9);
    }
    @Test void resonanceFirstSliceIsIncludedInPreflight(){
        var h=aura("thorns_aura","resonance");h.mana=18.55;assertEquals("AURA_INITIAL_UPKEEP_UNAFFORDABLE",h.cast().code());assertEquals(18.55,h.mana);
    }
    @Test void combinedAuraPassivesComposeOnceAndKeepResourceCostKernelUnchanged(){
        var h=aura("emanatism","conservation","resonance","selflessness");var ally=UUID.randomUUID();h.members.add(ally);h.cast();
        assertEquals(9.2,h.reserved,1e-9);assertEquals(11.2,h.lastRadius,1e-9);assertEquals(.25*.9*1.35,h.bonus(ally),1e-9);
        assertEquals(CompiledSkillPlan.KernelModifiers.NONE,h.context.compiledPlan().kernelModifiers());
    }
    @Test void pedanticismSelflessnessAndConservationAffectBeneficialFieldOnly(){
        var h=aura("pedanticism","selflessness","conservation");var ally=UUID.randomUUID();h.members.add(ally);h.cast();
        assertEquals(0,h.runtime.cooldownRecoveryIncreased(h.actor,0));assertEquals(.15*.9*1.35,h.runtime.cooldownRecoveryIncreased(ally,0),1e-9);
        assertEquals(.15,h.context.profile().support().coefficient()); // Hostile native branch remains separately capability-gated.
    }
    @Test void noOverhealCreatesNoBarrier(){var h=heal(40);assertEquals(60.6,h.health,1e-9);assertEquals(0,h.runtime.finite().size());}
    @Test void finalOverhealBecomesOnlyTheExcess(){var h=heal(90);assertEquals(100,h.health);assertEquals(10.6,barrier(h).shieldRemaining(),1e-9);}
    @Test void fullHealthOverhealIsCappedAndCannotEarnActualHealing(){var h=heal(100);assertEquals(100,h.health);assertEquals(20,barrier(h).shieldRemaining());assertEquals(88,h.mana);}
    @Test void potencyIsAppliedToHealingOnceNotAgainToOverflow(){
        var h=aura("minor_heal","overflow","potency");h.health=95;h.cast();assertEquals(20.6*1.15-5,barrier(h).shieldRemaining(),1e-9);
    }
    @Test void strongerRemainingBarrierRefreshesRatherThanAdds(){
        var h=heal(100);var effects=h.runtime.finite();effects.shieldHit(h.world,h.actor,4,false,0,(e,a)->false);
        var e=effects.healingResolved(h.context,h.actor,5,100,100,100,2).orElseThrow();assertEquals(16,e.shieldRemaining());assertEquals(8,e.ends());assertEquals(1,effects.size());
    }
    @Test void capIsReevaluatedAgainstCurrentMaximumOnRefresh(){
        var h=heal(100);var e=h.runtime.finite().healingResolved(h.context,h.actor,5,50,50,50,2).orElseThrow();assertEquals(10,e.shieldRemaining());
    }
    @Test void overflowCannotRedirectTwentyPercentLikeSpiritShield(){
        var h=heal(100);var recipient=UUID.randomUUID();h.runtime.finite().healingResolved(h.context,recipient,30,100,100,100,0);
        var hit=h.runtime.finite().shieldHit(h.world,recipient,30,true,1,(e,a)->{throw new AssertionError("Overflow must not redirect");});
        assertEquals(20,hit.absorbed());assertEquals(10,hit.remainder());assertEquals(0,hit.redirected());
    }
    @Test void staleBarrierExpiresAndNeverAbsorbsAfterSixSeconds(){
        var h=heal(100);assertTrue(h.runtime.finite().forTarget(h.world,h.actor,6).isEmpty());
        assertEquals(10,h.runtime.finite().shieldHit(h.world,h.actor,10,false,6,(e,a)->false).remainder());
    }
    @Test void failedOrMalformedHealthWriteIsNotOverheal(){
        var h=heal(40);var e=h.runtime.finite();assertThrows(IllegalStateException.class,()->e.healingResolved(h.context,h.actor,20,90,90,100,1));
        assertThrows(IllegalArgumentException.class,()->e.healingResolved(h.context,h.actor,Double.NaN,90,100,100,1));assertEquals(0,e.size());
    }
    @Test void crossSourceOverhealStillHasOneRecipientCapAndKeepsStrongestAttribution(){
        var first=heal(100);var other=heal(40);var c=other.context;
        var context=new SkillExecutionContext(c.request(),c.rootCastId(),c.skillInstanceId(),c.profile(),c.compiledPlan(),c.snapshot(),c.equipment(),
                new CommittedTarget(first.world,c.target().origin(),c.target().point(),c.target().direction(),first.actor),false);
        var e=first.runtime.finite().healingResolved(context,first.actor,10,100,100,100,1).orElseThrow();
        assertEquals(20,e.shieldRemaining());assertEquals(first.actor,e.key().owner());assertEquals(1,first.runtime.finite().size());
    }
    @Test void ownerRecipientAndWorldTeardownClearOverflow(){
        for(String mode:List.of("OWNER","TARGET","WORLD")){
            var h=heal(100);if(mode.equals("WORLD"))h.runtime.finite().clearWorld(h.world);else h.runtime.finite().forget(h.actor);assertEquals(0,h.runtime.finite().size());
        }
    }
    @Test void typedOperatorsAreDeterministicAndCompiledSchemaIsBumped(){
        var a=SupportModifiers.from(List.of(new PassiveId("conservation"),new PassiveId("resonance")));
        var b=SupportModifiers.from(List.of(new PassiveId("resonance"),new PassiveId("conservation")));
        assertEquals(a,b);assertEquals(.92,a.commitmentFactor(),1e-9);assertEquals(27,CompiledSkillPlan.CURRENT_SCHEMA);
    }
    @Test void overflowRegistryAcceptsHealingComponentsOutsideSupportFamily(){
        var h=heal(40);var c=h.context;
        var drain=new SkillExecutionContext(c.request(),c.rootCastId(),c.skillInstanceId(),h.profiles.require("life_drain"),c.compiledPlan(),c.snapshot(),c.equipment(),c.target(),false);
        assertNull(drain.profile().support());
        var e=h.runtime.finite().healingResolved(drain,h.actor,12,100,100,100,1).orElseThrow();
        assertEquals(12,e.shieldRemaining());assertEquals("life_drain",e.key().skill());assertTrue(FiniteSupportEffects.isShield(e));
    }
    @Test void overflowSharesTheExistingFiniteOwnerBudgetAndFailureIsAtomic(){
        var h=heal(40);var effects=h.runtime.finite();
        for(int i=0;i<FiniteSupportEffects.MAX_OWNER_EFFECTS;i++)effects.healingResolved(h.context,UUID.randomUUID(),5,100,100,100,0);
        assertThrows(IllegalStateException.class,()->effects.healingResolved(h.context,h.actor,5,100,100,100,0));
        assertEquals(FiniteSupportEffects.MAX_OWNER_EFFECTS,effects.size());assertTrue(effects.forTarget(h.world,h.actor,0).isEmpty());
    }
    private static Stage09AuraRuntimeTest.Harness aura(String skill,String...passives){
        var h=new Stage09AuraRuntimeTest.Harness(skill);for(int i=0;i<passives.length;i++)h.link(passives[i],PassiveSlot.values()[i]);return h;
    }
    private static Stage09AuraRuntimeTest.Harness heal(double health){var h=aura("minor_heal","overflow");h.health=health;assertTrue(h.cast().committed());return h;}
    private static FiniteSupportEffects.Effect barrier(Stage09SupportRuntimeTest.Harness h){return h.runtime.finite().forTarget(h.world,h.actor,h.now).getFirst();}
    private static boolean compatible(String skill,String passive){
        var catalog=Stage01BTestSupport.bundle().catalog();return new CompatibilityService().assess(catalog.skill(new SkillId(skill)).orElseThrow(),catalog.passive(new PassiveId(passive)).orElseThrow()).accepted();
    }
}
