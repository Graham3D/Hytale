package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.combat.damage.DamageCalculationService;
import com.inigmasgames.hytalerpg.combat.power.ItemPowerDescriptor;
import com.inigmasgames.hytalerpg.combat.resource.NativeResourcePort;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.gear.*;
import com.inigmasgames.hytalerpg.progress.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class BorrowedSkillOwnerTest {
    private static GearEffectSnapshot resolve(GearInstance item,boolean equipped,boolean intact){
        var result=GearEquipmentResolution.resolve(99,MasterAffixTestEquipment.BASELINE,
                List.of(new GearEquipmentResolution.Candidate(item,equipped,intact,true)));
        if(equipped&&intact)assertEquals(List.of(item),result.validItems(),result.rejected().toString());
        return result.effects().snapshot();
    }
    @Test void wa144AcceptedBorrowedRequestHitsCanonicalOwnerWithoutLearningAndWithdrawalRejectsSameRequest(){
        var item=MasterAffixTestEquipment.fixture("WA-144",false);
        String skill=item.affixes().getFirst().selector();assertNotNull(skill);
        var positive=resolve(item,true,true);
        var unrolled=resolve(MasterAffixTestEquipment.fixture("WA-144",true),true,true);
        var broken=resolve(item,true,false);
        var bundle=Stage01BTestSupport.bundle();var actor=UUID.randomUUID();
        var loadouts=new RpgLoadoutService(bundle.catalog(),bundle.repository(),bundle.graph(),bundle.compiler(),
                new OwnershipEntitlementPolicy(false),bundle.tracer());
        var kernel=RpgCombatKernel.createProduction();
        var profiles=Stage04SkillProfiles.loadCanonical(bundle.catalog());
        var profile=profiles.require(skill);
        class Port implements SkillExecutionPort,NativeResourcePort {
            GearEffectSnapshot snapshot=GearEffectSnapshot.EMPTY;
            SkillExecutionContext context;
            double mana=1000,stamina=1000,health=0,damage;
            public boolean actorAliveAndUsable(){return true;}
            public Equipment equipment(){
                String kind=profile.allowedMainHandKinds().stream().sorted().findFirst().orElse("STAFF");
                return new Equipment(new Item("fixture",kind,
                        new ItemPowerDescriptor("fixture",Set.of(kind),20d,20d)),null);
            }
            public NativeResourcePort resources(){return this;}
            public GearAffixRuntime.Effects gearEffects(){return new GearAffixRuntime.Effects(
                    Map.of(),0,0,0,0,0,0,snapshot);}
            public Validation familyPrerequisites(Stage04SkillProfile p,CompiledSkillPlan plan){return Validation.pass();}
            public CommittedTarget captureTarget(Stage04SkillProfile p,CompiledSkillPlan plan,SkillExecutionRequest r){
                return new CommittedTarget(UUID.randomUUID(),Vec3.ZERO,Vec3.FORWARD,Vec3.FORWARD,actor);
            }
            public Validation validateRelease(SkillExecutionContext c){return Validation.pass();}
            private SkillExecutionResult apply(SkillExecutionContext c){
                context=c;
                if(c.profile().support()!=null){
                    double amount=com.inigmasgames.hytalerpg.execution.support.SupportMagnitude.healing(c,1,0,1000);
                    health=restoreResourceAtMost(ResourceType.HEALTH,amount,1000);
                }else{
                    double coefficient=c.profile().summon()!=null
                            ?new com.inigmasgames.hytalerpg.execution.summon.SummonRegistry().reserve(c,0).getFirst().coefficient()
                            :c.snapshot().skillCoefficient();
                    damage=kernel.damage().calculate(new DamageCalculationService.Request(
                            Math.max(1,c.snapshot().basePower()),10,Math.max(.01,coefficient),
                            c.snapshot().modifiers(),false,0,1)).preMitigationDamage();
                }
                return SkillExecutionResult.committed("OWNER_APPLIED",1,0);
            }
            public SkillExecutionResult executeStrike(SkillExecutionContext c){return apply(c);}
            public SkillExecutionResult executeMovement(SkillExecutionContext c){return apply(c);}
            public SkillExecutionResult executeReaction(SkillExecutionContext c){return apply(c);}
            public SkillExecutionResult executeProjectile(SkillExecutionContext c){return apply(c);}
            public SkillExecutionResult executeArea(SkillExecutionContext c){return apply(c);}
            public SkillExecutionResult executeConnection(SkillExecutionContext c){return apply(c);}
            public SkillExecutionResult executeSupport(SkillExecutionContext c){return apply(c);}
            public SkillExecutionResult executeSummon(SkillExecutionContext c){return apply(c);}
            public double current(ResourceType type){return type==ResourceType.MANA?mana:type==ResourceType.STAMINA?stamina:health;}
            public double maximum(ResourceType type){return 1000;}
            public void setCurrent(ResourceType type,double value){
                if(type==ResourceType.MANA)mana=value;else if(type==ResourceType.STAMINA)stamina=value;else health=value;
            }
        }
        var port=new Port();
        var execution=new SkillExecutionService(loadouts,profiles,kernel,SkillExecutorRegistry.runtime(),
                new SkillInstanceLifecycle(),bundle.tracer(),()->0);
        var request=new SkillExecutionRequest(actor,SkillSlot.SKILL01,"borrowed-owner",1,"same-request",Vec3.FORWARD);
        assertFalse(loadouts.getPresentationView(actor).state().learnedSkills.contains(skill));
        for(var invalid:List.of(unrolled,broken)){
            loadouts.publishItemSkillAvailability(actor,invalid);
            assertFalse(loadouts.equipSkill(actor,SkillSlot.SKILL01,new SkillId(skill)).success());
            assertEquals(SkillExecutionResult.Status.REJECTED,execution.request(request,port).status());
            assertNull(port.context);
        }
        port.snapshot=positive;loadouts.publishItemSkillAvailability(actor,positive);
        assertEquals(skill,ItemSkillGrants.from(positive).get(item.identity()));
        assertTrue(loadouts.equipSkill(actor,SkillSlot.SKILL01,new SkillId(skill)).success());
        var result=execution.request(request,port);
        if(result.status()==SkillExecutionResult.Status.PENDING)result=execution.completeWindup(actor,port);
        assertTrue(result.committed(),result.toString());
        assertNotNull(port.context);assertEquals(1,port.context.effectiveSkillLevel());
        assertTrue(port.damage>0||port.health>0,"borrowed canonical recipient effect");
        assertFalse(loadouts.getPresentationView(actor).state().learnedSkills.contains(skill));
        execution.terminate(port.context,"FIXTURE_COMPLETE");
        loadouts.publishItemSkillAvailability(actor,broken);
        assertEquals(SkillExecutionResult.Status.REJECTED,execution.request(request,port).status());
        assertFalse(loadouts.getPresentationView(actor).state().learnedSkills.contains(skill));
    }
}
