package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.combat.power.ItemPowerDescriptor;
import com.inigmasgames.hytalerpg.combat.resource.NativeResourcePort;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.combat.damage.DamageCalculationService;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.gear.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** All 13 selectors cross the real learned-loadout and SkillExecutionService dispatch boundary. */
class GearSkillRankExecutionTest {
    private static final Map<String,String> CASES=Map.ofEntries(
            Map.entry("WA-121","minor_heal"),Map.entry("WA-122","minor_heal"),
            Map.entry("WA-123","wind_cutter"),Map.entry("WA-124","frost_bolt"),
            Map.entry("WA-125","fire_bolt"),Map.entry("WA-126","stone_bolt"),
            Map.entry("WA-127","lightning_arrow"),Map.entry("WA-128","void_bolt"),
            Map.entry("WA-129","quick_slash"),Map.entry("WA-130","minor_heal"),
            Map.entry("WA-131","wolf_summon"),Map.entry("WA-132","quick_slash"),
            Map.entry("WA-133","fire_bolt"));

    @Test void everyRankFamilyChangesActualLearnedExecutionAndProjectionOnlyWhileValid(){
        var profiles=Stage04SkillProfiles.loadCanonical(com.inigmasgames.hytalerpg.content.RpgCatalog.loadCanonical());
        for(var entry:CASES.entrySet()){
            String id=entry.getKey(),skill=entry.getValue();
            UUID actor=UUID.randomUUID(),world=UUID.randomUUID();
            var control=new Harness(skill,GearEffectSnapshot.EMPTY,actor,world);
            var positive=new Harness(skill,admitted(item(id,false)),actor,world);
            var negative=new Harness(skill,admitted(item(id,true)),actor,world);
            assertEquals(1,GearSkillRanks.bonus(positive.gear,profiles.require(skill)),id+" admitted selector");
            int base=positive.bundle.service().baseSkillRank(positive.actor,skill);
            assertEquals(base,control.castRank(),id+" control");
            assertEquals(base+1,positive.castRank(),id+" positive");
            assertEquals(base,negative.castRank(),id+" ineligible rarity or selector");
            assertTrue(positive.applied>control.applied,id+" canonical recipient effect");
            assertEquals(control.applied,negative.applied,1e-8,id+" invalid source recipient control");
            assertEquals(control.applied*1.02,positive.applied,1e-7,id+" one rank mastery at owner");
            assertEquals(base,positive.bundle.service().baseSkillRank(positive.actor,skill),id+" saved progression");
            var projected=GearSkillRanks.projectLearned(positive.gear,Map.of(skill,base),profiles);
            assertEquals(base+1,projected.get(skill).effective(),id+" UI selector");
            assertEquals(base,projected.get(skill).base(),id+" base projection");
            assertTrue(GearSkillRanks.projectLearned(positive.gear,Map.of(),profiles).isEmpty(),id+" unlearned");
            assertEquals(base,GearSkillRanks.projectLearned(GearEffectSnapshot.EMPTY,Map.of(skill,base),profiles)
                    .get(skill).effective(),id+" unequipped");
            var graph=positive.bundle.graph();
            var loadouts=new com.inigmasgames.hytalerpg.progress.RpgLoadoutService(positive.bundle.catalog(),
                    new Stage01BTestSupport.InMemoryRepository(),graph,positive.bundle.compiler(),
                    new com.inigmasgames.hytalerpg.progress.OwnershipEntitlementPolicy(false),positive.bundle.tracer());
            UUID unlearned=UUID.randomUUID();
            assertFalse(loadouts.equipSkill(unlearned,SkillSlot.SKILL01,new SkillId(skill)).success(),id);
            var unlearnedExecution=new SkillExecutionService(loadouts,profiles,RpgCombatKernel.createProduction(),
                    SkillExecutorRegistry.runtime(),new SkillInstanceLifecycle(),positive.bundle.tracer());
            assertEquals(SkillExecutionResult.Status.REJECTED,unlearnedExecution.request(
                    new SkillExecutionRequest(unlearned,SkillSlot.SKILL01,"rank-unlearned",1,"unlearned-"+id,Vec3.FORWARD),
                    positive).status(),id);
        }
    }
    private static GearEffectSnapshot admitted(GearInstance item){
        var resolved=GearEquipmentResolution.resolve(99,
                com.inigmasgames.hytalerpg.gear.MasterAffixTestEquipment.BASELINE,
                List.of(new GearEquipmentResolution.Candidate(item,true,true,true)));
        assertEquals(List.of(item),resolved.validItems(),resolved.rejected().toString());
        return resolved.effects().snapshot();
    }

    private static GearInstance item(String id,boolean negative){
        var catalog=GearCatalog.load();var affix=catalog.affix(id);
        var fixture=com.inigmasgames.hytalerpg.gear.MasterAffixTestEquipment.fixture(id,false);
        var base=catalog.base(id.equals("WA-122")?"gm.staff_prismatic.h":fixture.baseId());
        var tier=GearAffixTiers.compile(affix).getFirst();
        var roll=new GearInstance.AffixRoll(id,affix.side(),affix.exclusionGroup(),tier.tier(),1,
                new GearRequirements.Gate(tier.requiredLevel(),Map.of(affix.requirementAttribute(base),
                        affix.attributeFloor(0,tier.minimumItemLevel()))),affix.name(),affix.name(),
                id.equals("WA-122")?(negative?"wind_cutter":"minor_heal"):null);
        GearRarity rarity=id.equals("WA-121")?(negative?GearRarity.RARE:GearRarity.VERY_RARE):
                id.equals("WA-122")?GearRarity.MAGIC:negative?GearRarity.MAGIC:GearRarity.RARE;
        return GearInstance.authoredQa(base,UUID.randomUUID(),id.equals("WA-122")?99:fixture.itemLevel(),1000,rarity,List.of(roll),BigDecimal.ZERO);
    }

    private static final class Harness implements SkillExecutionPort,NativeResourcePort {
        final UUID actor,world;
        final Stage01BTestSupport.Bundle bundle=Stage01BTestSupport.bundle();
        final Stage04SkillProfiles profiles=Stage04SkillProfiles.loadCanonical(bundle.catalog());
        final RpgCombatKernel kernel=RpgCombatKernel.createProduction();
        final SkillExecutionService service;
        final GearEffectSnapshot gear;
        final String skill;
        SkillExecutionContext dispatched;
        double applied;
        double mana=1000,stamina=1000;
        long clock;
        Harness(String skill,GearEffectSnapshot gear,UUID actor,UUID world){
            this.skill=skill;this.gear=gear;this.actor=actor;this.world=world;
            assertTrue(bundle.service().equipSkill(actor,SkillSlot.SKILL01,new SkillId(skill)).success(),skill);
            service=new SkillExecutionService(bundle.service(),profiles,kernel,
                    SkillExecutorRegistry.runtime(),new SkillInstanceLifecycle(),bundle.tracer(),()->clock);
        }
        int castRank(){
            var result=service.request(new SkillExecutionRequest(actor,SkillSlot.SKILL01,"rank-test",1,
                    "rank-"+skill,Vec3.FORWARD),this);
            if(result.status()==SkillExecutionResult.Status.PENDING){clock=20_000_000_000L;service.tickScheduled(actor,this);}
            assertNotNull(dispatched,skill+" result="+result);
            return dispatched.effectiveSkillLevel();
        }
        public GearAffixRuntime.Effects gearEffects(){return new GearAffixRuntime.Effects(Map.of(),0,0,0,0,0,0,gear);}
        public int itemGrantedSkillLevels(Stage04SkillProfile profile,Equipment equipment){return GearSkillRanks.bonus(gear,profile);}
        public boolean actorAliveAndUsable(){return true;}
        public Equipment equipment(){
            var profile=profiles.require(skill);
            String kind=profile.allowedMainHandKinds().stream().sorted().findFirst().orElse("STAFF");
            return new Equipment(new Item("fixture",kind,new ItemPowerDescriptor("fixture",Set.of(kind),20d,20d)),null);
        }
        public NativeResourcePort resources(){return this;}
        public Validation familyPrerequisites(Stage04SkillProfile profile,CompiledSkillPlan plan){return Validation.pass();}
        public CommittedTarget captureTarget(Stage04SkillProfile profile,CompiledSkillPlan plan,SkillExecutionRequest request){
            return new CommittedTarget(world,Vec3.ZERO,Vec3.FORWARD,Vec3.FORWARD,actor);
        }
        public Validation validateRelease(SkillExecutionContext context){return Validation.pass();}
        private SkillExecutionResult done(SkillExecutionContext context){
            dispatched=context;
            if(context.profile().support()!=null){
                double requested=com.inigmasgames.hytalerpg.execution.support.SupportMagnitude.healing(context,1,0,1000);
                applied=restoreResourceAtMost(ResourceType.HEALTH,requested,1000);
            }else if(context.profile().summon()!=null){
                var lease=new com.inigmasgames.hytalerpg.execution.summon.SummonRegistry()
                        .reserve(context,0).getFirst();
                applied=damage(context,lease.coefficient());
            }else applied=damage(context,context.snapshot().skillCoefficient());
            return SkillExecutionResult.committed("RANK_EXECUTED",1,0);
        }
        private double damage(SkillExecutionContext context,double coefficient){
            return kernel.damage().calculate(new DamageCalculationService.Request(
                    Math.max(1,context.snapshot().basePower()),10,Math.max(.01,coefficient),
                    context.snapshot().modifiers(),false,0,1)).preMitigationDamage();
        }
        public SkillExecutionResult executeStrike(SkillExecutionContext c){return done(c);}
        public SkillExecutionResult executeMovement(SkillExecutionContext c){return done(c);}
        public SkillExecutionResult executeReaction(SkillExecutionContext c){return done(c);}
        public SkillExecutionResult executeProjectile(SkillExecutionContext c){return done(c);}
        public SkillExecutionResult executeArea(SkillExecutionContext c){return done(c);}
        public SkillExecutionResult executeConnection(SkillExecutionContext c){return done(c);}
        public SkillExecutionResult executeSupport(SkillExecutionContext c){return done(c);}
        public SkillExecutionResult executeSummon(SkillExecutionContext c){return done(c);}
        public double current(ResourceType type){return type==ResourceType.STAMINA?stamina:type==ResourceType.MANA?mana:applied;}
        public double maximum(ResourceType type){return 1000;}
        public void setCurrent(ResourceType type,double value){if(type==ResourceType.STAMINA)stamina=value;else if(type==ResourceType.MANA)mana=value;else applied=value;}
    }
}
