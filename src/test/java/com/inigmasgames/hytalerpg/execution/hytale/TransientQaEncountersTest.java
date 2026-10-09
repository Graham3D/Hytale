package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.enemies.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.progress.ProgressionMath;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransientQaEncountersTest {
    private static UUID id(String value){return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));}
    private final EnemyBalance balance=EnemyBalance.canonical();
    private final EnemyBirthPlanner planner=new EnemyBirthPlanner(balance,EnemyAffixRegistry.canonical(),
            EnemyAffinityRegistry.canonical(),EnemyNamePools.canonical(),EnemyVisualVariants.canonical());

    private EnemyBirthPlan birth(int attempt){
        UUID world=id("qa-world"),encounter=id("qa-encounter/"+attempt);
        var roster=new ArrayList<EnemyBirthPlanner.Candidate>();
        for(int index=0;index<3;index++){
            UUID actor=id("qa-actor/"+attempt+"/"+index);
            var source=new EnemyDescriptor(1,1,balance.revision(),"qa-binding",world,encounter,1,
                    actor,actor,"qa-command/test",EnemyRewardContext.Origin.QA,"Trork_Warrior","Trork_Warrior",50,
                    DifficultyId.HELL,ProgressionMath.Rank.COMMON,EnemyRarity.NORMAL,EnemyDescriptor.PackRole.NONE,
                    null,null,"qa-profile","Empty",null,"Trork","qa-seed",null,List.of(),List.of(),Set.of(),List.of(),
                    "Template_Predator",balance.rewards(EnemyRarity.NORMAL,EnemyRewardContext.Origin.QA,false,0),
                    "a".repeat(64),null);
            var binding=new EnemyAffixSelection.Binding("qa-binding",
                    EnumSet.allOf(EnemyAffixRegistry.Capability.class),0,true,false,false,0,false);
            roster.add(new EnemyBirthPlanner.Candidate(source,binding,0));
        }
        var request=new EnemyBirthPlanner.Request("qa/"+attempt,id("qa-pack/"+attempt),Vec3.ZERO,
                "qa-command/test",roster,List.of(),true,0,true);
        return planner.planQa(request,EnemyQaSpawnRequest.parse("Trork_Warrior","unique","hell",
                List.of("extrastrong","frenzied","armorbreaker")));
    }

    @Test void transientRootsAndLeaderMinionRetirementRemainSessionLocal() throws Exception {
        var forgotten=new ArrayList<UUID>();
        var registry=new TransientQaEncounters((world,actor)->forgotten.add(actor),(store,pack)->{});
        var first=birth(1);
        registry.reserve(first);
        assertEquals(1,registry.size());
        for(var actor:first.actors())assertTrue(registry.contains(first.world(),actor.entityId()));
        var leader=first.actors().getFirst();
        var roots1=registry.reserveActionRoots(leader,8).toCompletableFuture().join();
        var roots2=registry.reserveActionRoots(leader,8).toCompletableFuture().join();
        assertEquals(1,roots1.first());assertEquals(8,roots1.last());
        assertEquals(9,roots2.first());assertEquals(16,roots2.last());
        for(var actor:first.actors())registry.retire(first.world(),actor.entityId());
        assertEquals(0,registry.size());
        assertEquals(3,forgotten.size());
        var second=birth(2);
        registry.reserve(second);
        registry.discard(second);
        assertEquals(0,registry.size());
        assertFalse(registry.contains(second.world(),second.actors().getFirst().entityId()));
        registry.reserve(birth(3));
        registry.clear();
        assertEquals(0,registry.size());
    }

    @Test void localReservationFailureDoesNotAffectTheNextEncounter(){
        var registry=new TransientQaEncounters((world,actor)->{},(store,pack)->{});
        var first=birth(10);
        registry.reserve(first);
        assertThrows(IllegalStateException.class,()->registry.reserve(first));
        registry.discard(first);
        var next=birth(11);
        registry.reserve(next);
        assertEquals(1,registry.size());
    }
}
