package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.ui.CharacterXpProjectionService;
import java.math.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage13HotPathTest {
    @Test void everyXpTransitionMatchesIndependentOriginalFormula(){
        var xp=new CharacterXpProjectionService();long sum=0;
        for(int level=1;level<=99;level++){
            assertEquals(sum,xp.levelStartXp(level));assertEquals(level,xp.project(sum).level());
            if(level>1)assertEquals(level-1,xp.project(sum-1).level());
            if(level==99){assertEquals(0,xp.xpToNext(level));break;}
            double pressure=1+5*Math.pow(Math.max(0,level-80d)/18,3);
            long expected=BigDecimal.valueOf(100*Math.pow(level,1.6)*pressure/10).setScale(0,RoundingMode.HALF_UP).longValueExact()*10;
            assertEquals(expected,xp.xpToNext(level));assertEquals(level,xp.project(sum+expected-1).level());sum=Math.addExact(sum,expected);
        }
        assertEquals(99,xp.project(Long.MAX_VALUE).level());assertEquals(1,xp.project(Long.MAX_VALUE).progress());
        assertThrows(IllegalArgumentException.class,()->xp.project(-1));
    }
    @Test void repeatedFourPlayerHudAndCombatReadsCompileOnlyOneTimePerLoadout(){
        var b=Stage01BTestSupport.bundle();var actors=new ArrayList<UUID>();
        for(int i=0;i<4;i++){var actor=UUID.randomUUID();actors.add(actor);assertTrue(b.service().equipSkill(actor,SkillSlot.SKILL01,new SkillId("quick_slash")).success());}
        for(int tick=0;tick<200;tick++)for(var actor:actors)for(int victim=0;victim<16;victim++)assertFalse(b.service().getPresentationView(actor).plans().isEmpty());
        for(var actor:actors)assertEquals(1,b.service().presentationCompilations(actor));
    }
    @Test void publishedProgressAndAttributesRemainFreshWithoutRecompilingLinks(){
        var b=Stage01BTestSupport.bundle();var actor=UUID.randomUUID();b.service().equipSkill(actor,SkillSlot.SKILL01,new SkillId("fire_bolt"));
        var before=b.service().getPresentationView(actor);
        assertTrue(b.service().mutateProgress(actor,before.state().revision,"xp-fixture",state->state.currentXp=10).success());
        assertTrue(b.service().setDevelopmentAttribute(actor,com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.INT,42).success());
        var after=b.service().getPresentationView(actor);assertEquals(10,after.state().currentXp);assertEquals(42,after.state().attributes.get("INT"));
        assertSame(before.plans().get(SkillSlot.SKILL01),after.plans().get(SkillSlot.SKILL01));assertEquals(1,b.service().presentationCompilations(actor));
    }
    @Test void changedTopologyInvalidatesAndInvalidForkCannotPublishNewCache(){
        var b=Stage01BTestSupport.bundle();var actor=UUID.randomUUID();b.service().equipSkill(actor,SkillSlot.SKILL01,new SkillId("fire_bolt"));
        var first=b.service().getPresentationView(actor);b.service().equipPassive(actor,PassiveSlot.PASSIVE01,new PassiveId("fork"));
        assertTrue(b.service().link(actor,LinkNodeId.PASSIVE01,LinkNodeId.SKILL01).success());
        var linked=b.service().getPresentationView(actor);assertNotEquals(first.plans().get(SkillSlot.SKILL01).planHash(),linked.plans().get(SkillSlot.SKILL01).planHash());
        long count=b.service().presentationCompilations(actor);
        assertFalse(b.service().equipSkill(actor,SkillSlot.SKILL01,new SkillId("quick_slash")).success());
        assertSame(linked.plans().get(SkillSlot.SKILL01),b.service().getPresentationView(actor).plans().get(SkillSlot.SKILL01));assertEquals(count,b.service().presentationCompilations(actor));
    }
    @Test void callerOwnedViewCannotPoisonStateOrCompiledCache(){
        var b=Stage01BTestSupport.bundle();var actor=UUID.randomUUID();b.service().equipSkill(actor,SkillSlot.SKILL01,new SkillId("fire_bolt"));
        var view=b.service().getPresentationView(actor);view.state().skill(SkillSlot.SKILL01,new SkillId("quick_slash"));
        assertEquals("fire_bolt",b.service().getPresentationView(actor).state().skill(SkillSlot.SKILL01).orElseThrow().value());
        assertEquals(1,b.service().presentationCompilations(actor));
    }
    @Test void uncertainPersistenceStillHidesAllCachedExecutablePlans(){
        var b=Stage01BTestSupport.bundle();var actor=UUID.randomUUID();b.service().equipSkill(actor,SkillSlot.SKILL01,new SkillId("fire_bolt"));
        assertFalse(b.service().getPresentationView(actor).plans().isEmpty());b.repository().failSave=true;
        assertFalse(b.service().unequipSkill(actor,SkillSlot.SKILL01).success());
        var safe=b.service().getPresentationView(actor);assertTrue(safe.plans().isEmpty());assertTrue(safe.warnings().stream().anyMatch(w->w.contains("PERSISTENCE_UNCERTAIN")));
    }
    @Test void immutableProfilesAreSharedButPlansRemainActorOwned(){
        var a=Stage01BTestSupport.bundle();var b=Stage01BTestSupport.bundle();
        assertSame(Stage04SkillProfiles.loadCanonical(a.catalog()),Stage04SkillProfiles.loadCanonical(b.catalog()));
        assertEquals(89,Stage04SkillProfiles.loadCanonical(a.catalog()).all().size());
        assertThrows(UnsupportedOperationException.class,()->Stage04SkillProfiles.loadCanonical(a.catalog()).all().clear());
    }
    @Test void frenzyCannotChargeOrPretendToApplyAnUnverifiedNativeSpeedModifier(){
        var h=new Stage11ResourcePassivesTest.H("frenzy");h.weapon="SWORD";
        for(int i=0;i<10;i++)assertEquals(com.inigmasgames.hytalerpg.execution.strike.NativeStanceProfile.GATE,h.cast().code());
        assertEquals(100,h.current(com.inigmasgames.hytalerpg.combat.resource.ResourceType.STAMINA));assertEquals(0,h.cooldownSaves);assertTrue(h.contexts.isEmpty());
        var p=Stage04SkillProfiles.loadCanonical(h.b.catalog()).require("frenzy");assertEquals(12,p.resourceCost());assertEquals(10,p.cooldownSeconds());
        assertEquals(5,p.nativeStance().maximumStacks());assertEquals(.04,p.nativeStance().attackSpeedPerStack());assertEquals(2,p.nativeStance().staminaPerSecond());
        assertEquals(3,p.nativeStance().inactivitySeconds());assertTrue(p.nativeStance().cooldownOnEnd());assertEquals(0,p.damageCoefficient());
        assertFalse(ProfileComponentPolicy.finiteUpfront(p));assertFalse(ProfileComponentPolicy.retaliation(p));
    }
}
