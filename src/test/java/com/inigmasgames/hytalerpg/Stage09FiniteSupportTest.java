package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets;
import com.inigmasgames.hytalerpg.combat.power.ItemPowerDescriptor;
import com.inigmasgames.hytalerpg.combat.status.*;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.support.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class Stage09FiniteSupportTest {
    @Test void cohortHasExactlyFiveFiniteZeroDamageProfiles(){
        var h=new Harness("taunt");var cohort=Set.of("taunt","weakening_hex","hunter_s_mark","intimidate","battle_cry");
        var profiles=h.profiles.all().values().stream().filter(p->cohort.contains(p.skillId())).toList();
        assertEquals(5,profiles.size());assertTrue(profiles.stream().allMatch(p->p.damageCoefficient()==0));
        assertEquals(89,h.bundle.catalog().skills().size());assertEquals(67,h.bundle.catalog().passives().size());
    }
    @Test void tauntCommitsOneStaminaCostAndCooldown(){
        var h=new Harness("taunt");assertTrue(h.cast().committed());assertEquals(95,h.mana);assertEquals(1,h.runtime.finite().size());
        assertFalse(h.cast().committed());assertEquals(95,h.mana);
        assertEquals(5,h.runtime.finite().control(h.world,h.target,SupportProfile.Kind.TAUNT,0).orElseThrow().ends());
    }
    @Test void intimidateHasNoPlaceholderDamageAndTwoSecondsFear(){
        var h=new Harness("intimidate");h.cast();assertEquals(94,h.mana);assertEquals(40,h.health);
        assertEquals(2,h.runtime.finite().control(h.world,h.target,SupportProfile.Kind.FEAR,0).orElseThrow().ends());
    }
    @Test void intimidateCatalogDoesNotAdvertiseDamageOrAcceptPotency(){
        var h=new Harness("intimidate");var skill=h.bundle.catalog().skill(new com.inigmasgames.hytalerpg.domain.SkillId("intimidate")).orElseThrow();
        assertFalse(skill.canCrit());assertFalse(skill.linkCompatibilityTags().contains("DAMAGE"));
        assertTrue(h.bundle.service().equipPassive(h.actor,com.inigmasgames.hytalerpg.domain.PassiveSlot.PASSIVE01,new com.inigmasgames.hytalerpg.domain.PassiveId("potency")).success());
        assertFalse(h.bundle.service().link(h.actor,com.inigmasgames.hytalerpg.domain.LinkNodeId.PASSIVE01,com.inigmasgames.hytalerpg.domain.LinkNodeId.SKILL01).success());
    }
    @Test void weakHexIsPointEightFiveAndRefreshesWithoutMultiplication(){
        var h=new Harness("weakening_hex");h.cast();h.runtime.finite().apply(h.context,List.of(h.target),8,2);
        assertEquals(.85,h.runtime.finite().nativeOutgoingFactor(h.world,h.target,3),1e-9);assertEquals(1,h.runtime.finite().size());
        assertEquals(1,h.runtime.finite().nativeOutgoingFactor(h.world,h.target,10));
    }
    @Test void hunterMarkAddsToExistingIncreasedBucket(){
        var h=new Harness("hunter_s_mark");h.cast();
        var base=new ModifierBuckets(List.of(.15),List.of(),List.of(1.35),List.of(.1));
        assertEquals(1.25*1.35*.9,h.runtime.finite().damageModifiers(h.world,h.actor,h.target,base,0).factor(),1e-9);
    }
    @Test void markNeverBoostsOtherCastersOrNativeHits(){
        var h=new Harness("hunter_s_mark");h.cast();
        assertEquals(1,h.runtime.finite().damageModifiers(h.world,UUID.randomUUID(),h.target,ModifierBuckets.NONE,0).factor());
        assertEquals(1,h.runtime.finite().nativeOutgoingFactor(h.world,h.actor,0));
    }
    @Test void newMarkReplacesPreviousTargetForSameCaster(){
        var h=new Harness("hunter_s_mark");h.cast();var second=UUID.randomUUID();h.runtime.finite().apply(h.context,List.of(second),15,1);
        assertEquals(1,h.runtime.finite().size());assertTrue(h.runtime.finite().forTarget(h.world,h.target,1).isEmpty());
    }
    @Test void battleCrySamplesOnceAndDoesNotBecomeAura(){
        var h=new Harness("battle_cry");var ally=UUID.randomUUID();h.members.add(ally);h.cast();h.members.clear();
        assertEquals(0,h.runtime.auraCount());assertEquals(0,h.reserved);assertEquals(2,h.runtime.finite().size());
        assertEquals(.1,h.runtime.finite().movementIncreased(h.world,ally,7));
        assertEquals(0,h.runtime.finite().movementIncreased(h.world,ally,8));
    }
    @Test void duplicateRallyDoesNotStackCopies(){
        var h=new Harness("battle_cry");h.cast();h.runtime.finite().apply(h.context,h.members,8,1);
        assertEquals(1.1,h.runtime.finite().nativeOutgoingFactor(h.world,h.actor,2),1e-9);assertEquals(1,h.runtime.finite().size());
    }
    @Test void weakHexAndRallyUseSeparateOperators(){
        var rally=new Harness("battle_cry");rally.cast();var weak=new Harness("weakening_hex");weak.cast();
        var foreign=withOwnerWorld(weak.context,weak.actor,rally.world);
        rally.runtime.finite().apply(foreign,List.of(rally.actor),8,0);
        assertEquals(1.1*.85,rally.runtime.finite().nativeOutgoingFactor(rally.world,rally.actor,0),1e-9);
    }
    @Test void identicalForeignRallyUsesStrongestNotSum(){
        var h=new Harness("battle_cry");h.cast();
        h.runtime.finite().apply(withOwnerWorld(h.context,UUID.randomUUID(),h.world),List.of(h.actor),8,0);
        assertEquals(1.1,h.runtime.finite().damageModifiers(h.world,h.actor,UUID.randomUUID(),ModifierBuckets.NONE,0).factor(),1e-9);
        assertEquals(.1,h.runtime.finite().movementIncreased(h.world,h.actor,0));
    }
    @Test void fearGraceExcludesFirstQuarterSecond(){
        var h=new Harness("intimidate");h.cast();
        assertFalse(h.runtime.finite().directDamage(h.world,h.target,.249,true,true));
        assertTrue(h.runtime.finite().directDamage(h.world,h.target,.25,true,true));assertEquals(0,h.runtime.finite().size());
    }
    @Test void periodicOrCancelledDamageDoesNotBreakFear(){
        var h=new Harness("intimidate");h.cast();assertFalse(h.runtime.finite().directDamage(h.world,h.target,.5,false,true));
        assertFalse(h.runtime.finite().directDamage(h.world,h.target,.5,true,false));assertEquals(1,h.runtime.finite().size());
    }
    @Test void finiteEffectPreservesAllExecutionTraceIds(){
        var h=new Harness("taunt");h.cast();var e=h.runtime.finite().forTarget(h.world,h.target,0).getFirst();
        assertEquals(h.context.rootCastId(),e.rootCastId());assertEquals(h.context.skillInstanceId(),e.skillInstanceId());
        assertEquals(h.context.request().correlationId(),e.correlationId());
    }
    @Test void ownerLogoutAndTargetRemovalForgetFiniteEffects(){
        var h=new Harness("battle_cry");h.members.add(UUID.randomUUID());h.cast();h.runtime.cancel(h.actor,"LOGOUT",h);assertEquals(0,h.runtime.finite().size());
        var mark=new Harness("hunter_s_mark");mark.cast();mark.runtime.finite().forget(mark.target);assertEquals(0,mark.runtime.finite().size());
    }
    @Test void worldIsolationAndWorldCleanup(){
        var h=new Harness("weakening_hex");h.cast();assertEquals(1,h.runtime.finite().nativeOutgoingFactor(UUID.randomUUID(),h.target,0));
        h.runtime.finite().clearWorld(h.world);assertEquals(0,h.runtime.finite().size());
    }
    @Test void overfullBurstRejectsWholeBatch(){
        var h=new Harness("battle_cry");h.cast();var targets=new ArrayList<UUID>();for(int i=0;i<65;i++)targets.add(UUID.randomUUID());
        assertThrows(IllegalStateException.class,()->h.runtime.finite().apply(h.context,targets,8,0));assertEquals(1,h.runtime.finite().size());
    }
    @Test void finiteTargetBudgetRejectsWithoutDiscardingEarlierEffects(){
        var h=new Harness("weakening_hex");h.cast();
        for(int i=1;i<32;i++)h.runtime.finite().apply(withOwnerWorld(h.context,UUID.randomUUID(),h.world),List.of(h.target),8,0);
        assertThrows(IllegalStateException.class,()->h.runtime.finite().apply(withOwnerWorld(h.context,UUID.randomUUID(),h.world),List.of(h.target),8,0));
        assertEquals(32,h.runtime.finite().size());assertEquals(.85,h.runtime.finite().nativeOutgoingFactor(h.world,h.target,1),1e-9);
    }
    @Test void tauntEliteIsSeparateFromHardControlDR(){
        var h=new Harness("taunt");var statuses=h.kernel.statuses();var elite=new ControlProfile(false,false,false,true);
        for(int i=0;i<4;i++)assertEquals(5,statuses.apply(h.target,RpgStatusType.TAUNT,elite,5).remainingSeconds());
        assertEquals(1,statuses.apply(h.target,RpgStatusType.FEAR,elite,2).remainingSeconds());
    }
    @Test void bossRootUsesNormativeThirtyPercentTwoSecondSubstitute(){
        var h=new Harness("taunt");var r=h.kernel.statuses().apply(h.target,RpgStatusType.ROOT,new ControlProfile(false,true,false),1.5);
        assertEquals(RpgStatusType.FROZEN_SUBSTITUTE_SLOW,r.type());assertEquals(2,r.remainingSeconds());
        assertEquals(.3,h.kernel.statuses().strongestSlow(h.target).magnitude());
    }
    private static SkillExecutionContext withOwnerWorld(SkillExecutionContext c,UUID actor,UUID world){
        var r=new SkillExecutionRequest(actor,c.request().slot(),"test",1,c.request().correlationId(),Vec3.FORWARD);
        return new SkillExecutionContext(r,c.rootCastId(),c.skillInstanceId(),c.profile(),c.compiledPlan(),c.snapshot(),c.equipment(),
                new CommittedTarget(world,Vec3.ZERO,Vec3.ZERO,Vec3.FORWARD,c.target().entityId()),false);
    }
    static final class Harness extends Stage09SupportRuntimeTest.Harness {
        final UUID target=UUID.randomUUID();
        Harness(String skill){super(skill);}
        @Override public Equipment equipment(){
            var kind=profile.allowedMainHandKinds().stream().findFirst().orElse("STAFF");
            return new Equipment(new Item("fixture",kind,new ItemPowerDescriptor("fixture",Set.of("RPG_WEAPON_MAGIC"),20d,20d)),null);
        }
        @Override public CommittedTarget captureTarget(Stage04SkillProfile p,CompiledSkillPlan plan,SkillExecutionRequest request){
            return new CommittedTarget(world,Vec3.ZERO,Vec3.ZERO,Vec3.FORWARD,p.support().hostileTarget()?target:actor);
        }
        @Override public void finiteEffect(SkillExecutionContext c,FiniteSupportEffects effects,double now){
            effects.apply(c,c.profile().support().hostileTarget()?List.of(target):List.copyOf(members),c.profile().support().durationSeconds(),now);
        }
    }
}
