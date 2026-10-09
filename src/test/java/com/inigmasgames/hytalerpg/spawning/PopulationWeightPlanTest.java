package com.inigmasgames.hytalerpg.spawning;

import org.junit.jupiter.api.Test;
import java.util.List;
import static com.inigmasgames.hytalerpg.spawning.NativePopulationRoles.Category.*;
import static org.junit.jupiter.api.Assertions.*;

class PopulationWeightPlanTest {
    @Test void movesOnlyEligibleControlledMemberExpectations(){
        var rows=List.of(
                new PopulationWeightPlan.Row(1,HOSTILE,2,2,1,true),
                new PopulationWeightPlan.Row(2,HOSTILE,1,1,2,true),
                new PopulationWeightPlan.Row(3,WILDLIFE,5,9,3,true),
                new PopulationWeightPlan.Row(4,AMBIENT_AVIAN,1,2,1,true),
                new PopulationWeightPlan.Row(5,OTHER,1,1,1,true));
        var result=PopulationWeightPlan.calculate(100,rows,.65,.35);
        assertTrue(result.adjusted());
        assertEquals(52,result.expected().get(1)+result.expected().get(2),1e-9);
        assertEquals(28,result.expected().get(3),1e-9);
        assertEquals(10,result.expected().get(4),1e-9);
        assertEquals(10,result.expected().get(5),1e-9);
        assertEquals(2,result.actualAvian());
        assertEquals(9,result.actualWildlife());
    }
    @Test void oneEligibleCategoryPreservesNativeTargets(){
        var rows=List.of(new PopulationWeightPlan.Row(1,HOSTILE,2,1,1,false),
                new PopulationWeightPlan.Row(2,WILDLIFE,3,2,2,true));
        var result=PopulationWeightPlan.calculate(50,rows,.65,.35);
        assertFalse(result.adjusted());
        assertEquals(20,result.expected().get(1),1e-9);
        assertEquals(30,result.expected().get(2),1e-9);
    }
    @Test void insufficientNativeWeightFailsClosed(){
        var rows=List.of(new PopulationWeightPlan.Row(1,HOSTILE,.01,0,1,true),
                new PopulationWeightPlan.Row(2,WILDLIFE,9.99,9,1,true));
        var result=PopulationWeightPlan.calculate(100,rows,.65,.35);
        assertFalse(result.adjusted());
        assertEquals("AMPLIFICATION_LIMIT",result.reason());
    }
    @Test void densityScalesMemberTargetsButNeverCreatesHeadroomOrRemovesWildlife(){
        var rows=List.of(new PopulationWeightPlan.Row(1,HOSTILE,1,35,2,true),
                new PopulationWeightPlan.Row(2,WILDLIFE,1,65,5,true),
                new PopulationWeightPlan.Row(3,AMBIENT_AVIAN,1,6,1,true));
        var one=PopulationWeightPlan.calculate(100,rows,.65,.35);
        var eight=PopulationWeightPlan.calculate(800,rows,.65,.35);
        assertEquals(8*one.targetHostile(),eight.targetHostile(),1e-9);
        assertEquals(8*one.targetWildlife(),eight.targetWildlife(),1e-9);
        assertEquals(65,one.actualWildlife());
        assertEquals(6,one.actualAvian());
        assertEquals(100,one.actualHostile()+one.actualWildlife());
        assertEquals(100,one.expected().values().stream().mapToDouble(Double::doubleValue).sum(),1e-9);
    }
    @Test void installedRoleAuditHasExactHostileAndAvianExamples(){
        var catalog=NativePopulationRoles.load();
        assertEquals(HOSTILE,catalog.category("Eye_Void"));
        assertEquals(WILDLIFE,catalog.category("Deer_Stag"));
        assertEquals(WILDLIFE,catalog.category("Squirrel"));
        assertEquals(WILDLIFE,catalog.category("Fox"));
        assertEquals(WILDLIFE,catalog.category("Frog_Blue"));
        assertEquals(AMBIENT_AVIAN,catalog.category("Vulture"));
        assertEquals(OTHER,catalog.category("Piranha"));
        assertEquals(OTHER,catalog.category("Unknown_New_Role"));
    }
}
