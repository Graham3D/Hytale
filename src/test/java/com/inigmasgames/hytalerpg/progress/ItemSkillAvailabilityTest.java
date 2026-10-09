package com.inigmasgames.hytalerpg.progress;

import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.domain.SkillId;
import com.inigmasgames.hytalerpg.domain.SkillSlot;
import com.inigmasgames.hytalerpg.gear.*;
import com.inigmasgames.hytalerpg.links.*;
import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.combat.power.ItemPowerDescriptor;
import com.inigmasgames.hytalerpg.combat.resource.NativeResourcePort;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ItemSkillAvailabilityTest {
    private static final class Repository implements RpgPlayerStateRepository {
        final Map<UUID,RpgPlayerState> saved=new java.util.HashMap<>();
        public LoadResult load(UUID player){var state=saved.getOrDefault(player,RpgPlayerState.create(player));
            return new LoadResult(state.copy(),saved.containsKey(player),false,state.schemaVersion,List.of());}
        public void save(RpgPlayerState state){saved.put(state.playerUuid(),state.copy());}
    }
    private static RpgLoadoutService service(Repository repo){
        var catalog=RpgCatalog.loadCanonical();var compatible=new CompatibilityService();
        var graph=new RpgLinkGraphService(catalog,compatible);
        return new RpgLoadoutService(catalog,repo,graph,new LinkCompiler(catalog,graph,compatible),
                new OwnershipEntitlementPolicy(false),ignored->{});
    }
    private static GearInstance grant(UUID item,String selector){
        var catalog=GearCatalog.load();var base=catalog.base("gm.sword_mithril.h");var affix=catalog.affix("WA-144");
        var tier=GearAffixTiers.compile(affix).getFirst();
        var roll=new GearInstance.AffixRoll(affix.id(),affix.side(),affix.exclusionGroup(),tier.tier(),1,
                new GearRequirements.Gate(tier.requiredLevel(),Map.of()),"borrowed",affix.name(),selector);
        return GearInstance.authoredQa(base,item,95,1000,GearRarity.MAGIC,List.of(roll),BigDecimal.ZERO);
    }
    @Test void normalSlotCompilesAtBaseRankOneUntilLastSourceLeaves(){
        var repo=new Repository();UUID actor=UUID.randomUUID();var skill=new SkillId("quick_slash");
        var first=grant(UUID.randomUUID(),skill.value());var second=grant(UUID.randomUUID(),skill.value());
        var saved=RpgPlayerState.create(actor);saved.learnedSkills.add("heavy_swing");repo.save(saved);
        try(var loadouts=service(repo)){
            assertTrue(loadouts.equipSkill(actor,SkillSlot.SKILL02,new SkillId("heavy_swing")).success());
            assertFalse(loadouts.equipSkill(actor,SkillSlot.SKILL01,skill).success());
            loadouts.publishItemSkillAvailability(actor,new GearEffectSnapshot(List.of(first,second)));
            assertTrue(loadouts.equipSkill(actor,SkillSlot.SKILL01,skill).success());
            assertEquals(1,loadouts.baseSkillRank(actor,skill.value()));
            assertFalse(loadouts.learnedSkill(actor,skill.value()));
            assertTrue(loadouts.learnedSkill(actor,"heavy_swing"));
            assertEquals(skill,loadouts.getPresentationView(actor).state().skill(SkillSlot.SKILL01).orElseThrow());
            assertNotNull(loadouts.getPresentationView(actor).plans().get(SkillSlot.SKILL01));
            assertFalse(loadouts.getPresentationView(actor).state().learnedSkills.contains(skill.value()));
            loadouts.publishItemSkillAvailability(actor,new GearEffectSnapshot(List.of(second)));
            assertEquals(skill,loadouts.getPresentationView(actor).state().skill(SkillSlot.SKILL01).orElseThrow());
            loadouts.publishItemSkillAvailability(actor,GearEffectSnapshot.EMPTY);
            assertTrue(loadouts.getPresentationView(actor).state().skill(SkillSlot.SKILL01).isEmpty());
            assertEquals("heavy_swing",loadouts.getPresentationView(actor).state().skill(SkillSlot.SKILL02).orElseThrow().value());
            assertFalse(loadouts.skillAvailable(actor,skill));
            assertFalse(repo.saved.get(actor).learnedSkills.contains(skill.value()));
        }
    }
    @Test void reconnectCannotExecuteStaleTemporaryAssignmentBeforeEquipmentPublication(){
        var repo=new Repository();UUID actor=UUID.randomUUID();var item=grant(UUID.randomUUID(),"quick_slash");
        try(var first=service(repo)){
            first.publishItemSkillAvailability(actor,new GearEffectSnapshot(List.of(item)));
            assertTrue(first.equipSkill(actor,SkillSlot.SKILL01,new SkillId("quick_slash")).success());
        }
        try(var recovered=service(repo)){
            assertNull(recovered.getPresentationView(actor).plans().get(SkillSlot.SKILL01));
            recovered.publishItemSkillAvailability(actor,GearEffectSnapshot.EMPTY);
            assertTrue(recovered.getPresentationView(actor).state().skill(SkillSlot.SKILL01).isEmpty());
        }
    }
    @Test void invalidOrAbsentSelectorCannotGrantAvailability(){
        assertThrows(IllegalStateException.class,()->ItemSkillGrants.from(new GearEffectSnapshot(List.of(grant(UUID.randomUUID(),"missing_skill")))));
        assertTrue(ItemSkillGrants.from(GearEffectSnapshot.EMPTY).isEmpty());
    }
    @Test void borrowedOrdinarySlotUsesCanonicalPaidExecutionAndStopsAfterWithdrawal(){
        var repo=new Repository();UUID actor=UUID.randomUUID();var item=grant(UUID.randomUUID(),"quick_slash");
        try(var loadouts=service(repo)){
            loadouts.publishItemSkillAvailability(actor,new GearEffectSnapshot(List.of(item)));
            assertTrue(loadouts.equipSkill(actor,SkillSlot.SKILL01,new SkillId("quick_slash")).success());
            var catalog=RpgCatalog.loadCanonical();
            var execution=new SkillExecutionService(loadouts,Stage04SkillProfiles.loadCanonical(catalog),
                    RpgCombatKernel.createProduction(),SkillExecutorRegistry.stage04(),
                    new SkillInstanceLifecycle(),ignored->{});
            class Resources implements NativeResourcePort {
                double stamina=100;
                public double current(ResourceType type){return type==ResourceType.STAMINA?stamina:100;}
                public double maximum(ResourceType type){return 100;}
                public void setCurrent(ResourceType type,double value){if(type==ResourceType.STAMINA)stamina=value;}
            }
            var resources=new Resources();
            var weapon=new SkillExecutionPort.Item("test:sword","SWORD",
                    new ItemPowerDescriptor("test:sword",java.util.Set.of("SWORD"),10d,null));
            class Port implements SkillExecutionPort {
                SkillExecutionContext committed;
                public boolean actorAliveAndUsable(){return true;}
                public Equipment equipment(){return new Equipment(weapon,null);}
                public NativeResourcePort resources(){return resources;}
                public Validation familyPrerequisites(Stage04SkillProfile profile,com.inigmasgames.hytalerpg.domain.CompiledSkillPlan plan){return Validation.pass();}
                public SkillExecutionResult executeStrike(SkillExecutionContext context){committed=context;return SkillExecutionResult.committed("DISPATCHED",1,0);}
                public SkillExecutionResult executeMovement(SkillExecutionContext context){throw new AssertionError();}
                public SkillExecutionResult executeReaction(SkillExecutionContext context){throw new AssertionError();}
                public SkillExecutionResult executeProjectile(SkillExecutionContext context){throw new AssertionError();}
            }
            var port=new Port();
            var request=new SkillExecutionRequest(actor,SkillSlot.SKILL01,"Ability2",41,"borrowed-cast",Vec3.FORWARD);
            assertEquals(SkillExecutionResult.Status.COMMITTED,execution.request(request,port).status());
            assertEquals(1,port.committed.effectiveSkillLevel());
            assertTrue(resources.stamina<100);
            execution.terminate(port.committed,"FIXTURE_COMPLETE");
            loadouts.publishItemSkillAvailability(actor,GearEffectSnapshot.EMPTY);
            var after=execution.request(request,port);
            assertEquals(SkillExecutionResult.Status.REJECTED,after.status());
            assertEquals("EMPTY_SLOT",after.code());
        }
    }
}
