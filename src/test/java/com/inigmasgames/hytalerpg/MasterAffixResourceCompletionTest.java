package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.power.ItemPowerDescriptor;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects;
import com.inigmasgames.hytalerpg.gear.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class MasterAffixResourceCompletionTest {
    private static final GearCatalog CATALOG=GearCatalog.load();
    private static final GearAffixQaSuite QA=new GearAffixQaSuite(CATALOG);

    private static GearInstance item(String id){
        var item=QA.preview(QA.fixtures().stream().filter(f->f.fixtureId().equals("ab-"+id.toLowerCase(Locale.ROOT)+"-affixed"))
                .findFirst().orElseThrow());
        assertTrue(item.affixes().getFirst().value()>0);
        var attributes=new EnumMap<com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute,Integer>(com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.class);
        for(var attribute:com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.values())attributes.put(attribute,500);
        assertTrue(GearRequirements.resolve(99,attributes,
                List.of(new GearRequirements.Equipped(item.identity(),item.requirements(),Map.of()))).valid().contains(item.identity()));
        return item;
    }
    private static GearInstance unrolled(GearInstance source){
        return new GearInstance(source.schemaVersion(),UUID.randomUUID(),source.definitionRevision(),source.baseId(),
                source.baseName(),source.category(),source.sourceEra(),source.itemLevel(),source.rarity(),
                source.intrinsicThousandths(),source.intrinsicStats(),source.requirements(),List.of(),source.rngVersion(),true);
    }

    @Test void wa110ExtendsOnlyActualFriendlyFiniteReceiptExpiry(){
        var source=item("WA-110");
        var accepted=admitted(source,"WA-110").effects().snapshot();
        var treated=new FiniteDurationHarness("battle_cry",source);
        var unrolled=new FiniteDurationHarness("battle_cry",unrolled(source));
        var removed=new FiniteDurationHarness("battle_cry",null);
        assertTrue(treated.cast().committed());
        assertTrue(unrolled.cast().committed());
        assertTrue(removed.cast().committed());
        double baseSeconds=treated.context.profile().support().durationSeconds();
        double extended=FiniteSupportEffects.durationSeconds(treated.context);
        assertEquals(baseSeconds*(1+accepted.percent(GearEffectSnapshot.Operator.FINITE_SUPPORT_DURATION)),extended,1e-9);
        assertEquals(baseSeconds,FiniteSupportEffects.durationSeconds(unrolled.context),1e-9);
        assertEquals(baseSeconds,FiniteSupportEffects.durationSeconds(removed.context),1e-9);
        var effects=treated.runtime.finite();
        var receipt=effects.forTarget(treated.world,treated.actor,baseSeconds).getFirst();
        assertEquals(extended,receipt.ends(),1e-9);
        assertEquals(treated.context.profile().support().coefficient(),receipt.magnitude(),1e-9);
        assertEquals(treated.context.profile().support().movementIncreased(),receipt.movement(),1e-9);
        assertEquals(1,effects.forTarget(treated.world,treated.actor,Math.nextDown(extended)).size());
        assertTrue(effects.forTarget(treated.world,treated.actor,extended).isEmpty());
        for(var control:List.of(unrolled,removed)){
            assertEquals(baseSeconds,control.runtime.finite().forTarget(control.world,control.actor,0).getFirst().ends(),1e-9);
            assertTrue(control.runtime.finite().forTarget(control.world,control.actor,baseSeconds).isEmpty());
        }
        var hostile=new Stage09FiniteSupportTest.Harness("taunt");
        assertTrue(hostile.cast().committed());
        assertEquals(hostile.context.profile().support().durationSeconds(),
                FiniteSupportEffects.durationSeconds(withGear(hostile.context,accepted)),1e-9);
        var aura=new Stage09SupportRuntimeTest.Harness("managuard");
        assertTrue(aura.cast().committed());
        assertEquals(aura.context.profile().support().durationSeconds(),
                FiniteSupportEffects.durationSeconds(withGear(aura.context,accepted)),1e-9);
    }
    private static final class FiniteDurationHarness extends Stage09SupportRuntimeTest.Harness {
        private final GearInstance gear;
        FiniteDurationHarness(String skill,GearInstance gear){super(skill);this.gear=gear;}
        @Override public Equipment equipment(){
            var kind=profile.allowedMainHandKinds().stream().findFirst().orElse("STAFF");
            return new Equipment(new Item("fixture",kind,
                    new ItemPowerDescriptor("fixture",Set.of("RPG_WEAPON_MAGIC"),20d,20d)),null);
        }
        @Override public GearAffixRuntime.Effects gearEffects(){return new GearAffixRuntime.Effects(
                Map.of(),0,0,0,0,0,0,gear==null?GearEffectSnapshot.EMPTY:admitted(gear,"WA-110").effects().snapshot());}
        @Override public void finiteEffect(SkillExecutionContext context,FiniteSupportEffects effects,double now){
            effects.apply(context,List.copyOf(members),FiniteSupportEffects.durationSeconds(context),now);
        }
    }
    private static SkillExecutionContext withGear(SkillExecutionContext c,GearEffectSnapshot gear){
        return new SkillExecutionContext(c.request(),c.rootCastId(),c.skillInstanceId(),c.profile(),
                c.compiledPlan(),c.snapshot(),c.equipment(),c.target(),c.echo(),c.barrageBatch(),
                c.leechBudget(),c.multistrikeIndex(),c.effects(),c.secondaryKind(),c.effectiveSkillLevel(),gear);
    }

    @Test void wa009ChangesActualHeavySwingReleaseTimeAndRemovalRestoresIt(){
        var affixed=item("WA-009");
        // Heavy Swing retains its existing longsword while the cast-speed source is a helm.
        var rolled=swing(affixed,"WA-009").windup();var plain=swing(unrolled(affixed),"WA-009").windup();
        var removed=swing(null,"WA-009").windup();
        assertEquals(.45,plain,1e-9);
        assertEquals(.45,removed,1e-9);
        assertEquals(.45/(1+affixed.affixes().getFirst().value()/100),rolled,1e-9);
        assertTrue(rolled<plain);
    }
    @Test void wa012SeparatelyChangesActualCooldownAndRemovalRestoresIt(){
        var source=item("WA-012");
        var treated=swing(source,"WA-012");var control=swing(unrolled(source),"WA-012");
        var removed=swing(null,"WA-012");
        assertTrue(treated.cooldown()<control.cooldown());
        assertEquals(control.cooldown(),removed.cooldown(),1e-9);
        assertEquals(control.windup(),treated.windup(),1e-9);
    }
    private record Cast(double windup,double cooldown){}
    private static Cast swing(GearInstance source,String id){
        var h=new Stage09SupportRuntimeTest.Harness("heavy_swing"){
            @Override public Equipment equipment(){return new Equipment(new Item("fixture","LONGSWORD",
                    new ItemPowerDescriptor("fixture",Set.of("RPG_WEAPON_HEAVY"),20d,20d)),null);}
            @Override public Validation familyPrerequisites(Stage04SkillProfile p,CompiledSkillPlan plan){return Validation.pass();}
            @Override public GearAffixRuntime.Effects gearEffects(){return source==null?GearAffixRuntime.Effects.NONE:
                    admitted(source,id).effects();}
            @Override public SkillExecutionResult executeStrike(SkillExecutionContext c){context=c;
                return SkillExecutionResult.committed("SWING",0,0);}
        };
        assertEquals(SkillExecutionResult.Status.PENDING,h.cast().status());
        double seconds=h.execution.activeWindupSeconds(h.actor).orElseThrow();
        assertEquals(100,h.mana);
        assertEquals(SkillExecutionResult.Status.COMMITTED,h.execution.completeWindup(h.actor,h).status());
        assertEquals(91,h.mana);
        return new Cast(seconds,h.context.snapshot().cooldownSeconds());
    }

    @Test void wa106KeepsPaidFriendlyTetherInsideExtendedReachOnly(){
        var source=item("WA-106");
        var treated=tether(source);var control=tether(unrolled(source));
        assertTrue(treated.cast().committed());assertTrue(control.cast().committed());
        treated.advance(.25);control.advance(.25);
        assertEquals(1,treated.coefficients.size());assertEquals(1,control.coefficients.size());
        double extended=18*(1+source.affixes().getFirst().value()/100);
        double moved=18+Math.min(.5,(extended-18)/2);
        treated.targets.set(0,Stage08ConnectionCohortBTest.enemy(1,0,1.35,moved));
        control.targets.set(0,Stage08ConnectionCohortBTest.enemy(1,0,1.35,moved));
        treated.advance(.5);control.advance(.5);
        assertEquals(2,treated.coefficients.size());
        assertEquals(1,control.coefficients.size());
        assertEquals(List.of("TETHER_RANGE_BROKEN"),control.ends);
        treated.targets.clear();treated.advance(.75);
        assertEquals(2,treated.coefficients.size());
    }
    private static Stage13SupportTetherTest.Healing tether(GearInstance source){
        return new Stage13SupportTetherTest.Healing(){
            @Override public GearAffixRuntime.Effects gearEffects(){return new GearAffixRuntime.Effects(
                    Map.of(),0,0,0,0,0,0,admitted(source,"WA-106").effects().snapshot());}
        };
    }

    @Test void wa109ShortensOnlyControlAdmittedDuringActiveChannel(){
        var source=item("WA-109");
        var accepted=admitted(source,"WA-109").effects().snapshot();
        var unrolled=admitted(unrolled(source),"WA-109").effects().snapshot();
        var statuses=new com.inigmasgames.hytalerpg.combat.status.StatusService(
                com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile.loadCanonical(),()->0);
        var type=com.inigmasgames.hytalerpg.combat.status.RpgStatusType.STUN;
        var profile=com.inigmasgames.hytalerpg.combat.status.ControlProfile.NORMAL;
        double changed=statuses.apply(UUID.randomUUID(),type,profile,2,accepted,true).remainingSeconds();
        double control=statuses.apply(UUID.randomUUID(),type,profile,2,unrolled,true).remainingSeconds();
        double idle=statuses.apply(UUID.randomUUID(),type,profile,2,accepted,false).remainingSeconds();
        assertTrue(changed<control);
        assertEquals(control,idle,1e-9);
        assertEquals(5,statuses.apply(UUID.randomUUID(),
                com.inigmasgames.hytalerpg.combat.status.RpgStatusType.SLOW,profile,5,accepted,true).remainingSeconds(),1e-9);
    }

    @Test void wa095PaysActualManaOnAcceptedHitOnly(){recoveryHit("WA-095");}
    @Test void wa097PaysActualManaLeechOnAcceptedHitOnly(){recoveryHit("WA-097");}
    private static void recoveryHit(String id){
        var source=item(id);var accepted=admitted(source,id).effects().snapshot();
        var empty=admitted(unrolled(source),id).effects().snapshot();
        var actor=UUID.randomUUID();var victim=UUID.randomUUID();
        var hit=new com.inigmasgames.hytalerpg.combat.resource.GearRecoveryRuntime.Receipt(
                actor,"world","root","contact",victim,100,50,true,true,false,false,false);
        var treated=recovery();var control=recovery();var cancelled=recovery();
        treated.onAttack(hit,accepted,1000,500,0);
        control.onAttack(hit,empty,1000,500,0);
        cancelled.onAttack(new com.inigmasgames.hytalerpg.combat.resource.GearRecoveryRuntime.Receipt(
                actor,"world","root","contact",victim,100,50,true,true,true,false,false),accepted,1000,500,0);
        var treatedPort=new CreditPort();var controlPort=new CreditPort();var cancelledPort=new CreditPort();
        assertTrue(treated.pay(actor,"world",com.inigmasgames.hytalerpg.combat.resource.GearRecoveryRuntime.Pool.HIT_MANA,
                500,0,treatedPort).amount()>0);
        assertEquals(400,controlPort.mana);
        assertEquals(0,control.pay(actor,"world",com.inigmasgames.hytalerpg.combat.resource.GearRecoveryRuntime.Pool.HIT_MANA,
                500,0,controlPort).amount());
        assertEquals(0,cancelled.pay(actor,"world",com.inigmasgames.hytalerpg.combat.resource.GearRecoveryRuntime.Pool.HIT_MANA,
                500,0,cancelledPort).amount());
        assertTrue(treatedPort.mana>400);
        assertEquals(400,cancelledPort.mana);
    }
    @Test void wa098CreditsKillHealthOnceAndRejectsIneligibleDeath(){
        var source=item("WA-098");var accepted=admitted(source,"WA-098").effects().snapshot();
        var empty=admitted(unrolled(source),"WA-098").effects().snapshot();
        var actor=UUID.randomUUID();var treated=recovery();var control=recovery();var ineligible=recovery();
        treated.onKill(actor,"world","reward",true,accepted,1000,0);
        treated.onKill(actor,"world","reward",true,accepted,1000,0);
        control.onKill(actor,"world","reward",true,empty,1000,0);
        ineligible.onKill(actor,"world","reward",false,accepted,1000,0);
        var treatedPort=new CreditPort();var controlPort=new CreditPort();var ineligiblePort=new CreditPort();
        var pool=com.inigmasgames.hytalerpg.combat.resource.GearRecoveryRuntime.Pool.KILL_HEALTH;
        assertTrue(treated.pay(actor,"world",pool,1000,0,treatedPort).amount()>0);
        assertEquals(0,treated.pay(actor,"world",pool,1000,0,treatedPort).amount());
        assertEquals(0,control.pay(actor,"world",pool,1000,0,controlPort).amount());
        assertEquals(0,ineligible.pay(actor,"world",pool,1000,0,ineligiblePort).amount());
        assertTrue(treatedPort.health>900);
        assertEquals(900,controlPort.health);
    }
    private static com.inigmasgames.hytalerpg.combat.resource.GearRecoveryRuntime recovery(){
        return new com.inigmasgames.hytalerpg.combat.resource.GearRecoveryRuntime();
    }
    private static final class CreditPort implements com.inigmasgames.hytalerpg.combat.resource.NativeResourcePort,
            com.inigmasgames.hytalerpg.combat.resource.GearRecoveryRuntime.Credit {
        double health=900,mana=400;
        public double current(com.inigmasgames.hytalerpg.combat.resource.ResourceType type){return type==com.inigmasgames.hytalerpg.combat.resource.ResourceType.HEALTH?health:mana;}
        public double maximum(com.inigmasgames.hytalerpg.combat.resource.ResourceType type){return type==com.inigmasgames.hytalerpg.combat.resource.ResourceType.HEALTH?1000:500;}
        public void setCurrent(com.inigmasgames.hytalerpg.combat.resource.ResourceType type,double value){
            if(type==com.inigmasgames.hytalerpg.combat.resource.ResourceType.HEALTH)health=value;else mana=value;}
        public double admit(com.inigmasgames.hytalerpg.combat.resource.GearRecoveryRuntime.Pool pool,double requested,double cap){
            return restoreResourceAtMost(pool==com.inigmasgames.hytalerpg.combat.resource.GearRecoveryRuntime.Pool.HIT_MANA
                    ?com.inigmasgames.hytalerpg.combat.resource.ResourceType.MANA
                    :com.inigmasgames.hytalerpg.combat.resource.ResourceType.HEALTH,requested,cap);
        }
    }

    @Test void wa103SeparatelyChangesCommittedCanonicalMinorHeal(){
        healDifference("WA-103");
    }
    @Test void wa104SeparatelyChangesCommittedCanonicalMinorHeal(){
        healDifference("WA-104");
    }
    private static void healDifference(String id){
        var source=item(id);
        var treated=healer(source,id,40,100);var control=healer(unrolled(source),id,40,100);var removed=healer(null,id,40,100);
        assertTrue(treated.cast().committed());assertTrue(control.cast().committed());assertTrue(removed.cast().committed());
        assertTrue(treated.health>control.health,id);
        assertEquals(control.health,removed.health,1e-9);
        assertEquals(control.mana,treated.mana,1e-9);
        assertEquals(1,treated.events.stream().filter("HEAL_RESOLVED"::equals).count());
        var capped=healer(source,id,99,100);assertTrue(capped.cast().committed());assertEquals(100,capped.health,1e-9);
        var unpaid=healer(source,id,40,0);assertFalse(unpaid.cast().committed());assertEquals(40,unpaid.health,1e-9);
    }
    private static Stage09SupportRuntimeTest.Harness healer(GearInstance source,String id,double health,double mana){
        var h=new Stage09SupportRuntimeTest.Harness("minor_heal"){
            @Override public GearAffixRuntime.Effects gearEffects(){return new GearAffixRuntime.Effects(
                    Map.of(),0,0,0,0,0,0,source==null?GearEffectSnapshot.EMPTY:admitted(source,id).effects().snapshot());}
        };
        h.health=health;h.mana=mana;return h;
    }
    private static GearEquipmentResolution.Result admitted(GearInstance source,String id){
        try{
            var resolve=GearEquipmentResolution.class.getDeclaredMethod("resolve",int.class,Map.class,Collection.class,Set.class);
            resolve.setAccessible(true);
            var attributes=new EnumMap<com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute,Integer>(com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.class);
            for(var attribute:com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.values())attributes.put(attribute,500);
            var result=(GearEquipmentResolution.Result)resolve.invoke(null,99,attributes,
                    List.of(new GearEquipmentResolution.Candidate(source,true,true,true)),Set.of(id));
            assertEquals(List.of(source),result.validItems());
            return result;
        }catch(ReflectiveOperationException error){throw new AssertionError("Candidate equipment admission unavailable",error);}
    }
}
