package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.progress.ProgressionMath;
import com.inigmasgames.hytalerpg.progress.FileEncounterStore;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyQaBirthPlannerTest {
    private final EnemyBalance balance=EnemyBalance.canonical();
    private final EnemyBirthPlanner planner=new EnemyBirthPlanner(balance,EnemyAffixRegistry.canonical(),
            EnemyAffinityRegistry.canonical(),EnemyNamePools.canonical(),EnemyVisualVariants.canonical());
    private static UUID id(String key){return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));}
    private EnemyBirthPlanner.Candidate candidate(int index){return candidate(index,"Larva_Void");}
    private EnemyBirthPlanner.Candidate candidate(int index,String role){return candidate(index,role,DifficultyId.NORMAL);}
    private EnemyBirthPlanner.Candidate candidate(int index,String role,DifficultyId mode){
        var entity=id("qa/entity/"+index);
        var source=new EnemyDescriptor(1,1,balance.revision(),"qa-binding",id("qa/world"),id("qa/encounter"),1,
                entity,entity,"qa-command/test",EnemyRewardContext.Origin.QA,role,role,50,
                mode,ProgressionMath.Rank.COMMON,EnemyRarity.NORMAL,EnemyDescriptor.PackRole.NONE,
                null,null,"qa-profile","Empty",null,"Larva","qa-seed",null,List.of(),List.of(),Set.of(),List.of(),
                "Template_Predator",balance.rewards(EnemyRarity.NORMAL,EnemyRewardContext.Origin.QA,false,0),
                "a".repeat(64),null);
        var binding=new EnemyAffixSelection.Binding("qa-binding",EnumSet.allOf(EnemyAffixRegistry.Capability.class),
                0,true,false,false,0,false);
        return new EnemyBirthPlanner.Candidate(source,binding,0);
    }
    private EnemyBirthPlanner.Request request(int members){
        var roster=new ArrayList<EnemyBirthPlanner.Candidate>();
        for(int i=0;i<members;i++)roster.add(candidate(i));
        return new EnemyBirthPlanner.Request("qa-seed",id("qa/pack"),Vec3.ZERO,"qa-command/test",roster,
                List.of(),true,0,true);
    }
    private EnemyBirthPlanner.Request request(int members,String role){
        return request(members,role,DifficultyId.NORMAL);
    }
    private EnemyBirthPlanner.Request request(int members,String role,DifficultyId mode){
        var roster=new ArrayList<EnemyBirthPlanner.Candidate>();
        for(int i=0;i<members;i++)roster.add(candidate(i,role,mode));
        return new EnemyBirthPlanner.Request("qa-seed",id("qa/pack"),Vec3.ZERO,"qa-command/test",roster,
                List.of(),true,0,true);
    }
    @Test void legacyDurableQaPackStillRebindsButCannotFillProductionCapacity(){
        var qa=planner.planQa(request(3),EnemyQaSpawnRequest.parse("Larva_Void","unique",List.of("stoneskin")));
        var capacity=new EnemyPackCapacity(balance);
        var gate=new EnemyWorldAdmission(world->CompletableFuture.completedFuture(
                new FileEncounterStore.EnemyWorldInventory(List.of(qa),List.of(qa.pack()))),capacity);
        gate.begin(qa.world()).toCompletableFuture().join();
        assertEquals(0,capacity.count(qa.world()));
        assertFalse(gate.admits(qa.world()));
        assertThrows(IllegalStateException.class,()->gate.rebindComplete(qa.world(),List.of()));
        gate.rebindComplete(qa.world(),List.of(qa.encounter()));
        assertTrue(gate.admits(qa.world()));
        assertEquals(0,capacity.count(qa.world()));
    }
    @Test void trorkUniqueLeaderAndMinionProjectTheirOwnAndInheritedAffixes(){
        var birth=planner.planQa(request(3,"Trork_Warrior",DifficultyId.HELL),EnemyQaSpawnRequest.parse("Trork_Warrior","unique","hell",
                List.of("extrastrong","frenzied","armorbreaker")));
        var definitions=EnemyAffixRegistry.canonical();
        var leader=birth.actors().getFirst();
        assertEquals(EnemyDescriptor.PackRole.LEADER,leader.packRole());
        assertEquals(Set.of("ME-002","ME-019","ME-025"),leader.ownAffixes().stream()
                .map(EnemyDescriptor.AffixInstance::affixId).collect(java.util.stream.Collectors.toSet()));
        var leaderDisplay=EnemyDisplayDto.project(leader,definitions,
                EnemyAffixSnapshot.resolve(leader,balance,birth.pack(),0,true,false),birth.pack(),0,
                "Trork Warrior","Trork Warrior",Set.of(),false,0);
        assertEquals("Unique",leaderDisplay.rarityLabel());
        assertEquals(Set.of("ME-002","ME-019","ME-025"),leaderDisplay.ownAffixTags().stream()
                .map(EnemyDisplayDto.EnemyTag::sourceId).collect(java.util.stream.Collectors.toSet()));
        var minion=birth.actors().get(1);
        assertEquals(EnemyDescriptor.PackRole.MINION,minion.packRole());
        assertTrue(minion.ownAffixes().isEmpty());
        var minionDisplay=EnemyDisplayDto.project(minion,definitions,
                EnemyAffixSnapshot.resolve(minion,balance,birth.pack(),0,true,false),birth.pack(),0,
                "Trork Warrior","Trork Warrior",Set.of(),false,0);
        assertEquals("Minion",minionDisplay.packRoleLabel());
        assertTrue(minionDisplay.ownAffixTags().isEmpty());
        assertEquals(List.of("Extra Strong (Inherited)"),minionDisplay.inheritedEffectTags().stream()
                .map(EnemyDisplayDto.EnemyTag::fallbackText).toList());
    }
    @Test void frostSuperUniqueQaPlanKeepsRealAffixOwnersAndZeroEconomy(){
        var frost=EnemyNativeBindings.load().role("Golem_Crystal_Frost").orElseThrow();
        var roster=new ArrayList<EnemyBirthPlanner.Candidate>();
        for(int i=0;i<3;i++){
            var baseline=candidate(i,"Golem_Crystal_Frost").nativeBaseline();
            // This fixture shares the live manifest's capabilities and exact action affix set.
            var binding=new EnemyAffixSelection.Binding("qa-binding",frost.capabilities(),0,true,false,false,
                    0,false,frost.actions().getFirst().supportedAffixIds());
            roster.add(new EnemyBirthPlanner.Candidate(baseline,binding,0));
        }
        var request=new EnemyBirthPlanner.Request("frost-qa",id("qa/pack"),Vec3.ZERO,"qa-command/frost",
                roster,List.of(),true,0,true);
        var qa=EnemyQaSpawnRequest.parse("Golem_Crystal_Frost","superunique",
                List.of("coldenchanted","magicresistant","bulwark"));
        var birth=planner.planQa(request,qa);
        var leader=birth.actors().getFirst();
        assertEquals(EnemyRarity.SUPER_UNIQUE,leader.enemyRarity());
        assertEquals(List.of("ME-006","ME-003","ME-027"),leader.ownAffixes().stream()
                .map(EnemyDescriptor.AffixInstance::affixId).toList());
        assertTrue(birth.actors().stream().allMatch(actor->actor.spawnOrigin()==EnemyRewardContext.Origin.QA
                &&!actor.immutableRewardContext().economic()));
    }
    @Test void authoredTrorkSuperUniqueKeepsItsFixedCardsAndFourGuards(){
        var qa=EnemyQaSpawnRequest.parse("Trork_Warrior","superunique","normal",List.of());
        var birth=planner.planQa(request(qa.offeredMembers(),"Trork_Warrior"),qa);
        assertEquals(List.of("ME-005","ME-024"),birth.actors().getFirst().ownAffixes().stream()
                .map(EnemyDescriptor.AffixInstance::affixId).toList());
        assertEquals(5,birth.actors().size());
        assertEquals(4,birth.pack().guardIds().size());
    }
    @Test void eraCountRejectsLegacyNineAffixQaBirth(){
        var qa=EnemyQaSpawnRequest.parse("Larva_Void","unique",List.of("extrafast","extrastrong","stoneskin",
                "fireenchanted","manaburn","knockback","vampiric","reflective","bulwark"));
        assertEquals("Normal Unique requires 1 affixes; supplied 9",assertThrows(IllegalArgumentException.class,
                ()->planner.planQa(request(3),qa)).getMessage());
    }
    @Test void packboundHordeSealsFourRealGuards(){
        var birth=planner.planQa(request(5,"Larva_Void",DifficultyId.NIGHTMARE),EnemyQaSpawnRequest.parse("Larva_Void","unique","nightmare",
                List.of("horde","packbound")));
        assertEquals(5,birth.actors().size());
        assertEquals(4,birth.pack().guardIds().size());
        assertTrue(birth.actors().stream().skip(1).allMatch(actor->actor.packRole()==EnemyDescriptor.PackRole.MINION));
    }
    @Test void championAndAdHocSuperUniqueKeepTheirRequestedFamilies(){
        var champion=planner.planQa(request(1),EnemyQaSpawnRequest.parse("Larva_Void","champion",
                List.of("stoneskin")));
        assertEquals(EnemyRarity.CHAMPION,champion.actors().getFirst().enemyRarity());
        assertEquals(1,champion.actors().getFirst().ownAffixes().size());
        assertNull(champion.pack().leaderId());
        var superUnique=planner.planQa(request(3),EnemyQaSpawnRequest.parse("Larva_Void","superunique",
                List.of("stoneskin","reflective","bulwark")));
        assertEquals(EnemyRarity.SUPER_UNIQUE,superUnique.actors().getFirst().enemyRarity());
        assertEquals("qa-ad-hoc",superUnique.actors().getFirst().templateBirth().templateId());
    }
    @Test void zeroExplicitAliasesUseOneDeterministicFamilyDraw(){
        var qa=EnemyQaSpawnRequest.parse("Larva_Void","unique",List.of());
        var first=planner.planQa(request(5),qa);
        var replay=planner.planQa(request(5),qa);
        assertEquals(first,replay);
        assertTrue(first.actors().size()==3||first.actors().size()==5);
        assertEquals(first.actors().size()==5,first.actors().getFirst().own(EnemyAffixRegistry.Operator.HORDE).isPresent());
    }
    @Test void fiftyIndependentTrorkPlansRetainTheSameRealAffixOwners(){
        var qa=EnemyQaSpawnRequest.parse("Trork_Warrior","unique",
                List.of("extrastrong","frenzied","armorbreaker"));
        for(int attempt=0;attempt<50;attempt++){
            var original=request(3,"Trork_Warrior",DifficultyId.HELL);
            var independent=new EnemyBirthPlanner.Request("qa-attempt/"+attempt,id("qa-pack/"+attempt),
                    original.anchor(),original.nativePlanReceipt(),original.originals(),List.of(),true,0,true);
            var birth=planner.planQa(independent,EnemyQaSpawnRequest.parse("Trork_Warrior","unique","hell",
                    List.of("extrastrong","frenzied","armorbreaker")));
            assertEquals(List.of("ME-002","ME-019","ME-025"),birth.actors().getFirst().ownAffixes().stream()
                    .map(EnemyDescriptor.AffixInstance::affixId).toList());
            assertEquals(3,birth.actors().size());
            assertTrue(birth.actors().stream().allMatch(actor->!actor.immutableRewardContext().economic()));
        }
    }
    @Test void invalidQaInputDoesNotMutateTheFollowingBirth(){
        var request=request(3,"Trork_Warrior");
        var valid=EnemyQaSpawnRequest.parse("Trork_Warrior","unique",List.of("extrastrong"));
        var expected=planner.planQa(request,valid);
        assertThrows(IllegalArgumentException.class,()->EnemyQaSpawnRequest.parse(
                "Trork_Warrior","unique",List.of("no_such_affix")));
        assertTrue(EnemyNativeBindings.load().role("No_Such_Role").isEmpty());
        assertEquals(expected,planner.planQa(request,valid));
    }
    @Test void everyAdmittedAffixUsesTheSameQaDescriptorPlanner(){
        var aliases=EnemyQaSpawnRequest.canonicalAliases();
        assertEquals(27,aliases.size());
        for(int index=0;index<aliases.size();index++){
            var qa=EnemyQaSpawnRequest.parse("Larva_Void","unique",List.of(aliases.get(index)));
            var birth=planner.planQa(request(index==21?5:3),qa);
            assertEquals(List.of("ME-%03d".formatted(index+1)),birth.actors().getFirst().ownAffixes().stream()
                    .map(EnemyDescriptor.AffixInstance::affixId).toList(),aliases.get(index));
            assertTrue(birth.actors().stream().allMatch(actor->actor.spawnOrigin()==EnemyRewardContext.Origin.QA));
        }
    }
}
