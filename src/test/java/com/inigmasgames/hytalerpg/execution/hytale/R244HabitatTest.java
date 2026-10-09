package com.inigmasgames.hytalerpg.execution.hytale;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class R244HabitatTest {
    static class Habitat implements NativeAquaticHabitat.Column {
        FluidSection cave=new FluidSection(),surface=new FluidSection();boolean complete=true,valid=false;int checks;
        public List<int[]> runs(int x,int z){return List.of(new int[]{32,36,12},new int[]{64,68,13});}
        public FluidSection fluid(int section){return section==1?(complete?cave:null):surface;}
        public boolean accepts(int id){return id==1;}
        public boolean candidate(int x,int z){checks++;return valid;}
    }
    @Test void dryCaveAndUnrelatedFluidDoNotSpendNativeProbeWork(){
        var h=new Habitat();h.surface.setFluid(0,0,0,1,(byte)8);
        for(int i=0;i<100;i++)assertEquals(NativeAquaticHabitat.Outcome.KNOWN_UNSUITABLE,NativeAquaticHabitat.assess(h,12).outcome());
        assertEquals(0,h.checks);
    }
    @Test void missingRelevantSectionIsUnknownButUnrelatedMissingSectionsAreIgnored(){
        var h=new Habitat();h.complete=false;assertEquals(NativeAquaticHabitat.Outcome.UNKNOWN,NativeAquaticHabitat.assess(h,12).outcome());
        h.complete=true;h.surface=null;assertEquals(NativeAquaticHabitat.Outcome.KNOWN_UNSUITABLE,NativeAquaticHabitat.assess(h,12).outcome());
    }
    @Test void fillDrainAndIceCoveredPoolUseNativeCandidateResult(){
        var h=new Habitat();assertEquals(NativeAquaticHabitat.Outcome.KNOWN_UNSUITABLE,NativeAquaticHabitat.assess(h,12).outcome());
        h.cave.setFluid(0,0,0,1,(byte)8);h.valid=true;
        assertEquals(NativeAquaticHabitat.Outcome.SUITABLE_CANDIDATE,NativeAquaticHabitat.assess(h,12).outcome());
        // Clearance/ice belongs to the native candidate predicate; failed predicate is not proof every sample is impossible.
        h.valid=false;assertEquals(NativeAquaticHabitat.Outcome.UNKNOWN,NativeAquaticHabitat.assess(h,12).outcome());
        h.cave.setFluid(0,0,0,0,(byte)0);assertEquals(NativeAquaticHabitat.Outcome.KNOWN_UNSUITABLE,NativeAquaticHabitat.assess(h,12).outcome());
    }
    @Test void boundedBackoffExpiresAndHabitatVersionOrSuccessResetsIt(){
        var r=new NativeAquaticHabitat.Retry();assertTrue(r.admit(7,0));
        r.completed(false,0);r.completed(false,0);r.completed(false,0);
        assertFalse(r.admit(7,1));assertTrue(r.admit(7,2_000_000_001L));
        assertTrue(r.admit(8,2));r.completed(true,3);assertTrue(r.admit(8,4));
    }
}
