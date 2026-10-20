package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.progress.ProgressionMath;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyQaBirthPlannerTest {
    private final EnemyBalance balance=EnemyBalance.canonical();
    private final EnemyBirthPlanner planner=new EnemyBirthPlanner(balance,EnemyAffixRegistry.canonical(),
            EnemyAffinityRegistry.canonical(),EnemyNamePools.canonical(),EnemyVisualVariants.canonical());
    private static UUID id(String key){return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));}
    private EnemyBirthPlanner.Candidate candidate(int index){
        var entity=id("qa/entity/"+index);
        var source=new EnemyDescriptor(1,1,balance.revision(),"qa-binding",id("qa/world"),id("qa/encounter"),1,
                entity,entity,"qa-command/test",EnemyRewardContext.Origin.QA,"Larva_Void","Larva_Void",50,
                DifficultyId.NORMAL,ProgressionMath.Rank.COMMON,EnemyRarity.NORMAL,EnemyDescriptor.PackRole.NONE,
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
    @Test void nineExplicitAffixesSurviveFrozenQaBirth(){
        var qa=EnemyQaSpawnRequest.parse("Larva_Void","unique",List.of("extrafast","extrastrong","stoneskin",
                "fireenchanted","manaburn","knockback","vampiric","reflective","bulwark"));
        var birth=planner.planQa(request(3),qa);
        assertEquals(9,birth.actors().getFirst().ownAffixes().size());
        assertEquals(3,birth.actors().size());
        assertEquals(EnemyRewardContext.Origin.QA,birth.actors().get(1).spawnOrigin());
        assertFalse(birth.actors().getFirst().immutableRewardContext().economic());
    }
    @Test void packboundHordeSealsFourRealGuards(){
        var birth=planner.planQa(request(5),EnemyQaSpawnRequest.parse("Larva_Void","unique",
                List.of("horde","packbound")));
        assertEquals(5,birth.actors().size());
        assertEquals(4,birth.pack().guardIds().size());
        assertTrue(birth.actors().stream().skip(1).allMatch(actor->actor.packRole()==EnemyDescriptor.PackRole.MINION));
    }
    @Test void championAndAdHocSuperUniqueKeepTheirRequestedFamilies(){
        var champion=planner.planQa(request(1),EnemyQaSpawnRequest.parse("Larva_Void","champion",
                List.of("stoneskin","reflective")));
        assertEquals(EnemyRarity.CHAMPION,champion.actors().getFirst().enemyRarity());
        assertEquals(2,champion.actors().getFirst().ownAffixes().size());
        assertNull(champion.pack().leaderId());
        var superUnique=planner.planQa(request(3),EnemyQaSpawnRequest.parse("Larva_Void","superunique",
                List.of("stoneskin","reflective")));
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
}
