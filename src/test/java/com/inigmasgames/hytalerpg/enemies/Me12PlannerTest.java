package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.*;

class Me12PlannerTest {
    static Me12Planner.Context context(DifficultyId mode) {
        var binding=new EnemyAffixSelection.Binding("static-only",EnumSet.allOf(Capability.class),100,true,false,false,3,true);
        var member=new Me12Planner.Member("Trork_Warrior","static-only/not-native-admission",binding,Set.of(),true);
        return new Me12Planner.Context("a".repeat(64),"static-context",1,mode,Me12Planner.Evidence.CERTIFIED_STATIC_FIXTURE,
                member,List.of(member,member,member),List.of(member,member),Me12Planner.weights(EnemyAffixRegistry.canonical(),mode,new EnemyAffixSelection.WeightPolicy(Map.of(),Set.of())));
    }
    @Test void N01N04N05AnalyticalTierMasses() {
        assertArrayEquals(new long[]{4900,1750,350,1750,625,125,350,125,25},Me12Planner.countFeasibleMasses(8,2));
        var three=Me12Planner.countFeasibleMasses(3,0);assertEquals(8400,Arrays.stream(three).sum());
        assertEquals(7.0/12,three[0]/8400.0);assertEquals(5.0/24,three[1]/8400.0);assertEquals(5.0/24,three[3]/8400.0);
        assertEquals(9975,Arrays.stream(Me12Planner.countFeasibleMasses(5,0)).sum());
        assertEquals(9725,Arrays.stream(Me12Planner.countFeasibleMasses(5,1)).sum());
        assertEquals(8400,Arrays.stream(Me12Planner.countFeasibleMasses(5,2)).sum());
    }
    @Test void N02S03FullGraphsHaveExactBudgetsAndCanonicalOwnership() {
        var policies=Me12Policies.canonical();
        for(var mode:DifficultyId.values()) {
            var compiled=Me12Planner.compile(context(mode),policies,new Me12Planner.Budget(n->{}));
            System.out.println("ME12 "+mode+" states="+compiled.states()+" bytes="+compiled.accountedBytes()+" coldNs="+compiled.compileNanos()+" tiers="+Arrays.toString(compiled.tierMasses()));
            assertArrayEquals(Me12Planner.countFeasibleMasses(8,mode.ordinal()),compiled.tierMasses());
            for(int i=0;i<1000;i++) {
                var plan=compiled.sample("fixture/"+i);assertEquals(plan,compiled.sample("fixture/"+i));
                assertEquals(plan.prefix().tier()+plan.suffix().tier()+mode.ordinal(),plan.affixes().size());
                assertEquals(plan.affixes().size(),plan.choices().stream().map(EnemyAffixSelection.Choice::affixId).distinct().count());
                assertEquals(plan.affixes(),Me12Planner.canonicalOwnership(plan.prefix(),plan.suffix(),mode.ordinal(),plan.choices()));
            }
        }
    }
    @Test void S04BeyondLongAndZeroSingleton() {
        var rng=new Me12Random(Me12Random.SET,"big");assertThrows(IllegalArgumentException.class,()->rng.uniformBelow(BigInteger.ZERO));
        assertEquals(BigInteger.ZERO,rng.uniformBelow(BigInteger.ONE));var bound=BigInteger.ONE.shiftLeft(128).add(BigInteger.valueOf(17));
        boolean beyondLong=false;for(int i=0;i<1000;i++){var draw=rng.uniformBelow(bound);assertTrue(draw.signum()>=0&&draw.compareTo(bound)<0);beyondLong|=draw.bitLength()>63;}assertTrue(beyondLong);
    }
    @Test void S04IndependentPythonGoldenVectorsAndNonPowerOfTwoSampling() throws Exception {
        var vectors=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of("src/test/resources/enemies/me12-rng-vectors.json"))).getAsJsonArray();
        for(var entry:vectors){var v=entry.getAsJsonObject();var random=new Me12Random(v.get("domain").getAsString(),v.get("seed").getAsString());var bound=new BigInteger(v.get("bound").getAsString());
            for(var expected:v.getAsJsonArray("draws"))assertEquals(new BigInteger(expected.getAsString()),random.uniformBelow(bound));}
        var random=new Me12Random(Me12Random.SET,"ME12-S04/three");long[] counts=new long[3];for(int i=0;i<100000;i++)counts[random.uniformBelow(BigInteger.valueOf(3)).intValueExact()]++;
        for(int i=0;i<3;i++)Me12DistributionTest.assertCell(counts[i],100000,1.0/3,"uniformBelow(3)/"+i);
        var c=Me12PlannerTest.context(DifficultyId.NORMAL);var weights=new TreeMap<>(c.effectiveWeights());weights.replaceAll((id,w)->0L);
        for(String id:List.of("ME-001","ME-002","ME-003"))weights.put(id,10000000000L);weights.put("ME-017",1L);weights.put("ME-018",1L);
        var graph=Me12Planner.compile(Me12DistributionTest.withWeights(c,weights),Me12DistributionTest.toy(),new Me12Planner.Budget(n->{}));
        assertEquals(new BigInteger("300000000000000000000"),graph.completionMasses().get("toy-prefix/toy-suffix"));
        assertTrue(graph.accepts(graph.sample("beyond-long-graph")));
    }
    @Test void S09DeadlineIsFailureAndProductionUncertifiedIsNotZeroMass() {
        var c=context(DifficultyId.HELL);var p=Me12Policies.canonical();
        assertThrows(Me12Planner.CompileFailure.class,()->Me12Planner.compile(c,p,new Me12Planner.Budget(0,n->{})));
        var production=new Me12Planner.Context(c.nativeSnapshotDigest(),c.sourceReceipt(),c.generation(),c.difficulty(),Me12Planner.Evidence.UNCERTIFIED_PRODUCTION,c.leader(),c.minions(),c.hordeSlots(),c.effectiveWeights());
        assertTrue(assertThrows(IllegalStateException.class,()->Me12Planner.compile(production,p,new Me12Planner.Budget(n->{}))).getMessage().contains("DEPENDENCY_UNCERTIFIED"));
    }
}
