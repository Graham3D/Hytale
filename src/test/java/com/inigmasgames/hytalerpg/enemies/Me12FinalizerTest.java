package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.*;

class Me12FinalizerTest {
    static Me12Policies fixed(String prefix,String suffix){var docs=Me12DistributionTest.documents();var names=JsonParser.parseString(docs.get("name-pools-v2.json")).getAsJsonObject();
        var json=new Gson();var p=new JsonArray();p.add(json.toJsonTree(new Me12Policies.Name("fixed-prefix","Fixed",1,1,prefix,Set.of())));
        var s=new JsonArray();s.add(json.toJsonTree(new Me12Policies.Name("fixed-suffix","the Fixture",1,1,suffix,Set.of())));
        names.add("prefixes",p);names.add("suffixes",s);names.addProperty("revision","ME12-fixed-fixture");docs.put("name-pools-v2.json",names.toString());return Me12Policies.load(docs);}
    static Me12Planner.Context roster(Me12Planner.Context c,Me12Planner.Member leader,List<Me12Planner.Member> base,List<Me12Planner.Member> extras){return new Me12Planner.Context(c.nativeSnapshotDigest(),c.sourceReceipt(),c.generation(),c.difficulty(),c.evidence(),leader,base,extras,c.effectiveWeights());}
    static long mass(Me12Planner.Context c,Me12Policies p){return Arrays.stream(Me12Planner.compile(c,p,new Me12Planner.Budget(n->{})).tierMasses()).sum();}
    @Test void S10HordeReservesTwoAdmittedSlotsAndEveryInheritedPackageIsSupported(){
        var c=Me12PlannerTest.context(DifficultyId.NORMAL);var horde=fixed("ME-001","ME-022");assertEquals(4900,mass(c,horde));
        assertEquals(0,mass(roster(c,c.leader(),c.minions(),List.of()),horde));
        var old=c.leader().binding();var caps=new HashSet<>(old.capabilities());caps.remove(Capability.MOBILE);
        var stationary=new Me12Planner.Member(c.leader().role(),c.leader().source(),new EnemyAffixSelection.Binding(old.revision(),caps,old.usableDefense(),true,false,false,3,true),Set.of(),true);
        assertEquals(0,mass(roster(c,c.leader(),c.minions(),List.of(stationary,stationary)),horde));
        assertEquals(0,mass(roster(c,c.leader(),List.of(stationary,stationary,stationary),c.hordeSlots()),fixed("ME-001","ME-017")));
    }
    @Test void S10PackboundRequiresReachableGuardsAndSustainCannotTakeTwoNativeImmunities(){
        var c=Me12PlannerTest.context(DifficultyId.NORMAL);var packbound=fixed("ME-017","ME-024");assertEquals(4900,mass(c,packbound));
        var unreachable=new Me12Planner.Member(c.leader().role(),c.leader().source(),c.leader().binding(),Set.of(),false);
        assertEquals(0,mass(roster(c,c.leader(),List.of(unreachable,unreachable,unreachable),c.hordeSlots()),packbound));
        var hell=Me12PlannerTest.context(DifficultyId.HELL);var immune=new Me12Planner.Member(hell.leader().role(),hell.leader().source(),hell.leader().binding(),Set.of("FIRE","WATER"),true);
        var context=roster(hell,immune,hell.minions(),hell.hordeSlots());
        assertEquals(0,mass(context,fixed("ME-017","ME-016")));assertEquals(0,mass(context,fixed("ME-017","ME-024")));assertEquals(0,mass(context,fixed("ME-017","ME-027")));
        assertTrue(mass(context,fixed("ME-017","ME-018"))>0);
    }
    @Test void S10EightAffixSnapshotsStillUseExistingAggregateCapsAndFrozenImmunityDraws(){
        var f=Me12PersistenceTest.fixture(true);var birth=f.root().plan();var pack=birth.pack().staged().publish();
        for(var actor:birth.actors()){
            var snapshot=EnemyAffixSnapshot.resolve(actor,f.policies().balance(),pack,8000,true,true);
            assertTrue(snapshot.movementMultiplier()<=1.5);assertTrue(snapshot.recoveryRateMultiplier()<=1.5);
            assertTrue(snapshot.allDirectIncrease()<=.6);assertTrue(snapshot.physicalIncrease()<=.6);
            assertEquals(actor,new Gson().fromJson(new Gson().toJson(actor),EnemyDescriptor.class));
        }
    }
}
