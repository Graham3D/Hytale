package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.execution.CommittedTarget;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.combat.damage.DamageCalculationService;
import com.inigmasgames.hytalerpg.execution.projectile.*;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.gear.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ItemSkillChildExecutionTest {
    private static GearInstance source(String affix){
        var catalog=GearCatalog.load();var definition=catalog.affix(affix);
        var tier=GearAffixTiers.compile(definition).getFirst();
        var roll=new GearInstance.AffixRoll(affix,definition.side(),definition.exclusionGroup(),tier.tier(),12,
                new GearRequirements.Gate(tier.requiredLevel(),Map.of()),definition.name(),definition.name());
        var fixture=com.inigmasgames.hytalerpg.gear.MasterAffixTestEquipment.fixture(affix,false);
        String base=fixture.baseId();
        return GearInstance.authoredQa(catalog.base(base),UUID.randomUUID(),fixture.itemLevel(),1000,
                GearRarity.MAGIC,List.of(roll),BigDecimal.ZERO);
    }
    @Test void canonicalBoltAndHealChildrenHaveRankOneAndNoLearnedOrLinkedPlan(){
        var h=new Stage09SupportRuntimeTest.Harness("emanatism");
        UUID target=UUID.randomUUID();
        for(var spec:new String[][]{{"WA-145","fire_bolt"},{"WA-146","frost_bolt"},{"WA-147","minor_heal"}}){
            var item=source(spec[0]);var unrelated=source("WA-145");
            boolean heal=spec[1].equals("minor_heal");
            var child=new ItemSkillTriggerRuntime.Child(h.world,h.actor,heal?h.actor:target,
                    "root-"+spec[0],spec[1],1,item.identity(),new GearEffectSnapshot(List.of(unrelated,item)));
            var solution=new CommittedTarget(h.world,Vec3.ZERO,Vec3.FORWARD,Vec3.FORWARD,child.target());
            double before=h.mana;
            var context=h.execution.itemChild(child,h,solution);
            assertEquals(1,context.effectiveSkillLevel());
            assertTrue(context.compiledPlan().passiveOrder().isEmpty());
            assertEquals(0,context.snapshot().resourceCost().amount(),1e-9);
            assertEquals(0,context.snapshot().cooldownSeconds(),1e-9);
            assertEquals("ITEM_TRIGGER",context.request().action());
            assertEquals(unrelated.identity(),context.gearSnapshot().items().getFirst().identity());
            assertEquals(item.identity(),context.gearSourceItemId());
            assertEquals(before,h.mana,1e-9);
            if(!heal)assertTrue(context.snapshot().basePower()>0);
        }
    }
    @Test void wrongTargetOrMissingSourceCannotBecomeChild(){
        var h=new Stage09SupportRuntimeTest.Harness("emanatism");var item=source("WA-145");
        var child=new ItemSkillTriggerRuntime.Child(h.world,h.actor,UUID.randomUUID(),"root","fire_bolt",1,
                item.identity(),new GearEffectSnapshot(List.of(item)));
        assertThrows(IllegalArgumentException.class,()->h.execution.itemChild(child,h,
                new CommittedTarget(h.world,Vec3.ZERO,Vec3.FORWARD,Vec3.FORWARD,UUID.randomUUID())));
    }

    private static GearEffectSnapshot admitted(GearInstance item,boolean equipped,boolean intact){
        var result=GearEquipmentResolution.resolve(99,
                com.inigmasgames.hytalerpg.gear.MasterAffixTestEquipment.BASELINE,
                List.of(new GearEquipmentResolution.Candidate(item,equipped,intact,true)));
        if(equipped&&intact)assertEquals(List.of(item),result.validItems(),result.rejected().toString());
        return result.effects().snapshot();
    }
    private static double projectileContact(ItemSkillTriggerRuntime.Child child,Stage09SupportRuntimeTest.Harness h){
        var target=new CommittedTarget(h.world,Vec3.ZERO,new Vec3(0,0,8),Vec3.FORWARD,child.target());
        var context=h.execution.itemChild(child,h,target);
        var projectiles=new RpgProjectileService(new ProjectileLifecycleRegistry());
        var plan=projectiles.buildPlan(context,h.actor,Vec3.ZERO,Vec3.FORWARD,
                context.profile().projectile().configId(),context.profile().projectile().speed(),1);
        var instance=projectiles.onProjectileSpawn(plan);
        assertTrue(projectiles.onEnemyContact(instance,child.target().toString()));
        assertFalse(projectiles.onEnemyContact(instance,child.target().toString()));
        var snapshot=plan.snapshot();
        return h.kernel.damage().calculate(new DamageCalculationService.Request(snapshot.basePower(),
                snapshot.derivedStats().effective(com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.INT),
                snapshot.skillCoefficient(),snapshot.modifiers(),false,0,1)).preMitigationDamage();
    }
    @Test void wa145And146QueuedCanonicalBoltsHitOneProjectileRecipientWithSourceControls(){
        for(String id:List.of("WA-145","WA-146")){
            var item=source(id);var plain=new GearInstance(item.schemaVersion(),UUID.randomUUID(),item.definitionRevision(),
                    item.baseId(),item.baseName(),item.category(),item.sourceEra(),item.itemLevel(),item.rarity(),
                    item.intrinsicThousandths(),item.intrinsicStats(),item.requirements(),List.of(),item.rngVersion(),true);
            var h=new Stage09SupportRuntimeTest.Harness("emanatism");var victim=UUID.randomUUID();
            var trigger=new ItemSkillTriggerRuntime(()->0);
            var queue=new java.util.ArrayList<ItemSkillTriggerRuntime.Child>();
            var rolled=admitted(item,true,true);
            var hit=new ItemSkillTriggerRuntime.DirectHit(h.world,h.actor,item.identity(),victim,
                    "root-"+id,"contact",rolled,true,true,false,false,false,20,1,false);
            trigger.applied(hit,0,queue::add);
            trigger.applied(hit,0,queue::add);
            assertEquals(1,queue.size(),id+" duplicate root");
            assertEquals(id.equals("WA-145")?"fire_bolt":"frost_bolt",queue.getFirst().skill());
            assertEquals(1,queue.getFirst().rank());
            double health=100;health-=projectileContact(queue.getFirst(),h);
            assertTrue(health<100,id+" accepted impact");
            assertEquals(100-projectileContact(queue.getFirst(),h),health,1e-8);
            assertEquals(100,h.mana,1e-9,id+" child has no payment");
            trigger.applied(new ItemSkillTriggerRuntime.DirectHit(h.world,h.actor,item.identity(),victim,
                    "no-proc-"+id,"contact",rolled,true,true,true,false,false,20,1,false),.1,queue::add);
            trigger.applied(new ItemSkillTriggerRuntime.DirectHit(h.world,h.actor,item.identity(),victim,
                    "cooldown-"+id,"contact",rolled,true,true,false,false,false,20,1,false),.1,queue::add);
            assertEquals(1,queue.size(),id+" NoProc and cooldown");
            for(var control:List.of(admitted(plain,true,true),admitted(item,true,false),admitted(item,false,true))){
                new ItemSkillTriggerRuntime(()->0).applied(new ItemSkillTriggerRuntime.DirectHit(
                        h.world,h.actor,item.identity(),victim,"root-"+id,"contact",control,
                        true,true,false,false,false,20,1,false),0,queue::add);
                assertEquals(1,queue.size(),id+" invalid source cannot damage recipient");
            }
        }
    }

    @Test void wa147SuccessfulBlockChildCreditsActualSelfHealthOnceWithCap(){
        var item=source("WA-147");var h=new Stage09SupportRuntimeTest.Harness("emanatism");
        var queue=new java.util.ArrayList<ItemSkillTriggerRuntime.Child>();
        var trigger=new ItemSkillTriggerRuntime(()->0);var rolled=admitted(item,true,true);
        var block=new ItemSkillTriggerRuntime.Block(h.world,h.actor,item.identity(),"block-root","contact",
                rolled,true,false,false);
        trigger.blocked(block,0,queue::add);trigger.blocked(block,0,queue::add);
        assertEquals(1,queue.size());
        var child=queue.getFirst();assertEquals(h.actor,child.target());
        var context=h.execution.itemChild(child,h,
                new CommittedTarget(h.world,Vec3.ZERO,Vec3.ZERO,Vec3.FORWARD,h.actor));
        h.health=90;assertTrue(h.runtime.execute(context,0,h).committed());
        assertEquals(100,h.health,1e-9);assertEquals(100,h.mana,1e-9);
        h.health=90;
        for(var control:List.of(admitted(item,false,true),admitted(item,true,false))){
            trigger.blocked(new ItemSkillTriggerRuntime.Block(h.world,h.actor,item.identity(),UUID.randomUUID().toString(),
                    "contact",control,true,false,false),9,queue::add);
        }
        trigger.blocked(new ItemSkillTriggerRuntime.Block(h.world,h.actor,item.identity(),"failed","contact",
                rolled,false,false,false),9,queue::add);
        assertEquals(1,queue.size());assertEquals(90,h.health,1e-9);
    }
}
