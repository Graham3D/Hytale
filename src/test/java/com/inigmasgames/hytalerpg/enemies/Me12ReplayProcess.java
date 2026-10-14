package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.GsonBuilder;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import java.util.*;

/** Offline JVM process, not a Hytale server. stdout is a deterministic plan transcript. */
public final class Me12ReplayProcess {
    public static void main(String[] args){
        var policy=Me12Policies.canonical();var binding=new EnemyAffixSelection.Binding("replay-static",EnumSet.allOf(EnemyAffixRegistry.Capability.class),100,true,false,false,3,true);
        var member=new Me12Planner.Member("Trork_Warrior","replay-static",binding,Set.of(),true);
        var context=new Me12Planner.Context("a".repeat(64),"replay-static",1,DifficultyId.HELL,Me12Planner.Evidence.CERTIFIED_STATIC_FIXTURE,member,List.of(member,member,member),List.of(member,member),
                Me12Planner.weights(policy.registry(),DifficultyId.HELL,new EnemyAffixSelection.WeightPolicy(Map.of(),Set.of())));
        var compiled=Me12Planner.compile(context,policy,new Me12Planner.Budget(n->{}));var json=new GsonBuilder().disableHtmlEscaping().create();
        for(int i=0;i<128;i++)System.out.println(json.toJson(compiled.sample("ME12-S06/"+i)));
    }
}
