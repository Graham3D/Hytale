package com.inigmasgames.hytalerpg;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.combat.damage.*;
import com.inigmasgames.hytalerpg.combat.power.*;
import com.inigmasgames.hytalerpg.combat.resource.*;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.strike.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

class QuickSlashLightProfileTest {
    static JsonObject json(String value){return JsonParser.parseString(value).getAsJsonObject();}
    static class Graph implements WeaponLightAttackProfileResolver.Assets {
        final Map<String,JsonObject> ops=new HashMap<>(),roots=new HashMap<>();
        Graph(String damage){
            roots.put("primary",json("{\"Interactions\":[\"charge\"]}"));
            roots.put("hit",json("{\"Interactions\":[\"damage\"]}"));
            ops.put("charge",json("{\"Type\":\"Charging\",\"Next\":{\"0\":\"chain\",\"0.2\":\"unsupported-heavy\"}}"));
            ops.put("chain",json("{\"Type\":\"Chaining\",\"Next\":[\"windup\",\"other-combo\"]}"));
            ops.put("windup",json("{\"Type\":\"Simple\",\"RunTime\":0.12,\"Next\":\"selector\"}"));
            ops.put("selector",json("{\"Type\":\"Selector\",\"RunTime\":0.06,\"HitEntity\":\"hit\",\"Next\":{\"Type\":\"Simple\",\"RunTime\":0.22}}"));
            ops.put("damage",json("{\"Type\":\"DamageEntity\",\"DamageCalculator\":{\"Type\":\"Absolute\",\"BaseDamage\":"+damage+"},\"TargetedDamage\":{},\"AngledDamage\":[]}"));
        }
        public JsonObject root(String id){return roots.get(id);}public JsonObject interaction(String id){return ops.get(id);}
        WeaponLightAttackProfile resolve(){return new WeaponLightAttackProfileResolver(this,Map.of()).resolve("fixture","SWORD","primary","test-revision");}
    }
    static WeaponLightAttackProfile profile(){return new Graph("{\"Physical\":100,\"Fire\":20,\"Ice\":10}").resolve();}
    static final DamageCalculationService DAMAGE=RpgCombatKernel.createProduction().damage();
    static WeaponLightHit hit(WeaponLightAttackProfile p,String id,boolean crit){return WeaponLightHit.create(p,.375,id,()->.5,crit,true);}
    @Test void normalUnchargedFirstComboExtractionAndContactFraction(){
        var p=profile();assertEquals(.4,p.normalDuration(),1e-9);assertEquals(.12,p.contactTime(),1e-9);assertEquals(3,p.components().size());
        assertEquals("fixture",p.weaponId());assertEquals("test-revision",p.sourceRevision());
    }
    @Test void twoHitsPreserveFullMultiChannelComposition(){
        var p=profile();var a=hit(p,"root/0",false);var b=hit(p,"root/1",false);
        assertNotEquals(a.executionId(),b.executionId());assertSame(a.profile(),b.profile());
        var expected=Map.of("PHYSICAL",37.5,"FIRE",7.5,"COLD",3.75);
        for(var c:a.sampled()){
            double first=a.calculate(DAMAGE,c,0,ModifierBuckets.NONE,1.5).preMitigationDamage();
            double second=b.calculate(DAMAGE,c,0,ModifierBuckets.NONE,1.5).preMitigationDamage();
            assertEquals(expected.get(c.channel()),first,1e-9);assertEquals(expected.get(c.channel())*2,first+second,1e-9);
        }
    }
    @Test void physicalOnlyAndProviderFireUseSameConsumer(){
        var base=new Graph("{\"Physical\":100}").resolve();
        assertEquals(37.5,hit(base,"0",false).calculate(DAMAGE,hit(base,"0",false).sampled().getFirst(),0,ModifierBuckets.NONE,1.5).preMitigationDamage());
        var assembled=base.assemble(List.of(p->List.of(new WeaponLightAttackProfile.Component("affix/fire","FIRE",20,20,"affix",
            WeaponDamageExecution.Provenance.WEAPON_AFFIX,true))));
        var h=hit(assembled,"0",false);assertEquals(2,h.sampled().size());
        assertEquals(7.5,h.calculate(DAMAGE,h.sampled().getLast(),0,ModifierBuckets.NONE,1.5).preMitigationDamage());
    }
    @Test void nativeRandomnessIsSharedByChannelsAndIndependentBetweenAuthoredHits(){
        var g=new Graph("{\"Physical\":100,\"Fire\":20}");g.ops.get("damage").getAsJsonObject("DamageCalculator").addProperty("RandomPercentageModifier",.15);
        var p=g.resolve();int[] calls={0};
        var low=p.sample(.375,()->{calls[0]++;return 0;},true);assertEquals(1,calls[0]);
        var high=p.sample(.375,()->1,true);
        assertEquals(31.875,low.getFirst().sourceAmount(),1e-9);assertEquals(43.125,high.getFirst().sourceAmount(),1e-9);
        assertEquals(.2,low.getLast().sourceAmount()/low.getFirst().sourceAmount(),1e-9);
    }
    @ParameterizedTest @ValueSource(strings={"ChargingUnsupported","Condition","Projectile","Repeat","StatsCondition"})
    void unsupportedComplexProfilesRejectInsteadOfFallingBack(String type){
        var g=new Graph("{\"Physical\":100}");g.ops.get("windup").addProperty("Type",type);
        assertThrows(IllegalArgumentException.class,g::resolve);
    }
    @Test void rejectsRealConditionalDamageButAcceptsEmptyCodecMaps(){
        var g=new Graph("{\"Physical\":100}");assertNotNull(g.resolve());
        g.ops.get("damage").add("TargetedDamage",json("{\"Head\":{\"DamageCalculator\":{\"BaseDamage\":{\"Physical\":200}}}}"));assertThrows(IllegalArgumentException.class,g::resolve);
    }
    @Test void rejectsMultipleDamageLeavesAndCycles(){
        var g=new Graph("{\"Physical\":100}");g.ops.get("damage").addProperty("Next","damage");
        assertThrows(IllegalArgumentException.class,g::resolve);
    }
    @Test void missingVariableAndRelativeCalculatorsAreNotInvented(){
        var g=new Graph("{\"Physical\":100}");g.ops.get("damage").getAsJsonObject("DamageCalculator").addProperty("Type","Relative");
        assertThrows(IllegalArgumentException.class,g::resolve);
        g.ops.put("windup",json("{\"Type\":\"Replace\",\"Var\":\"missing\"}"));assertThrows(IllegalArgumentException.class,g::resolve);
    }
    @Test void exactItemReplacementPreservesItsDamage(){
        var g=new Graph("{\"Fire\":31}");g.ops.put("replace",json("{\"Type\":\"Replace\",\"Var\":\"source\"}"));
        g.roots.put("hit",json("{\"Interactions\":[\"replace\"]}"));g.roots.put("itemHit",json("{\"Interactions\":[\"damage\"]}"));
        var p=new WeaponLightAttackProfileResolver(g,Map.of("source","itemHit")).resolve("any-fire-weapon","LONGSWORD","primary","r");
        assertEquals("FIRE",p.components().getFirst().channel());assertEquals(31,p.components().getFirst().minimum());
    }
    @Test void independentCriticalHitsDoNotRerollInsideComponents(){
        var yes=hit(profile(),"a",true);var no=hit(profile(),"b",false);
        for(var c:yes.sampled()){var a=yes.calculate(DAMAGE,c,0,ModifierBuckets.NONE,1.5);var b=no.calculate(DAMAGE,c,0,ModifierBuckets.NONE,1.5);
            assertTrue(a.critical());assertFalse(b.critical());assertEquals(b.preMitigationDamage()*1.5,a.preMitigationDamage(),1e-9);}
    }
    @Test void nonCriticalComponentIsNotCriticallyMultipliedAgain(){
        var p=new Graph("{\"Physical\":100}").resolve().assemble(List.of(base->List.of(
            new WeaponLightAttackProfile.Component("imbue","FIRE",20,20,"",WeaponDamageExecution.Provenance.FLAME_WEAPON,false))));
        var h=hit(p,"a",true);assertEquals(7.5,h.calculate(DAMAGE,h.sampled().getLast(),0,ModifierBuckets.NONE,1.5).preMitigationDamage());
    }
    @Test void halfCycleContactScheduleDoesNotDeliverHitZeroAtCommit(){
        var p=profile();var s=new StrikeRepeatSchedule(2,p.normalDuration()/2,0,p.normalDuration(),p.contactTime()/2);
        assertTrue(s.claimDue(0).isEmpty());assertTrue(s.claimDue(59_999_999).isEmpty());assertEquals(0,s.claimDue(60_000_000).orElseThrow());
        assertTrue(s.claimAnimationDue(199_999_999).isEmpty());assertEquals(1,s.claimAnimationDue(200_000_000).orElseThrow());
        assertEquals(1,s.claimDue(260_000_000).orElseThrow());assertTrue(s.claimDue(300_000_000).isEmpty());
        assertFalse(s.complete(399_999_999));assertTrue(s.complete(400_000_000));
    }
    static class Harness extends Stage11ResourcePassivesTest.H {
        WeaponLightAttackProfile reference=profile();int captures;
        Harness(){super("quick_slash");weapon="SWORD";}
        @Override public WeaponLightAttackProfile captureWeaponLightAttack(Equipment e){captures++;return reference;}
    }
    @Test void productionCommitCapturesOneProfileAndChargesOnce(){
        var h=new Harness();assertEquals(SkillExecutionResult.Status.COMMITTED,h.cast().status());var c=h.last();
        assertSame(h.reference,c.effects().lightAttack());assertEquals(1,h.captures);assertEquals(95,h.current(ResourceType.STAMINA));
        assertEquals(100,h.current(ResourceType.MANA));assertEquals(1,h.cooldownSaves);
        var original=c.effects().lightAttack();h.reference=new Graph("{\"Physical\":999}").resolve();h.weapon="LONGSWORD";
        assertSame(original,c.effects().lightAttack());assertThrows(IllegalStateException.class,()->c.effects().captureLightAttack(h.reference));
        assertEquals(.375,c.profile().strike().coefficient());assertEquals(.8,c.profile().cooldownSeconds());
    }
    @ParameterizedTest @ValueSource(strings={"potency","multistrike","ruthless","shockwave","lifeblood","attunement","executioner","opportunist"})
    void importantPassivesRetainCompiledCommitAndSnapshot(String passive){
        var h=new Harness();h.link(passive,PassiveSlot.PASSIVE01);assertEquals(SkillExecutionResult.Status.COMMITTED,h.cast().status());
        assertSame(h.reference,h.last().effects().lightAttack());assertEquals(1,h.cooldownSaves);assertEquals(1,h.captures);
    }
    @Test void twoMantleExecutionsNotMultipliedByFiveVictims(){
        var resource=RpgCombatKernel.createProduction().resources();var port=new WeaponFireDecisionTest.Port();
        var owner=new RootEffectBudget(WeaponFireDecisionTest.ACTOR,"root");int pulses=0;
        for(int i=0;i<2;i++){
            var h=hit(profile(),"root/light-"+i,false);
            var envelope=h.envelope(new WeaponDamageExecution.Identity(WeaponFireDecisionTest.WORLD,WeaponFireDecisionTest.ACTOR,"root",h.executionId(),h.executionId()),
                DAMAGE,0,ModifierBuckets.NONE,1.5,false);assertEquals(7.5,envelope.sourceFire());
            var d=owner.weaponExecution(envelope);assertSame(d,owner.weaponExecution(envelope));
            d.decideProportional(true,()->WeaponFireDecisionTest.recipients(5,7.5*.25),1,resource,port,()->fail("funded"));
            for(int victim=0;victim<5;victim++){assertEquals(7.5,d.directAmount("weapon/FIRE",7.5));assertEquals(37.5,d.directAmount("weapon/PHYSICAL",37.5));}
            if(!d.claimPulse().isEmpty())pulses++;assertTrue(d.claimPulse().isEmpty());
        }
        assertEquals(2,pulses);assertEquals(2,port.writes);assertEquals(92.5,port.current,1e-9);
    }
    @Test void multistrikeSharesRootButCannotRecursivelyProcMantle(){
        var h=new Harness();h.link("multistrike",PassiveSlot.PASSIVE01);h.cast();var c=h.last();assertEquals(6,c.profile().strike().repeats());
        for(int i=1;i<=2;i++){var child=c.multistrikeCopy(i);assertEquals(c.rootCastId(),child.rootCastId());assertSame(c.effects(),child.effects());
            assertThrows(IllegalStateException.class,()->child.multistrikeCopy(1));
            var execution=hit(c.effects().lightAttack(),child.skillInstanceId(),false).envelope(new WeaponDamageExecution.Identity(h.actor,h.actor,c.rootCastId(),child.skillInstanceId(),"0"),
                DAMAGE,0,child.snapshot().modifiers(),1.5,true);assertEquals(0,execution.sourceFire());}
    }
    @Test void wrongEquipmentAndNoStaminaStillRejectBeforeCapture(){
        var h=new Harness();h.weapon="STAFF";assertEquals(SkillExecutionResult.Status.REJECTED,h.cast().status());assertEquals(0,h.captures);
        h.weapon="SWORD";h.current.put(ResourceType.STAMINA,0d);assertEquals(SkillExecutionResult.Status.REJECTED,h.cast().status());assertEquals(0,h.captures);
    }
    @Test void serialTraversalAndKnownRecipientTailDoNotCloneNativeSideEffects(){
        var g=new Graph("{\"Physical\":100}");
        g.ops.get("damage").add("Next",json("{\"Type\":\"Serial\",\"Interactions\":[{\"Type\":\"ApplyEffect\",\"EffectId\":\"Red_Flash\",\"Entity\":\"Target\"},{\"Type\":\"ClearEntityEffect\",\"EntityEffectId\":\"Potion_Stamina_Regen\",\"Entity\":\"Target\"}]}"));
        g.ops.put("serial",json("{\"Type\":\"Serial\",\"Interactions\":[\"windup\"]}"));
        g.ops.get("chain").add("Next",JsonParser.parseString("[\"serial\"]"));
        assertEquals(.4,g.resolve().normalDuration(),1e-9);
        g.ops.get("damage").getAsJsonObject("Next").getAsJsonArray("Interactions").get(0).getAsJsonObject().addProperty("EffectId","Unknown_Damage_Effect");
        assertThrows(IllegalArgumentException.class,g::resolve);
    }
    @ParameterizedTest @ValueSource(strings={"INTERRUPTED","DEATH","PLAYER_DISCONNECT","WORLD_DRAIN","COMMITTED_EQUIPMENT_CHANGED"})
    void cancelledRootAndScheduleCannotProduceAStaleSecondHit(String reason){
        var h=new Harness();h.cast();var c=h.last();assertTrue(h.service.ownsActiveRoot(c));
        var s=new StrikeRepeatSchedule(2,.2,0,.4,.06);assertEquals(0,s.claimDue(60_000_000).orElseThrow());
        assertTrue(h.service.cancel(h.actor,reason));s.cancel();
        assertFalse(h.service.ownsActiveRoot(c));assertTrue(s.claimDue(1_000_000_000).isEmpty());assertTrue(s.claimAnimationDue(1_000_000_000).isEmpty());
        assertEquals(95,h.current(ResourceType.STAMINA));assertEquals(100,h.current(ResourceType.MANA));assertEquals(1,h.cooldownSaves);
        h.ready();h.cast();assertFalse(h.service.ownsActiveRoot(c));assertTrue(h.service.ownsActiveRoot(h.last()));
    }
    @Test void multiChannelReceiptHasOneGenericProcAttemptAndPhysicalOnlyBleedMagnitude(){
        var h=new Harness();h.link("hemorrhage",PassiveSlot.PASSIVE01);h.link("terror",PassiveSlot.PASSIVE02);h.cast();
        var b=new Stage11HitProcTest.B();b.element="OTHER";b.amount=48.75;
        var port=new Stage11HitProcTest.P();int[] rolls={0};var proc=new HitProcRuntime(()->{rolls[0]++;return 0;});
        proc.observedComposition(h.last(),b.hit(),Set.of("PHYSICAL","FIRE","COLD"),37.5,0,port);
        proc.observedComposition(h.last(),b.hit(),Set.of("PHYSICAL","FIRE","COLD"),37.5,0,port);
        assertEquals(1,port.bleeds);assertEquals(4.5,port.dps);assertEquals(1,port.fears);assertEquals(2,rolls[0]);
        b.contact="second-authored-hit";
        proc.observedComposition(h.last(),b.hit(),Set.of("PHYSICAL","FIRE","COLD"),37.5,.2,port);
        assertEquals(2,port.bleeds);assertEquals(1,port.fears);assertEquals(3,rolls[0]); // Existing victim fear ICD remains authoritative.
    }
    @Test void shockwaveRemainsOncePerRootAcrossBothAuthoredHits(){
        var h=new Harness();h.link("shockwave",PassiveSlot.PASSIVE01);h.cast();
        var v=com.inigmasgames.hytalerpg.execution.math.Vec3.ZERO;
        var target=new StrikeGeometryService.Candidate<String>("victim","victim",v,true,false,false);
        var primary=List.of(new StrikeSecondaryRuntime.Hit<>(target,45,45,false));int[] delivered={0};
        var port=new StrikeSecondaryRuntime.Port<String>(){
            public List<StrikeGeometryService.Candidate<String>> candidates(com.inigmasgames.hytalerpg.execution.math.Vec3 p,double r){return List.of(target);}
            public boolean lineOfSight(com.inigmasgames.hytalerpg.execution.math.Vec3 p,StrikeGeometryService.Candidate<String> t){return true;}
            public void damage(SkillExecutionContext child,StrikeGeometryService.Candidate<String> t,Double amount){delivered[0]++;assertTrue(child.derivedRelease());assertEquals(18,amount);}
        };
        var runtime=new StrikeSecondaryRuntime();runtime.afterPrimary(h.last(),0,v,com.inigmasgames.hytalerpg.execution.math.Vec3.FORWARD,primary,port);
        runtime.afterPrimary(h.last(),1,v,com.inigmasgames.hytalerpg.execution.math.Vec3.FORWARD,primary,port);assertEquals(1,delivered[0]);
    }
    @Test void admittedDerivedSequenceIsNotMistakenForATerminatedManualRoot(){
        var h=new Harness();h.cast();var root=h.last();var child=root.echoCopy();h.service.terminate(root,"STRIKE_REPEATS_COMPLETE");
        assertFalse(h.service.allowsStrikeSequence(root));assertTrue(h.service.allowsStrikeSequence(child));
        assertSame(root.effects().lightAttack(),child.effects().lightAttack());
        var schedule=new StrikeRepeatSchedule(2,.2,0,.4,.06);schedule.cancel();assertTrue(schedule.claimDue(1_000_000_000).isEmpty());
    }
    @Test void overlappingPassivePairDoesNotReplaceThePaidPairAndTeardownCancelsBoth(){
        var registry=new StrikeSequenceRegistry<StrikeRepeatSchedule>();var actor=UUID.randomUUID();
        var root=new StrikeRepeatSchedule(2,.26,0,.52,.1145);var echo=new StrikeRepeatSchedule(2,.26,200_000_000,.52,.1145);
        registry.put(actor,"root",root);assertEquals(0,root.claimDue(114_500_000).orElseThrow());registry.put(actor,"root/echo",echo);
        assertEquals(2,registry.owned(actor).size());assertEquals(0,echo.claimDue(314_500_000).orElseThrow());
        assertEquals(1,root.claimDue(374_500_000).orElseThrow());assertSame(root,registry.remove("root"));assertEquals(List.of(echo),registry.owned(actor));
        registry.cancel(actor).forEach(StrikeRepeatSchedule::cancel);assertTrue(echo.claimDue(900_000_000).isEmpty());assertTrue(registry.owned(actor).isEmpty());
    }
    @Test void boundedSequenceOwnerRejectsDuplicatesAndOverflowWithoutReplacingEntries(){
        var registry=new StrikeSequenceRegistry<String>();var actor=UUID.randomUUID();registry.put(actor,"root","original");
        assertThrows(IllegalStateException.class,()->registry.put(actor,"root","replacement"));assertEquals(List.of("original"),registry.owned(actor));
        for(int i=1;i<7;i++)registry.put(actor,"child"+i,"child"+i);
        assertThrows(IllegalStateException.class,()->registry.put(actor,"overflow","new"));assertEquals(7,registry.owned(actor).size());
    }
    @Test void feedbackOnlyAnglesRetainTheNativeBaseCalculator(){
        var g=new Graph("{\"Physical\":5}");g.ops.get("damage").add("AngledDamage",JsonParser.parseString("[{\"Angle\":180,\"DamageEffects\":{},\"DamageCalculator\":null}]"));
        assertEquals(5,g.resolve().components().getFirst().minimum());
        g.ops.get("damage").getAsJsonArray("AngledDamage").get(0).getAsJsonObject().add("DamageCalculator",json("{\"BaseDamage\":{\"Physical\":50}}"));
        assertThrows(IllegalArgumentException.class,g::resolve);
    }
}
