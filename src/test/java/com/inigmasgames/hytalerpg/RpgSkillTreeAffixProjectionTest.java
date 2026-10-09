package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.combat.power.ItemPowerDescriptor;
import com.inigmasgames.hytalerpg.combat.resource.NativeResourcePort;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.gear.*;
import com.inigmasgames.hytalerpg.progress.RpgPlayerState;
import com.inigmasgames.hytalerpg.progress.RpgLoadoutService;
import com.inigmasgames.hytalerpg.progress.OwnershipEntitlementPolicy;
import com.inigmasgames.hytalerpg.ui.skilltree.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Skill-tree rank rows read the same learned/temporary loadout and selector as a paid cast. */
class RpgSkillTreeAffixProjectionTest {
    private static GearInstance item(String id,double value,String selector,GearRarity rarity) {
        var catalog=GearCatalog.load();var affix=catalog.affix(id);
        var roll=new GearInstance.AffixRoll(id,affix.side(),affix.exclusionGroup(),1,value,
                new GearRequirements.Gate(1,Map.of()),affix.name(),affix.name(),selector);
        return GearInstance.authoredQa(catalog.base("gm.sword_mithril.h"),UUID.randomUUID(),95,1000,
                rarity,List.of(roll),BigDecimal.ZERO);
    }
    private static String rank(RpgSkillTreeProjectionService projection,UUID actor,String skill) {
        return projection.project(actor,StaticSkillTreeViewModel.Tab.SKILLS,"","","SWORD",null,skill)
                .details().rows().stream().filter(row->row.label().equals("RANK"))
                .findFirst().orElseThrow().value();
    }
    private static final class Port implements SkillExecutionPort {
        GearAffixRuntime.Effects effects=GearAffixRuntime.Effects.NONE;
        SkillExecutionContext committed;
        final NativeResourcePort resources=new NativeResourcePort() {
            double stamina=100;
            public double current(ResourceType type){return type==ResourceType.STAMINA?stamina:100;}
            public double maximum(ResourceType type){return 100;}
            public void setCurrent(ResourceType type,double value){if(type==ResourceType.STAMINA)stamina=value;}
        };
        public boolean actorAliveAndUsable(){return true;}
        public Equipment equipment(){return new Equipment(new Item("test:sword","SWORD",
                new ItemPowerDescriptor("test:sword",Set.of("SWORD"),10d,null)),null);}
        public NativeResourcePort resources(){return resources;}
        public Validation familyPrerequisites(Stage04SkillProfile profile,CompiledSkillPlan plan){return Validation.pass();}
        public GearAffixRuntime.Effects gearEffects(){return effects;}
        public int itemGrantedSkillLevels(Stage04SkillProfile profile,Equipment equipment) {
            return GearSkillRanks.bonus(effects.snapshot(),profile);
        }
        public SkillExecutionResult executeStrike(SkillExecutionContext context){
            committed=context;return SkillExecutionResult.committed("DISPATCHED",1,0);
        }
        public SkillExecutionResult executeMovement(SkillExecutionContext context){throw new AssertionError();}
        public SkillExecutionResult executeReaction(SkillExecutionContext context){throw new AssertionError();}
        public SkillExecutionResult executeProjectile(SkillExecutionContext context){throw new AssertionError();}
    }
    @Test void learnedOnlyAllSkillsRespectsBaseCapAndMatchesPaidExecutionThenUnequip() {
        var bundle=Stage01BTestSupport.bundle();UUID actor=UUID.randomUUID();
        RpgPlayerState saved=RpgPlayerState.create(actor);
        saved.learnedSkills.addAll(List.of("quick_slash","heavy_swing"));
        saved.gearEconomy=new GearEconomyProgress(Map.of(),Map.of("quick_slash",20,"heavy_swing",5),Map.of());
        bundle.repository().save(saved);
        try(var loadouts=bundle.service()) {
            assertTrue(loadouts.equipSkill(actor,SkillSlot.SKILL01,new SkillId("quick_slash")).success());
            var projection=new RpgSkillTreeProjectionService(bundle.catalog(),loadouts,new StaticSkillTreeLayout(),true);
            var port=new Port();
            projection.configureGearEffects(ignored->port.effects);
            var execution=new SkillExecutionService(loadouts,Stage04SkillProfiles.loadCanonical(bundle.catalog()),
                    RpgCombatKernel.createProduction(),SkillExecutorRegistry.stage04(),new SkillInstanceLifecycle(),ignored->{});
            assertEquals("20 base / 20 effective",rank(projection,actor,"quick_slash"));
            assertEquals("1 base / 1 effective",rank(projection,actor,"fire_bolt"));
            var gear=new GearEffectSnapshot(List.of(item("WA-121",2,null,GearRarity.LEGENDARY)));
            port.effects=new GearAffixRuntime.Effects(Map.of(),0,0,0,0,0,2,gear);
            assertEquals("20 base / 22 effective",rank(projection,actor,"quick_slash"));
            assertEquals("5 base / 7 effective",rank(projection,actor,"heavy_swing"));
            assertEquals("1 base / 1 effective",rank(projection,actor,"fire_bolt"));
            var request=new SkillExecutionRequest(actor,SkillSlot.SKILL01,"Ability1",41,"rank-ui-cast",Vec3.FORWARD);
            assertEquals(SkillExecutionResult.Status.COMMITTED,execution.request(request,port).status());
            assertEquals(22,port.committed.effectiveSkillLevel());
            execution.terminate(port.committed,"FIXTURE_COMPLETE");
            assertEquals(20,bundle.repository().states.get(actor).gearEconomy.baseRanks().get("quick_slash"));
            port.effects=GearAffixRuntime.Effects.NONE;
            assertEquals("20 base / 20 effective",rank(projection,actor,"quick_slash"));
            assertEquals("5 base / 5 effective",rank(projection,actor,"heavy_swing"));
        }
    }
    @Test void borrowedArtsShowsTemporaryRankOneWithoutLearningOrPersistingIt() {
        var bundle=Stage01BTestSupport.bundle();UUID actor=UUID.randomUUID();
        bundle.service().close();
        var borrowed=new GearEffectSnapshot(List.of(item("WA-144",1,"quick_slash",GearRarity.MAGIC)));
        try(var loadouts=new RpgLoadoutService(bundle.catalog(),bundle.repository(),bundle.graph(),
                bundle.compiler(),new OwnershipEntitlementPolicy(false),bundle.tracer())) {
            var projection=new RpgSkillTreeProjectionService(bundle.catalog(),loadouts,new StaticSkillTreeLayout(),true);
            assertFalse(loadouts.learnedSkill(actor,"quick_slash"));
            loadouts.publishItemSkillAvailability(actor,borrowed);
            assertEquals("1 base / 1 effective",rank(projection,actor,"quick_slash"));
            assertTrue(projection.project(actor,StaticSkillTreeViewModel.Tab.SKILLS,"","","SWORD",null,"quick_slash")
                    .details().description().contains("Temporary access"));
            assertTrue(loadouts.equipSkill(actor,SkillSlot.SKILL01,new SkillId("quick_slash")).success());
            assertFalse(loadouts.learnedSkill(actor,"quick_slash"));
            loadouts.publishItemSkillAvailability(actor,GearEffectSnapshot.EMPTY);
            assertTrue(loadouts.getPresentationView(actor).state().skill(SkillSlot.SKILL01).isEmpty());
            assertFalse(loadouts.skillAvailable(actor,new SkillId("quick_slash")));
            assertFalse(bundle.repository().states.get(actor).learnedSkills.contains("quick_slash"));
        }
    }
}
